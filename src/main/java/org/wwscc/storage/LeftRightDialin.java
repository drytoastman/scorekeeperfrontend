/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LeftRightDialin
{
    @JsonProperty
    public double left;
    @JsonProperty
    public double right;

    public LeftRightDialin()
    {
    }

    public LeftRightDialin(double l, double r)
    {
        left = l;
        right = r;
    }
}