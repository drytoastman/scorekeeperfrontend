/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.util.HashMap;
import java.util.Map;

import org.wwscc.util.BroadcastState;

/**
 * Utility so DataEntry can poke the sync server without knowing about docker internals
 */
public class SyncPoker
{
    DockerAPI docker;
    BroadcastState<Map<String, String>> env;

    public SyncPoker()
    {
        // Do it in the background so we don't slow startup of application
        new Thread(() -> {
            DockerAPI api = new DockerAPI();
            if (DockerMachine.machinepresent()) {
                env = new BroadcastState<Map<String,String>>(null, null);
                DockerMachine.machineenv(env);
                api.setup(env.get());
            } else {
                api.setup(new HashMap<>());
            }
            docker = api;
        }).start();
    }

    public void poke()
    {
        docker.pokeByName("server");
    }
}
