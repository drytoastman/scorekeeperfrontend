
package org.wwscc.system.docker;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

/**
 * Basically doing same thing as UnixSocket from jr-unixsocket
 * with minimal implementation
 */
public class WindowsPipe extends java.net.Socket
{
    private RandomAccessFile raf;
    private FileChannel chan;

    private InputStream in;
    private OutputStream out;

    public WindowsPipe(String name) throws FileNotFoundException
    {
        raf = new RandomAccessFile(name, "rw");
        chan = raf.getChannel();

        in = Channels.newInputStream(chan);
        out = Channels.newOutputStream(chan);
    }


    @Override
    public void close() throws IOException {
        try {
            chan.close();
            raf.close();
        } catch (IOException e) {}
    }

    @Override
    public void connect(SocketAddress addr, int timeout) throws IOException {
        throw new SocketException("I'm always connected");
    }

    @Override
    public void bind(SocketAddress local) throws IOException {
        throw new SocketException("I don't bind");
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        System.out.println("set timeout?");
    }


    @Override public InputStream getInputStream() throws IOException { return in; }
    @Override public OutputStream getOutputStream() throws IOException { return out; }

    @Override public boolean isBound() { return true; }
    @Override public boolean isClosed() { return !chan.isOpen(); }
    @Override public boolean isConnected() { return true; }
}
