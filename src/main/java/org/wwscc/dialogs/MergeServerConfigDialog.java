package org.wwscc.dialogs;

import java.util.List;

import org.wwscc.storage.MergeServer;

import net.miginfocom.swing.MigLayout;

public class MergeServerConfigDialog extends BaseDialog<List<MergeServer>>
{
    public MergeServerConfigDialog(List<MergeServer> servers)
    {
        super(new MigLayout("fill", "fill", ""), false);
        result = servers;
        mainPanel.add(label("Server", true), "");
        mainPanel.add(label("Merge Interval", true), "");
        mainPanel.add(label("Connect timeout", true ), "wrap");
        for (MergeServer s : servers) {
            if (s.isLocalHost())
                continue;
            mainPanel.add(label(s.getHostname(), false), "");
            mainPanel.add(ientry(s.getServerId()+"waittime", s.getWaitTime()), "");
            mainPanel.add(ientry(s.getServerId()+"ctimeout", s.getConnectTimeout()), "wrap");
        }
    }

    @Override
    public List<MergeServer> getResult()
    {
        for (MergeServer s : result) {
            if (s.isLocalHost())
                continue;
            s.setWaitTime(getEntryInt(s.getServerId()+"waittime"));
            s.setConnectTimeout(getEntryInt(s.getServerId()+"ctimeout"));
        }
        return result;
    }

    @Override
    public boolean verifyData()
    {
        return true;
    }
}
