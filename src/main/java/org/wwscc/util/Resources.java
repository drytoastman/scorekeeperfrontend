package org.wwscc.util;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

import org.wwscc.tray.TrayMonitor;

public class Resources 
{	
	private static final Logger log = Logger.getLogger(TrayMonitor.class.getName());
	
	public static Image loadImage(String s)
	{
		try {
			return Toolkit.getDefaultToolkit().getImage(Resources.class.getResource(s));
		} catch (Exception e) {
			log.warning("Failed to load " + s);
			return new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
		}
	}

}
