package org.wwscc.fxchallenge;

import java.util.logging.Level;
import javafx.application.Application;

import org.wwscc.util.AppSetup;
import org.wwscc.util.AppSetup.Mode;
import org.wwscc.util.Messenger;

public class MainHack {
    public static void main(String args[])
    {
        try
        {
            Messenger.setFXMode();
            AppSetup.appSetup("challengegui", Mode.FX_MODE);
            Application.launch(FXChallengeGUI.class, args);
        }
        catch (Throwable e)
        {
            FXChallengeGUI.log.log(Level.SEVERE, "\bFailed to start Challenge GUI: " + e, e);
        }
    }
}
