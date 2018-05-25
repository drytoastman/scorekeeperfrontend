/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.rules.ExternalResource;
import org.wwscc.system.docker.DockerAPI;
import org.wwscc.system.docker.DockerContainer;
import org.wwscc.system.docker.DockerMachine;
import org.wwscc.util.AppSetup;
import org.wwscc.util.BroadcastState;

public class TestDatabaseContainer extends ExternalResource
{
    static DockerAPI docker;
    static String TestContainerImage = "drytoastman/scdb:latest";
    static String TestContainerName  = "testdb";
    static String TestNetName        = "testnet";
    static DockerContainer container;


    @Override
    protected void before() throws Throwable
    {
        AppSetup.unitLogging();
        BroadcastState<Map<String,String>> env = new BroadcastState<Map<String,String>>(null, null);

        container = new DockerContainer(TestContainerName, TestContainerImage, TestNetName);
        container.addPort("127.0.0.1", 6432, 6432);
        container.addPort("0.0.0.0", 54329, 5432);

        docker = new DockerAPI();
        if (DockerMachine.machinepresent()) {
            DockerMachine.machineenv(env);
            docker.setup(env.get());
        } else {
            docker.setup(new HashMap<>());
        }
        docker.teardown(Arrays.asList(container), null);
        docker.networkUp(TestNetName);
        docker.containersUp(Arrays.asList(container), null);

        while (!Database.testUp())
            Thread.sleep(1000);
        Database.openSeries("testseries", 0);
    }

    @Override
    protected void after()
    {
        Database.d.close();
        docker.kill(container);
    }
}
