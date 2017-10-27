package org.wwscc.tray;

import java.util.Map;

public interface TrayStateInterface 
{
    public boolean isApplicationDone();
    public boolean shouldStopMachine();
    public boolean arePortsForwarded();
    public boolean isMachineReady();
    
    public void setMachineEnv(Map<String,String> env);
    public Map<String,String> getMachineEnv();
    
    public void setMachineStatus(String status);
    public void setBackendStatus(String status);
    public void setUsingMachine(boolean using);

    public void signalPortsReady(boolean ready);
    public void signalMachineReady(boolean ready);
    public void signalComposeReady(boolean ready);
            
    public void shutdownRequest();
}
