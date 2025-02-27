/*
 * TerminalPreferencesPane.java
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
package org.rstudio.studio.client.workbench.prefs.views;

import java.util.List;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import org.rstudio.core.client.BrowseCap;
import org.rstudio.core.client.JsArrayUtil;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.resources.ImageResource2x;
import org.rstudio.core.client.widget.FileChooserTextBox;
import org.rstudio.core.client.widget.SelectWidget;
import org.rstudio.core.client.widget.TextBoxWithButton;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.HelpLink;
import org.rstudio.studio.client.server.Server;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.model.Session;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;
import org.rstudio.studio.client.workbench.views.terminal.TerminalShellInfo;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;

public class TerminalPreferencesPane extends PreferencesPane
{

   @Inject
   public TerminalPreferencesPane(UserPrefs prefs,
                                  PreferencesDialogResources res,
                                  Session session,
                                  final GlobalDisplay globalDisplay,
                                  final Server server)
   {
      prefs_ = prefs;
      res_ = res;
      session_ = session;
      server_ = server;

      Label shellLabel = headerLabel("Shell");
      shellLabel.getElement().getStyle().setMarginTop(8, Unit.PX);
      add(shellLabel);

      terminalShell_ = new SelectWidget("New terminals open with:");
      spaced(terminalShell_);
      add(terminalShell_);
      terminalShell_.setEnabled(false);
      terminalShell_.addChangeHandler(event -> manageCustomShellControlVisibility());

      // custom shell exe path chooser
      Command onShellExePathChosen = new Command()
      {
         @Override
         public void execute()
         {
            if (BrowseCap.isWindowsDesktop())
            {
               String shellExePath = customShellChooser_.getText();
               if (!shellExePath.endsWith(".exe"))
               {
                  String message = "The program '" + shellExePath + "'" +
                     " is unlikely to be a valid shell executable.";
                  
                  globalDisplay.showMessage(
                        GlobalDisplay.MSG_WARNING,
                        "Invalid Shell Executable",
                        message);
               }
            }
         }
      };

      String textboxWidth = "250px";
      customShellChooser_ = new FileChooserTextBox("",
                                                   "(Not Found)",
                                                   null,
                                                   onShellExePathChosen);
      customShellPathLabel_ = new Label("Custom shell binary:");
      addTextBoxChooser(textboxWidth, customShellPathLabel_, customShellChooser_);
      customShellChooser_.setEnabled(false);

      customShellOptionsLabel_ = new Label("Custom shell command-line options:");
      add(spacedBefore(customShellOptionsLabel_));
      customShellOptions_ = new TextBox();
      DomUtils.disableSpellcheck(customShellOptions_);
      customShellOptions_.setWidth(textboxWidth);
      customShellOptions_.setEnabled(false);
      add(customShellOptions_);

      Label perfLabel = headerLabel("Connection");
      perfLabel.getElement().getStyle().setMarginTop(8, Unit.PX);
      add(perfLabel);
 
      boolean showPerfLabel = false;
      if (haveLocalEchoPref())
      {
         CheckBox chkTerminalLocalEcho = checkboxPref("Local terminal echo",
               prefs_.terminalLocalEcho(), 
               "Local echo is more responsive but may get out of sync with some line-editing modes or custom shells.");
         add(chkTerminalLocalEcho);
         showPerfLabel = true;
      }
      if (haveWebsocketPref())
      {
         CheckBox chkTerminalWebsocket = checkboxPref("Connect with WebSockets",
               prefs_.terminalWebsockets(), 
               "WebSockets are generally more responsive; try turning off if terminal won't connect.");
         add(chkTerminalWebsocket);
         showPerfLabel = true;
      }

      perfLabel.setVisible(showPerfLabel);

      Label miscLabel = headerLabel("Miscellaneous");
      miscLabel.getElement().getStyle().setMarginTop(8, Unit.PX);
      add(miscLabel);
      miscLabel.setVisible(true);

      CheckBox chkTerminalAutoClose = checkboxPref("Close terminal when shell exits",
            prefs_.terminalAutoClose(),
            "Deselect this option to keep terminal pane open after shell exits.");
      add(chkTerminalAutoClose);

      if (haveCaptureEnvPref())
      {
         CheckBox chkCaptureEnv = checkboxPref("Save and restore environment variables",
               prefs_.terminalTrackEnvironment(),
               "Terminal occasionally runs a hidden command to capture state of environment variables.");
         add(chkCaptureEnv);
      }

      if (haveBusyDetectionPref())
      {
         Label shutdownLabel = headerLabel("Process Termination");
         shutdownLabel.getElement().getStyle().setMarginTop(8, Unit.PX);
         add(shutdownLabel);
         shutdownLabel.setVisible(true);

         busyMode_ = new SelectWidget("Ask before killing processes:");
         spaced(busyMode_);
         add(busyMode_);
         busyMode_.setEnabled(false);
         busyMode_.addChangeHandler(event -> manageBusyModeControlVisibility());
         busyWhitelistLabel_ = new Label("Don't ask before killing:");
         add(busyWhitelistLabel_);
         busyWhitelist_ = new TextBox();
         DomUtils.disableSpellcheck(busyWhitelist_);
         busyWhitelist_.setWidth(textboxWidth);
         add(busyWhitelist_);
         busyWhitelist_.setEnabled(false);
      }
      
      HelpLink helpLink = new HelpLink("Using the RStudio terminal", "rstudio_terminal", false);
      nudgeRight(helpLink); 
      helpLink.addStyleName(res_.styles().newSection()); 
      add(helpLink);
   }

   @Override
   public ImageResource getIcon()
   {
      return new ImageResource2x(res_.iconTerminal2x());
   }

   @Override
   public String getName()
   {
      return "Terminal";
   }

   @Override
   protected void initialize(UserPrefs prefs)
   {
      Scheduler.get().scheduleDeferred(() -> server_.getTerminalShells(
            new ServerRequestCallback<JsArray<TerminalShellInfo>>()
      {
         @Override
         public void onResponseReceived(JsArray<TerminalShellInfo> shells)
         {
            String currentShell = BrowseCap.isWindowsDesktop() ?
               prefs.windowsTerminalShell().getValue() :
               prefs.posixTerminalShell().getValue();
            int currentShellIndex = 0;

            TerminalPreferencesPane.this.terminalShell_.getListBox().clear();

            boolean hasCustom = false;

            for (int i = 0; i < shells.length(); i++)
            {
               TerminalShellInfo info = shells.get(i);
               if (StringUtil.equals(info.getShellType(), UserPrefs.WINDOWS_TERMINAL_SHELL_CUSTOM))
                  hasCustom = true;
               TerminalPreferencesPane.this.terminalShell_.addChoice(
                     info.getShellName(), info.getShellType());
               if (info.getShellType() == currentShell)
                  currentShellIndex = i;
            }
            if (TerminalPreferencesPane.this.terminalShell_.getListBox().getItemCount() > 0)
            {
               TerminalPreferencesPane.this.terminalShell_.setEnabled((true));
               TerminalPreferencesPane.this.terminalShell_.getListBox().setSelectedIndex(currentShellIndex);
            }

            if (hasCustom)
            {
               customShellChooser_.setText(prefs.customShellCommand().getValue());
               customShellChooser_.setEnabled(true);
               customShellOptions_.setText(prefs.customShellOptions().getValue());
               customShellOptions_.setEnabled(true);
            }
            manageCustomShellControlVisibility();
         }

         @Override
         public void onError(ServerError error) { }
      }));

      if (busyMode_ != null)
      {
         busyMode_.getListBox().clear();
         busyMode_.addChoice("Always", UserPrefs.BUSY_DETECTION_ALWAYS);
         busyMode_.addChoice("Never", UserPrefs.BUSY_DETECTION_NEVER);
         busyMode_.addChoice("Always except for whitelist", UserPrefs.BUSY_DETECTION_WHITELIST);
         busyMode_.setEnabled(true);
         
         String selection = prefs_.busyDetection().getValue();
         for (int i = 0; i < busyMode_.getListBox().getItemCount(); i++)
         {
            if (busyMode_.getListBox().getValue(i) == prefs_.busyDetection().getValue())
            {
               busyMode_.getListBox().setSelectedIndex(i);
            }
         }
         
         List<String> whitelistArray = JsArrayUtil.fromJsArrayString(
               prefs_.busyWhitelist().getValue());
         
         StringBuilder whitelist = new StringBuilder();
         for (String entry: whitelistArray)
         {
            if (entry.trim().isEmpty())
            {
               continue;
            }
            if (whitelist.length() > 0)
            {
               whitelist.append(" ");
            }
            whitelist.append(entry.trim());
         }

         busyWhitelist_.setText(whitelist.toString());
         busyWhitelist_.setEnabled(true);

         manageBusyModeControlVisibility();
      }
   }

   @Override
   public boolean onApply(UserPrefs rPrefs)
   {
      boolean restartRequired = super.onApply(rPrefs);
     
      if (haveBusyDetectionPref())
      {
         prefs_.busyWhitelist().setGlobalValue(StringUtil.split(busyWhitelist_.getText(), " "));
         prefs_.busyDetection().setGlobalValue(selectedBusyMode());
      } 
      
      if (BrowseCap.isWindowsDesktop())
         prefs_.windowsTerminalShell().setGlobalValue(selectedShellType());
      else
         prefs_.posixTerminalShell().setGlobalValue(selectedShellType());

      prefs_.customShellCommand().setGlobalValue(customShellChooser_.getText());
      prefs_.customShellOptions().setGlobalValue(customShellOptions_.getText());

      return restartRequired;
   }

   private boolean haveLocalEchoPref()
   {
      return !BrowseCap.isWindowsDesktop();
   }

   private boolean haveBusyDetectionPref()
   {
      return !BrowseCap.isWindowsDesktop();
   }

   private boolean haveWebsocketPref()
   {
      return session_.getSessionInfo().getAllowTerminalWebsockets();
   }

   private boolean haveCaptureEnvPref()
   {
      return !BrowseCap.isWindowsDesktop();
   }

   private String selectedShellType()
   {
      return terminalShell_.getListBox().getSelectedValue();
   }

   private void manageCustomShellControlVisibility()
   {
      boolean customEnabled = (selectedShellType() == UserPrefs.WINDOWS_TERMINAL_SHELL_CUSTOM);
      customShellPathLabel_.setVisible(customEnabled);
      customShellChooser_.setVisible(customEnabled);
      customShellOptionsLabel_.setVisible(customEnabled);
      customShellOptions_.setVisible(customEnabled);
   }

   private String selectedBusyMode()
   {
      int idx = busyMode_.getListBox().getSelectedIndex();
      return busyMode_.getListBox().getValue(idx);
   }

   private void manageBusyModeControlVisibility()
   {
      boolean whitelistEnabled = selectedBusyMode() == UserPrefs.BUSY_DETECTION_WHITELIST;
      busyWhitelistLabel_.setVisible(whitelistEnabled);
      busyWhitelist_.setVisible(whitelistEnabled);
   }
   
   private void addTextBoxChooser(String textWidth, Label captionLabel, TextBoxWithButton chooser)
   {
      HorizontalPanel captionPanel = new HorizontalPanel();
      captionPanel.setWidth(textWidth);
      nudgeRight(captionPanel);

      captionPanel.add(captionLabel);
      captionPanel.setCellHorizontalAlignment(captionLabel,
            HasHorizontalAlignment.ALIGN_LEFT);

      add(tight(captionPanel));

      chooser.setTextWidth(textWidth);
      nudgeRight(chooser);
      textBoxWithChooser(chooser);
      spaced(chooser);
      add(chooser);
   }

   private final SelectWidget terminalShell_;
   private final Label customShellPathLabel_;
   private final TextBoxWithButton customShellChooser_;
   private final Label customShellOptionsLabel_;
   private final TextBox customShellOptions_;

   private SelectWidget busyMode_;
   private Label busyWhitelistLabel_;
   private TextBox busyWhitelist_;
   
   // Injected ----  
   private final UserPrefs prefs_;
   private final PreferencesDialogResources res_;
   private final Session session_;
   private final Server server_;
 }
