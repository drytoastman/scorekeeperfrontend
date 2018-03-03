package org.wwscc.tray;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class DockerMachineTests
{
    @Test
    public void scanEnv() throws Exception
    {
        String spaceduser = "SET DOCKER_TLS_VERIFY=1\r\n" +
                       "SET DOCKER_HOST=tcp://192.168.99.100:2376\r\n" +
                       "SET DOCKER_CERT_PATH=C:\\Users\\BSCC User\\.docker\\machine\\machines\\default double name\r\n" +
                       "SET DOCKER_MACHINE_NAME=default double name\r\n" +
                       "SET COMPOSE_CONVERT_WINDOWS_PATHS=true\r\n" +
                       "REM Run this command to configure your shell:\r\n" +
                       "REM     @FOR /f \"tokens=*\" %i IN ('docker-machine env --shell cmd') DO @%I";

        Map<String, String> env = DockerMachine.scanenv(spaceduser);

        Assert.assertEquals("1", env.get("DOCKER_TLS_VERIFY"));
        Assert.assertEquals("tcp://192.168.99.100:2376", env.get("DOCKER_HOST"));
        Assert.assertEquals("C:\\Users\\BSCC User\\.docker\\machine\\machines\\default double name", env.get("DOCKER_CERT_PATH"));
        Assert.assertEquals("default double name", env.get("DOCKER_MACHINE_NAME"));
    }
}
