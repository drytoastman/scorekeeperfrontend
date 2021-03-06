/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.math.BigInteger;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IdGenerator
{
    private static final Logger log = Logger.getLogger(IdGenerator.class.getName());
    public static final UUID namespaceDNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    // Upper long
    public static final long TIME_LOW_MASK = 0xFFFFFFFF00000000L;
    public static final long TIME_MID_MASK = 0x00000000FFFF0000L;
    public static final long VERSION_MASK  = 0x000000000000F000L;;
    public static final long TIME_HI_MASK  = 0x0000000000000FFFL;
    public static final long VERSION1      = 0x1000;

    // Lower long
    public static final long VARIANT_MASK  = 0xC000000000000000L;
    public static final long CLKSEQ_MASK   = 0x3FFF000000000000L;
    public static final long NODE_MASK     = 0x0000FFFFFFFFFFFFL;
    public static final long VARIANT1      = 0x8000000000000000L;

    // internal state
    private static long lasttime = 0;
    private static long counter = 0;
    private static long hwseq = 0;

    // Lazy initialization of hwseq, also retry if not successful on first try
    static private long getHWSeq()
    {
        if (hwseq != 0)
            return hwseq;

        try {
            NetworkInterface ni = Network.getPrimaryInterface();
            if (ni != null) {
                byte hwaddr[] = ni.getHardwareAddress();
                hwseq = (VARIANT1 & VARIANT_MASK)| (new Random().nextLong() & CLKSEQ_MASK) | (new BigInteger(1, hwaddr).longValue() & NODE_MASK);
            }
        } catch (SocketException se) {}

        return hwseq;
    }

    public synchronized static UUID generateId()
    {
        long ms = (System.currentTimeMillis() * 10000) + 0x01B21DD213814000L; // UUIDv1 uses .1uS increments from 15 Oct 1582
        if (lasttime == ms) {
            counter++;
        } else {
            counter = 0;
        }
        lasttime = ms;

        //  counter acts as a 100ns timer to deal with things happening faster than Java 1ms time
        long nstime = lasttime + counter;
        long timever = ((nstime << 32) & TIME_LOW_MASK) | ((nstime >> 16) & TIME_MID_MASK) | (VERSION1 & VERSION_MASK) | ((nstime >> 48) & TIME_HI_MASK);

        return new UUID(timever, getHWSeq());
    }

    public static UUID generateV5DNSId(String hostname)
    {
        try {
            ByteBuffer in = ByteBuffer.allocate(16 + hostname.length());
            in.putLong(namespaceDNS.getMostSignificantBits());
            in.putLong(namespaceDNS.getLeastSignificantBits());
            in.put(hostname.toLowerCase().getBytes());
            MessageDigest md = MessageDigest.getInstance("SHA1");
            ByteBuffer out = ByteBuffer.wrap(md.digest(in.array()));
            out.put(6, (byte)((out.get(6) & 0x0F) | 0x50));
            out.put(8, (byte)((out.get(8) & 0x3F) | 0x80));
            long hibyte = out.getLong();
            long lobyte = out.getLong();
            return new UUID(hibyte, lobyte);
        } catch (NoSuchAlgorithmException e) {
            log.log(Level.SEVERE, "\bFailed to load SHA1 for generating V5 DNS UUID", e);
            return null;
        }
    }
}
