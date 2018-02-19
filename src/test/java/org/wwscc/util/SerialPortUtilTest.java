package org.wwscc.util;

import java.io.ByteArrayInputStream;
import org.junit.Assert;
import org.junit.Test;
import org.wwscc.util.SerialPortUtil.LineBasedSerialBuffer;

public class SerialPortUtilTest
{
    @Test
    public void multipleNewlineTest() throws Exception
    {
        LineBasedSerialBuffer buffer = new LineBasedSerialBuffer();
        buffer.readData(new ByteArrayInputStream(new byte[] { 51, 52, 13, 54, 55, 10, 56, 57, 13, 10, 58, 59, 10, 13, 99 }));
        Assert.assertArrayEquals(new byte[] { 51,  52 }, buffer.getNextLine());
        Assert.assertArrayEquals(new byte[] { 54,  55 }, buffer.getNextLine());
        Assert.assertArrayEquals(new byte[] { 56,  57 }, buffer.getNextLine());
        Assert.assertArrayEquals(new byte[] { 58,  59 }, buffer.getNextLine());
        Assert.assertArrayEquals(null, buffer.getNextLine());
    }

    @Test
    public void singleInputsTest() throws Exception
    {
        LineBasedSerialBuffer buffer = new LineBasedSerialBuffer();

        buffer.readData(new ByteArrayInputStream(new byte[] { 51 }));
        Assert.assertArrayEquals(null, buffer.getNextLine());
        buffer.readData(new ByteArrayInputStream(new byte[] { 52 }));
        Assert.assertArrayEquals(null, buffer.getNextLine());

        buffer.readData(new ByteArrayInputStream(new byte[] { 13 }));
        Assert.assertArrayEquals(new byte[] { 51, 52 }, buffer.getNextLine());

        buffer.readData(new ByteArrayInputStream(new byte[] { 10 }));
        Assert.assertArrayEquals(null, buffer.getNextLine());

        buffer.readData(new ByteArrayInputStream(new byte[] { 53 }));
        Assert.assertArrayEquals(null, buffer.getNextLine());
        buffer.readData(new ByteArrayInputStream(new byte[] { 54 }));
        Assert.assertArrayEquals(null, buffer.getNextLine());

        buffer.readData(new ByteArrayInputStream(new byte[] { 10 }));
        Assert.assertArrayEquals(new byte[] { 53, 54 }, buffer.getNextLine());
    }
}
