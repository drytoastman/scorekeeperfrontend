package org.wwscc.fxchallenge;

import java.util.logging.Level;
import javafx.application.Application;
import javafx.application.Platform;

import org.wwscc.util.AppSetup;
import org.wwscc.util.Messenger;

public class MainHack {
    public static void main(String args[])
    {
        try
        {
            Messenger.setCallbackMode((t, d) -> { Platform.runLater(() -> Messenger.sendEventNowWrapper(t, d)); });
            AppSetup.appSetup("challengegui", new AlertHandlerFX());
            Application.launch(FXChallengeGUI.class, args);
        }
        catch (Throwable e)
        {
            FXChallengeGUI.log.log(Level.SEVERE, "\bFailed to start Challenge GUI: " + e, e);
        }
    }
}
