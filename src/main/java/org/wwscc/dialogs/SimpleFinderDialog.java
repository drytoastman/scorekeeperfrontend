/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.awt.Component;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.json.simple.JSONObject;
import org.wwscc.util.Discovery;
import org.wwscc.util.Discovery.DiscoveryListener;
import org.wwscc.util.Resources;

import net.miginfocom.swing.MigLayout;

/**
 * Dialog that queries for a service on the network.
 */
public class SimpleFinderDialog extends BaseDialog<InetSocketAddress> implements ListSelectionListener
{
	//private static final Logger log = Logger.getLogger(SimpleFinderDialog.class.getCanonicalName());
	public static final String BWTIMER_TYPE  = "_bwtimer._tcp.local.";
	public static final String PROTIMER_TYPE = "_protimer._tcp.local.";
	public static final String DATABASE_TYPE = "_postgresql._tcp.local.";
			
	private JServiceList list;
	private List<String> services;
	
	/**
	 * shortcut when only looking for a single name
	 * @param serviceName the name of the servers to discover
	 */
	public SimpleFinderDialog(String serviceName)
	{
		this(Arrays.asList(new String[] { serviceName }));
	}
	
	/**
	 * Create the dialog
	 * 
	 * @param serviceNames the service names to look for
	 */
    public SimpleFinderDialog(List<String> serviceNames)
	{
		super(new MigLayout(""), false);

		// some defaults
		Map<String, Icon> iconMap = new HashMap<String, Icon>();
		iconMap.put(BWTIMER_TYPE, new ImageIcon(Resources.loadImage("timer.gif")));
		iconMap.put(PROTIMER_TYPE, new ImageIcon(Resources.loadImage("draglight.gif")));
		iconMap.put(DATABASE_TYPE, new ImageIcon(Resources.loadImage("server.gif")));
		
		list = new JServiceList(iconMap);
		list.addListSelectionListener(this);
		
		JScrollPane p = new JScrollPane(list);
        mainPanel.add(p, "w 300, h 400, growx, spanx 2, wrap");
    
		mainPanel.add(label("Host", false), "");
		mainPanel.add(entry("host", ""), "growx, wrap");
		mainPanel.add(label("Port", false), "");
		mainPanel.add(ientry("port", 0), "growx, wrap");
		result = null;

		services = serviceNames;
		for (String service : services) {
		    Discovery.get().addServiceListener(service, list);
		}
    }

	@Override
	public void close()
	{
        for (String service : services) {
            Discovery.get().removeServiceListener(service, list);
        }
		super.close();
	}
	
	/**
	 * Called after OK to verify data before closing.
	 */ 
	@Override
	public boolean verifyData()
	{
		try {
			result = new InetSocketAddress(getEntryText("host"), getEntryInt("port"));
		} catch (Exception e) {
			result = null;
		}
		return (result != null);
	}

	@Override
	public void valueChanged(ListSelectionEvent e) 
	{
	    ServiceInfo f = list.getSelectedValue();
		if (f != null)
		{
			setEntryText("host", f.ip.getHostAddress());
			setEntryText("port", String.valueOf(f.serviceport));
		}
		else
		{
			setEntryText("host", "");
			setEntryText("port", "");
		}
	}

}


class ServiceInfo 
{
    @Override
    public int hashCode() 
    {
        final int prime = 31;
        int result = prime + ip.hashCode();
        return prime * result + servicetype.hashCode();
    }

    @Override
    public boolean equals(Object obj) 
    {
        if (this == obj) return true;
        if ((obj == null) || (obj.getClass() != getClass())) return false;
        ServiceInfo other = (ServiceInfo)obj;
        return servicetype.equals(other.servicetype) && ip.equals(other.ip);
    }

    String servicetype;
    InetAddress ip;
    int serviceport;
    
    public ServiceInfo(String servicetype, InetAddress ip, JSONObject data)
    {
        this.servicetype = servicetype;
        this.ip          = ip;
        this.serviceport = ((Long)data.get("serviceport")).intValue();
    }
}


class JServiceList extends JList<ServiceInfo> implements DiscoveryListener 
{	
	private static final Logger log = Logger.getLogger(JServiceList.class.getCanonicalName());
	
	private static Map<InetAddress, String> hostnames = new Hashtable<InetAddress, String>();  // map IP to name, need to do async to keep GUI lively
	private static final Pattern lookslikeip = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");
	
	DefaultListModel<ServiceInfo> serviceModel;
	FoundServiceRenderer renderer;
	
	/**
	 * Create a JList that can listen to a ServiceFinder and update its list accordingly
	 * @param iconMap map of service types to an icon to use
	 */
	public JServiceList(Map<String, Icon> iconMap)
	{
		super();
		serviceModel = new DefaultListModel<ServiceInfo>();
		setModel(serviceModel);
		renderer = new FoundServiceRenderer(iconMap);
		setCellRenderer(renderer);
	}

	@Override
	public void serviceChange(String service, InetAddress ip, JSONObject data, boolean up)
	{
	    ServiceInfo info = new ServiceInfo(service, ip, data);
	    if (up) {
	        serviceModel.addElement(info);  // FINISH ME
	    } else {
	        serviceModel.removeElement(info);
	    }
        repaint();
	}
	

	/**
	 * Renderer for displaying Icon and service information based on FoundService objects
	 */
	class FoundServiceRenderer extends DefaultListCellRenderer
	{
		Map<String, Icon> iconMap;
		
		public FoundServiceRenderer(Map<String, Icon> map)
		{
			iconMap = map;
		}
		
		@Override
		 public Component getListCellRendererComponent(
	        JList<?> list,
	        Object value,
	        int index,
	        boolean isSelected,
	        boolean cellHasFocus)
		 {
			 super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			 if (value instanceof ServiceInfo)
			 {
			     ServiceInfo data = (ServiceInfo)value;
				 String hostname = "";
				 
				 if (hostnames.containsKey(data.ip))
					 hostname = hostnames.get(data.ip);
				 else
					 new Lookup(data.ip).execute();
				 
				 if (iconMap.containsKey(data.servicetype))
				 {
					setIcon(iconMap.get(data.servicetype));
					setText(String.format("%s (%s:%s)", hostname, data.ip, data.serviceport));
				 }
				else
				{
					setIcon(null);
					setText(String.format("%s (%s:%s) (type=%s)", hostname, data.ip, data.serviceport, data.servicetype));
				}
			 }
			 return this;
		 }
	}
	
	/**
	 * Use SwingWorker thread to do hostname lookup so GUI remains responsive
	 */
	class Lookup extends SwingWorker<String, Object>
	{
		InetAddress tofind;
		public Lookup(InetAddress src) { tofind = src; }
		@Override
		protected String doInBackground() throws Exception { return tofind.getHostName(); }
		@Override
		protected void done()  {
			try {
				if (lookslikeip.matcher(get()).matches())  // don't resolve to IP?
					return;
				hostnames.put(tofind, get());
				repaint();
			} catch (Exception e) {
				log.info("Failed to process hostname lookup: " + e.getMessage());
			}
		}
	}
}


