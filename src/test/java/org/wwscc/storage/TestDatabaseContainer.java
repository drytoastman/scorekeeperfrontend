/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public static final UUID driverid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID carid1 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID carid2 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID carid3 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    public static final UUID eventid = UUID.fromString("00000000-0000-0000-0000-000000000010");

    static List<Object> newList(Object... args)
    {
        List<Object> l = new ArrayList<Object>();
        for (Object o : args)
            l.add(o);
        return l;
    }

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
            Thread.sleep(500);

        Database.openSeries("template", 0);
        PostgresqlDatabase pgdb = (PostgresqlDatabase)Database.d;
        pgdb.start();
        pgdb.executeUpdate("SET search_path='template','public'", null);
        pgdb.executeUpdate("INSERT INTO indexlist (indexcode, descrip, value) VALUES ('',   '', 1.000)", null);
        pgdb.executeUpdate("INSERT INTO indexlist (indexcode, descrip, value) VALUES ('i1', '', 1.000)", null);
        pgdb.executeUpdate("INSERT INTO classlist (classcode, descrip, indexcode, caridxrestrict, classmultiplier, carindexed, usecarflag, eventtrophy, champtrophy, secondruns, countedruns, modified) VALUES ('c1', '', '', '', 1.0, 't', 'f', 't', 't', 'f', 0, now())", null);
        pgdb.executeUpdate("INSERT INTO drivers  (driverid, firstname, lastname, email, username, password) VALUES (?, 'first', 'last', 'email', 'username', '$2b$12$g0z0JiGEuCudjhUF.5aawOlho3fpnPqKrV1EALTd1Cl/ThQQpFi2K')", newList(driverid));
        pgdb.executeUpdate("INSERT INTO cars     (carid, driverid, classcode, indexcode, number, useclsmult, attr, modified) VALUES (?, ?, 'c1', 'i1', 1, 'f', '{}', now())", newList(carid1, driverid));
        pgdb.executeUpdate("INSERT INTO cars     (carid, driverid, classcode, indexcode, number, useclsmult, attr, modified) VALUES (?, ?, 'c1', 'i1', 1, 'f', '{}', now())", newList(carid2, driverid));
        pgdb.executeUpdate("INSERT INTO cars     (carid, driverid, classcode, indexcode, number, useclsmult, attr, modified) VALUES (?, ?, 'c1', 'i1', 1, 'f', '{}', now())", newList(carid3, driverid));
        pgdb.executeUpdate("INSERT INTO events   (eventid, name, date, regclosed, attr)  VALUES (?, 'name', now(), now(), '{}')", newList(eventid));
        pgdb.executeUpdate("INSERT INTO runorder (eventid, course, rungroup, cars) VALUES (?, 1, 1, ?)", newList(eventid, new UUID[] {carid1, carid3}));
        pgdb.executeUpdate("INSERT INTO registered (eventid, carid) VALUES (?, ?)", newList(eventid, carid1));
        pgdb.executeUpdate("INSERT INTO runs (eventid, carid, course, run, raw, status, attr) VALUES (?, ?, 1, 1, 1.0, 'OK', '{}')", newList(eventid, carid1));
        pgdb.executeUpdate("INSERT INTO runs (eventid, carid, course, run, raw, status, attr) VALUES (?, ?, 1, 2, 2.0, 'OK', '{}')", newList(eventid, carid1));
        pgdb.executeUpdate("INSERT INTO runs (eventid, carid, course, run, raw, status, attr) VALUES (?, ?, 1, 3, 3.0, 'OK', '{}')", newList(eventid, carid1));
        pgdb.executeUpdate("INSERT INTO runs (eventid, carid, course, run, raw, status, attr) VALUES (?, ?, 1, 4, 4.0, 'OK', '{}')", newList(eventid, carid1));
        pgdb.commit();
    }

    @Override
    protected void after()
    {
        Database.d.close();
        docker.kill(container);
    }
}
