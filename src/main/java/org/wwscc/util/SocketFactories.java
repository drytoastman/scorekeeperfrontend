/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

import org.apache.http.HttpHost;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/**
 * Implementations of Apache HttpClient ConnectionSocketFactory for local unix socket
 * and windows named pipes.
 */
public class SocketFactories
{
    public static class UnixSocketFactory implements ConnectionSocketFactory
    {
        String name;
        public UnixSocketFactory(String name)
        {
            this.name = name;
        }

        @Override
        public Socket createSocket(HttpContext context) throws IOException
        {
            return UnixSocketChannel.open(new UnixSocketAddress(name)).socket();
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException
        {
            return sock;
        }
    }


    public static class WindowsPipeFactory implements ConnectionSocketFactory
    {
        String name;
        public WindowsPipeFactory(String name)
        {
            this.name = name;
        }

        @Override
        public Socket createSocket(HttpContext context) throws IOException
        {
            return new WindowsPipe(name);
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException
        {
            return sock;
        }
    }


    /**
     * Basically doing same thing as UnixSocket from jr-unixsocket
     * with minimal implementation
     */
    static class WindowsPipe extends java.net.Socket
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
            if (timeout > 0) {
                new SocketException("I don't do timeout").printStackTrace();
            }
        }


        @Override public InputStream getInputStream() throws IOException { return in; }
        @Override public OutputStream getOutputStream() throws IOException { return out; }

        @Override public boolean isBound() { return true; }
        @Override public boolean isClosed() { return !chan.isOpen(); }
        @Override public boolean isConnected() { return true; }
    }
}
