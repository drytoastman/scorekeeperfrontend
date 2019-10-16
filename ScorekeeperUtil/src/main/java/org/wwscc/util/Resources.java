/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class Resources
{
    private static final Logger log = Logger.getLogger(Resources.class.getName());

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
