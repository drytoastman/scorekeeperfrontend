/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import org.junit.rules.ExternalResource;
import org.wwscc.system.docker.DockerContainer;
import org.wwscc.system.docker.DockerMachine;
import org.wwscc.util.AppSetup;

public class TestDatabaseContainer extends ExternalResource
{
    static DockerContainer container;

    @Override
    protected void before() throws Throwable
    {
        AppSetup.unitLogging();
        container = new DockerContainer("drytoastman/scdb:testdb", "testdb");
        container.setMachineEnv(DockerMachine.machineenv());
        container.addPort("127.0.0.1:6432", "6432");
        container.addPort("54329", "5432");
        container.createNetsAndVolumes();
        container.start();
        Database.waitUntilUp();
        Database.openSeries("testseries", 0);
    }

    @Override
    protected void after()
    {
        Database.d.close();
        container.kill();  // comment out to leave container running after tests
    }
}
