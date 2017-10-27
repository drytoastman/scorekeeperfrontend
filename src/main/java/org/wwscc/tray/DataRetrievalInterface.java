package org.wwscc.tray;

import java.nio.file.Path;

/**
 * Interface for retrieving data from containers.  Allows for wrapping of implementations
 */
public interface DataRetrievalInterface 
{
    /**
     * Call through docker to run pg_dump and pipe the output to file
     * @param file the file to write the backup data to
     * @return true if succeeded
     */
    public boolean dumpDatabase(Path file);
    
    /**
     * Call through docker to copy log files from container to host
     * @param dir the directory to copy the logs to
     * @return true if succeeded
     */
    public boolean copyLogs(Path dir);
}
