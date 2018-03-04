/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import javax.swing.Action;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Resources;
import org.wwscc.util.WrappedAWTMenuItem;

public class ScorekeeperTrayIcon
{
    private static final Image coneok, conewarn;
    static
    {
        coneok   = Resources.loadImage("conesmall.png");
        conewarn = Resources.loadImage("conewarn.png");
    }

    TrayIcon trayIcon;

    public ScorekeeperTrayIcon(Actions actions) throws AWTException
    {
        PopupMenu trayPopup = new PopupMenu();
        trayPopup.add(new WrappedAWTMenuItem(actions.openStatus));
        trayPopup.addSeparator();
        for (Action a : actions.apps)
            trayPopup.add(new WrappedAWTMenuItem(a));
        trayPopup.addSeparator();
        trayPopup.add(new WrappedAWTMenuItem(actions.quit));

        trayIcon = new TrayIcon(conewarn, "Scorekeeper Monitor", trayPopup);
        trayIcon.setImageAutoSize(true);

        IconChoice ic = new IconChoice();
        Messenger.register(MT.BACKEND_READY, ic);
        Messenger.register(MT.WEB_READY, ic);
        SystemTray.getSystemTray().add(trayIcon);
    }

    class IconChoice implements MessageListener
    {
        boolean backend = false;
        boolean web = false;
        @Override public void event(MT type, Object data) {
            switch (type) {
                case BACKEND_READY: backend = (boolean)data; break;
                case WEB_READY: web = (boolean)data; break;
            }
            trayIcon.setImage(backend&&web ? coneok : conewarn);
        }
    }
}
