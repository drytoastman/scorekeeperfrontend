/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import org.json.simple.JSONObject;

public interface Serial
{
	public void encode(JSONObject o);
	public void decode(JSONObject o);
}
