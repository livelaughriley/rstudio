/*
 * Preferences.cpp
 *
 * Copyright (C) 2009-19 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */


#include <session/prefs/Preferences.hpp>

#include <session/SessionModuleContext.hpp>

using namespace rstudio::core;

namespace rstudio {
namespace session {
namespace prefs {

core::json::Array Preferences::allLayers()
{
   json::Array layers;
   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      for (auto layer: layers_)
      {
         json::Object obj;
         obj["name"] = layer->layerName();
         obj["values"] = layer->allPrefs();
         layers.push_back(obj);
      }
   }
   END_LOCK_MUTEX
   return layers;
}

core::Error Preferences::readLayers()
{
   Error error;
   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      for (auto layer: layers_)
      {
         error = layer->readPrefs();
         if (error)
         {
            LOG_ERROR(error);
         }
      }
   }
   END_LOCK_MUTEX
   return Success();
}

Error Preferences::initialize()
{
   Error error = createLayers();
   if (error)
      return error;

   error = readLayers();
   if (error)
      return error;

   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      for (auto layer: layers_)
      {
         // Validate the layer and log errors for violations
         error = layer->validatePrefs();
         if (error)
            LOG_ERROR(error);

         // Subscribe for layer change notifications
         layer->onChanged.connect(boost::bind(&Preferences::onPrefLayerChanged, this,
                  layer->layerName(), _1));
      }
   }
   END_LOCK_MUTEX

   return Success();
}

core::Error Preferences::writeLayer(int layer, const core::json::Object& prefs)
{
   Error result;

   // A vector of all the preferences actually changed in this update
   std::vector<std::string> changed;

   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      // We cannot write the base layer or a non-existent layer.
      if (layer >= static_cast<int>(layers_.size()) || layer < 1)
         return systemError(boost::system::errc::invalid_argument, ERROR_LOCATION);

      // Write only the unique values in this layer.
      json::Object unique;
      for (const auto pref: prefs)
      {
         // Check to see whether the value for this preference (a) exists in some other lower layer,
         // and if so (b) whether it differs from the value in that layer.
         bool found = false;
         bool differs = false;
         for (int i = layer; i >= 0; --i)
         {
            const auto val = layers_[i]->readValue(pref.name());
            if (val)
            {
               found = true;
               if (!(*val == pref.value()))
               {
                  if (layer == i)
                  {
                     // The pref exists in this layer and has a different value; emit a changed
                     // notification for it later.
                     changed.push_back(pref.name());
                  }
                  else
                  {
                     // The pref exists in a lower layer.
                     differs = true;
                  }
               }
               if (i < layer)
               {
                  // We found the pref in a lower layer, so no need to look deeper.
                  break;
               }
            }
         }

         if (!found || differs)
         {
            // If the preference doesn't exist in any other layer, or the value doesn't match the
            // value found elsewhere, record the unique value in this layer.
            unique[pref.name()] = pref.value();
         }
      }

      result = layers_[layer]->writePrefs(unique);
   }
   END_LOCK_MUTEX;

   // Emit change events for all the preferences we changed
   for (const auto prefName: changed)
   {
      onChanged(layers_[layer]->layerName(), prefName);
   }

   if (result)
      return result;

   return Success();
}

boost::optional<core::json::Value> Preferences::readValue(const std::string& name, 
      std::string *pLayerName)
{
   // Work backwards through the layers, starting with the most specific (project or user-level
   // settings) and working towards the most general (basic defaults)
   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      for (const auto layer: boost::adaptors::reverse(layers_))
      {
         boost::optional<core::json::Value> val = layer->readValue(name);
         if (val)
         {
            if (pLayerName != nullptr)
            {
               *pLayerName = layer->layerName();
            }
            return val;
         }
      }
   }
   END_LOCK_MUTEX

   return boost::none;
}

boost::optional<core::json::Value> Preferences::readValue(const std::string& layerName,
      const std::string& name)
{
   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      for (const auto layer: layers_)
      {
         if (layer->layerName() == layerName)
         {
            boost::optional<core::json::Value> val = layer->readValue(name);
            if (val)
            {
               return val;
            }
            break;
         }
      }
   }
   END_LOCK_MUTEX

   return boost::none;
}

core::Error Preferences::writeValue(const std::string& name, const core::json::Value& value)
{
   Error result;
   std::string layerName;
   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      auto layer = layers_[userLayer()];
      if (!layer)
      {
         core::Error error(core::json::errc::ParamMissing, ERROR_LOCATION);
         error.addProperty("description", "missing user layer for preference value '" + 
               name + "'");
         return error;
      }
      layerName = layer->layerName();
      result = layer->writePref(name, value);
   }
   END_LOCK_MUTEX

   // Make sure to keep this outside the mutex lock since prefs are typically read after being
   // changed.
   onChanged(layerName, name);

   return result;
}

core::json::Object Preferences::userPrefLayer()
{
   core::json::Object layer;
   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      layer = layers_[userLayer()]->allPrefs();
   }
   END_LOCK_MUTEX
   return layer;
}

Error Preferences::clearValue(const std::string &name)
{
   Error result;
   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      result = layers_[userLayer()]->clearValue(name);
   }
   END_LOCK_MUTEX
   return result;
}

json::Object Preferences::getLayer(const std::string& name)
{
   json::Object result;
   bool found = false;

   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      for (auto layer: layers_)
      {
         if (layer->layerName() == name)
         {
            result = layer->allPrefs();
            found = true;
            break;
         }
      }
   }
   END_LOCK_MUTEX;

   if (!found)
   {
      LOG_WARNING_MESSAGE("Preference layer '" + name + "' does not exist.");
   }

   return result;
}

void Preferences::onPrefLayerChanged(const std::string& layerName, const std::string& prefName)
{
   onChanged(layerName, prefName);
}

void Preferences::notifyClient(const std::string &layerName, const std::string &pref)
{
   bool found = false;
   RECURSIVE_LOCK_MUTEX(mutex_)
   {
      for (auto layer: layers_)
      {
         if (layer->layerName() == layerName)
         {
            json::Object valueJson;
            auto val = layer->readValue(pref);
            if (val)
            {
               valueJson[pref] = *val;

               json::Object dataJson;
               dataJson["name"] = layerName;
               dataJson["values"] = valueJson;
               ClientEvent event(clientChangedEvent(), dataJson);
               module_context::enqueClientEvent(event);

               found = true;
            }
         }
      }
   }
   END_LOCK_MUTEX

   if (!found)
   {
      LOG_WARNING_MESSAGE("Pref '" + pref + "' doesn't exist in layer '" + layerName + "'");
   }
}

} // namespace prefs
} // namespace session
} // namespace rstudio
