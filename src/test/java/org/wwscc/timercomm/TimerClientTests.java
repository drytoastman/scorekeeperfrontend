package org.wwscc.timercomm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wwscc.storage.LeftRightDialin;
import org.wwscc.storage.Run;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TimerClientTests
{
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static Object lastRun;
    private static Object lastDelete;
    private static Object lastDial;
    private Run.WithRowId run;

    @BeforeClass
    public static void init()
    {
        Messenger.setTestMode();
        Messenger.register(MT.TIMER_SERVICE_RUN,    (t,d) -> lastRun = d);
        Messenger.register(MT.TIMER_SERVICE_DELETE, (t,d) -> lastDelete = d);
        Messenger.register(MT.TIMER_SERVICE_DIALIN, (t,d) -> lastDial = d);
    }

    @Before
    public void setup()
    {
        run = new Run.WithRowId();
        run.setRaw(123.456);
        run.setCones(2);
        run.setGates(1);
        run.setStatus("RL");
        run.setCourse(2);
        run.setReaction(0.501);
        run.setSixty(2.100);
        run.setSegment(2, 22.333);
        run.setSegment(2, 23.456);
        lastRun = null;
        lastDelete = null;
        lastDial = null;
    }

    @Test
    public void encodeRun() throws Exception
    {
        String fields[] = new String[] { "course", "run", "cones", "gates", "status", "raw", "attr" };

        ObjectNode encoded = objectMapper.valueToTree(run);
        int index = 0;
        String found[] = new String[encoded.size()];
        Iterator<String> iter = encoded.fieldNames();
        while (iter.hasNext())
            found[index++] = iter.next();
        Arrays.sort(fields);
        Arrays.sort(found);
        Assert.assertArrayEquals(fields, found);

        String json = objectMapper.writeValueAsString(encoded);
        Run result = objectMapper.readValue(json, Run.class);
        Assert.assertEquals(run, result);
    }

    @Test
    public void sendRun() throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TimerClient c = new TimerClient(bytes);
        c.sendRun(run);
        c.processLine(bytes.toString());
        Assert.assertEquals(run, lastRun);
    }

    @Test
    public void deleteRun() throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TimerClient c = new TimerClient(bytes);
        c.deleteRun(run);
        c.processLine(bytes.toString());
        Assert.assertEquals(run, lastDelete);
    }

    @Test
    public void sendDial() throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TimerClient c = new TimerClient(bytes);
        LeftRightDialin lr = new LeftRightDialin(23.456, 34.567);
        c.sendDial(lr);
        c.processLine(bytes.toString());
        Assert.assertEquals(lr, lastDial);
    }

}
