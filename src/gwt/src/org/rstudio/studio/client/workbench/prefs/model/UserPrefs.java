/*
 * UserPrefs.java
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
package org.rstudio.studio.client.workbench.prefs.model;

import com.google.gwt.core.client.JsArray;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.rstudio.core.client.CommandWithArg;
import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.satellite.Satellite;
import org.rstudio.studio.client.common.satellite.SatelliteManager;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.workbench.events.SessionInitEvent;
import org.rstudio.studio.client.workbench.events.SessionInitHandler;
import org.rstudio.studio.client.workbench.model.Session;
import org.rstudio.studio.client.workbench.prefs.events.UserPrefsChangedEvent;
import org.rstudio.studio.client.workbench.prefs.events.UserPrefsChangedHandler;

@Singleton
public class UserPrefs extends UserPrefsComputed implements UserPrefsChangedHandler
{
   @Inject
   public UserPrefs(Session session, 
                  EventBus eventBus,
                  PrefsServerOperations server,
                  SatelliteManager satelliteManager)
   {
      super(session.getSessionInfo(),
            (session.getSessionInfo() == null ? 
               JsArray.createArray().cast() :
               session.getSessionInfo().getPrefs()));

      session_ = session;
      server_ = server;
      satelliteManager_ = satelliteManager;

      eventBus.addHandler(UserPrefsChangedEvent.TYPE, this);
   }
   
   public void writeUserPrefs()
   {
      writeUserPrefs(null);
   }

   public void writeUserPrefs(CommandWithArg<Boolean> onCompleted)
   {
      server_.setUserPrefs(
         session_.getSessionInfo().getUserPrefs(),
         new ServerRequestCallback<Void>() 
         {
            @Override
            public void onResponseReceived(Void v)
            {
               UserPrefsChangedEvent event = new UserPrefsChangedEvent(
                     session_.getSessionInfo().getUserPrefLayer());

               if (Satellite.isCurrentWindowSatellite())
               {
                  RStudioGinjector.INSTANCE.getEventBus()
                     .fireEventToMainWindow(event);
               }
               else
               {
                  // let satellites know prefs have changed
                  satelliteManager_.dispatchCrossWindowEvent(event);
               }
               
               if (onCompleted != null)
               {
                  onCompleted.execute(true);
               }
            }
            @Override
            public void onError(ServerError error)
            {
               if (onCompleted != null)
               {
                  onCompleted.execute(false);
               }
               Debug.logError(error);
            }
         });
   }
   
   @Override
   public void onUserPrefsChanged(UserPrefsChangedEvent e)
   {
      syncPrefs(e.getName(), e.getValues());
   }

   public static final int LAYER_DEFAULT  = 0;
   public static final int LAYER_SYSTEM   = 1;
   public static final int LAYER_COMPUTED = 2;
   public static final int LAYER_USER     = 3;
   public static final int LAYER_PROJECT  = 4;
   
   private final Session session_;
   private final PrefsServerOperations server_;
   private final SatelliteManager satelliteManager_;
}
