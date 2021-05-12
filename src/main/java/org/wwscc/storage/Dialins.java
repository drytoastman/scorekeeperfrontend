/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 */
public class Dialins 
{
	private class CarInfo
	{
		UUID   carid;
		String classcode;
		Double index;
		Double net;
		Double bonus;
		Double dial;
		Double classdiff;
	}

	private class CarInfoNet implements Comparator<CarInfo> {
		@Override
		public int compare(CarInfo o1, CarInfo o2) {
			return o1.net.compareTo(o2.net);
		}		
	}

	private class CarInfoDiff implements Comparator<CarInfo> {
		@Override
		public int compare(CarInfo o1, CarInfo o2) {
			return o1.classdiff.compareTo(o2.classdiff);
		}		
	}

	private Map <String, List<CarInfo>> classmap;   // used to determine class dialin basis
	private Map <UUID, CarInfo> carmap;     // map from carid to details for the Car
	
	public Dialins()
	{
		classmap = new HashMap<>();
		carmap   = new HashMap<>();
	}
	
	public void setEntrant(UUID carid, String classcode, double raw, double net, double index)
	{
		CarInfo c = new CarInfo();
		carmap.put(carid, c);
		c.carid     = carid;
		c.classcode = classcode;
		c.index     = index;
		c.net       = net;
		c.bonus     = raw/2.0;
		c.dial      = 999.999;
		c.classdiff = 666.666;
		
		if (!classmap.containsKey(c.classcode))
			classmap.put(c.classcode, new ArrayList<>());
		classmap.get(c.classcode).add(c);
	}
	
	public void finalizedialins()
	{
		for (List<CarInfo> l : classmap.values()) {
			if (l.size() == 0) continue;
			l.sort(new CarInfoNet());

			CarInfo lead = l.get(0);
			lead.dial = lead.bonus;
			lead.classdiff = 0.0;
			if (l.size() == 1) continue;
			
			double basis = lead.bonus * lead.index;
			lead.classdiff = lead.net - l.get(1).net;

			for (CarInfo info : l.subList(1, l.size())) {
				info.dial      = basis / info.index;
				info.classdiff = info.net - lead.net;
			}
		}
	}
	
	public double getNet(UUID carid)  { return carmap.get(carid).net; }
	public double getDiff(UUID carid) { return carmap.get(carid).classdiff; }
 	public double getDial(UUID carid, boolean bonus)
	{
		double ret;
		if (bonus)
			ret = carmap.get(carid).bonus;
		else
			ret = carmap.get(carid).dial;
		
		return (Math.round(ret * 1000.0))/1000.0;
	}

	public List<UUID> getNetOrder()
	{
		return mapSort(new CarInfoNet());
	}

	public List<UUID> getDiffOrder()
	{
		return mapSort(new CarInfoDiff());
	}

	private List<UUID> mapSort(Comparator<CarInfo> compare)
	{
		List<CarInfo> torder = new LinkedList<CarInfo>(carmap.values());
		Collections.sort(torder, compare);

		List<UUID> ids = new ArrayList<UUID>();
		for (CarInfo i : torder)
			ids.add(i.carid);
		
		return ids;
	}
}
