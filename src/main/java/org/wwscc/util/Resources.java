package org.wwscc.util;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

import org.wwscc.tray.TrayApplication;

public class Resources 
{	
	private static final Logger log = Logger.getLogger(TrayApplication.class.getName());
	
	public static Image loadImage(String name)
	{
		String path = "/images/"+name;
		try {			
			return Toolkit.getDefaultToolkit().getImage(Resources.class.getResource(path));
		} catch (Exception e) {
			log.warning("Failed to load " + path);
			return new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
		}
	}

}
