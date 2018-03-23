/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.util.Map;

import org.junit.rules.ExternalResource;
import org.wwscc.system.docker.DockerAPI;
import org.wwscc.system.docker.DockerContainer;
import org.wwscc.system.docker.DockerMachine;
import org.wwscc.util.AppSetup;

public class TestDatabaseContainer extends ExternalResource
{
    static DockerAPI docker;
    static String TestContainerImage = "drytoastman/scdb:testdb";

    @Override
    protected void before() throws Throwable
    {
        AppSetup.unitLogging();

        DockerContainer container = new DockerContainer(TestContainerImage, "testdb");
        container.addPort("127.0.0.1", 6432, 6432);
        container.addPort("0.0.0.0", 54329, 5432);

        docker = new DockerAPI();
        docker.setup(DockerMachine.machineenv());
        docker.pull(TestContainerImage);
        docker.networkDelete(DockerContainer.NET_NAME);
        docker.networkCreate(DockerContainer.NET_NAME);
        container.up(docker);

        Database.waitUntilUp();
        Database.openSeries("testseries", 0);
    }

    @Override
    protected void after()
    {
        Database.d.close();
        docker.kill("testdb");
    }
}
