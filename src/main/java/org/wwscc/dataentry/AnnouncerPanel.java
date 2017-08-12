/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry;

import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Encompasses all the components in the announcer tab and handles the run change
 * events, keeping track of last to finish as well as next to finish result panels.
 */
public class AnnouncerPanel extends JFXPanel implements MessageListener
{
	WebEngine engine;
	
	public AnnouncerPanel()
	{
	    Messenger.register(MT.SERIES_CHANGED, this);
	    Messenger.register(MT.EVENT_CHANGED, this);  
	}
	
	private void setURL()
	{
	    if (engine == null)
	    {
	        WebView view = new WebView();
	        setScene(new Scene(view));
	        engine = view.getEngine();
	    }
	    
        String url = String.format("http://127.0.0.1/announcer/%s/event/%s/?mini=1", 
                DataEntry.state.getCurrentSeries(), DataEntry.state.getCurrentEventId());
        engine.load(url);
	}
	
	private void reload()
	{
	    if (engine != null) 
	        engine.reload();
	}

	@Override
	public void event(MT type, Object o)
	{
		switch (type)
		{
		    case SERIES_CHANGED:
		    case EVENT_CHANGED:
		        Platform.runLater(new Runnable () { public void run() { setURL(); }});
		        break;
		        
			case RUN_CHANGED:
			    Platform.runLater(new Runnable () { public void run() { reload(); }});
				break;
		}
	}
}
