/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import org.json.simple.JSONObject;

/**
 *
 * @author bwilson
 */
@SuppressWarnings("unchecked")
public class LeftRightDialin implements Serial
{
	public double left;
	public double right;

	public LeftRightDialin()
	{
	}

	public LeftRightDialin(double l, double r)
	{
		left = l;
		right = r;
	}

    @Override
	public void encode(JSONObject out)
	{
	    out.put("left", left);
        out.put("right", right);
	}

	@Override
	public void decode(JSONObject in)
	{
		left = (double)in.getOrDefault("left", -1);
		right = (double)in.getOrDefault("right", -1);
	}
}