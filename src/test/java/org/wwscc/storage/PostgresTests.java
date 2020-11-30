/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.ResultSet;
import org.junit.ClassRule;
import org.junit.Test;

public class PostgresTests
{
    @ClassRule
    public static TestDatabaseContainer pc = new TestDatabaseContainer();

    @Test
    public void longQueryTest() throws Exception
    {
        PostgresqlDatabase pdb = (PostgresqlDatabase)Database.d;
        ResultSet rs = pdb.executeSelect("select pg_sleep(3);", null);  // should complete without exception
        assert(rs.next());
    }
}
