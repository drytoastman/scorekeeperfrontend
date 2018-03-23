/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.apache.http.ssl.SSLContexts;

/**
 * Load docker certs from file into an apache SSLContext
 */
public class DockerCertificates
{
    public static SSLContext createContext(Path dir) throws GeneralSecurityException, IOException
    {
        Path capath   = dir.resolve("ca.pem");
        Path keypath  = dir.resolve("key.pem");
        Path certpath = dir.resolve("cert.pem");

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        keyStore.setKeyEntry("key", readPrivateKey(keypath), "asdf".toCharArray(), readCertificates(certpath));

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        for (Certificate ca : readCertificates(capath)) {
            X509Certificate crt = (X509Certificate)ca;
            String alias = crt.getSubjectX500Principal().getName();
            trustStore.setCertificateEntry(alias, ca);
        }

        return SSLContexts.custom().loadTrustMaterial(trustStore, null).loadKeyMaterial(keyStore, "asdf".toCharArray()).build();
    }


    /* I'm not loading 4MB of bouncycastle dependencies just to do this  */
    private static byte[] pkcs8wrap(byte[] content)
    {
        short clen = (short)content.length;
        short flen = (short)(clen + 22);
        short tlen = (short)(flen + 4);

        ByteBuffer buf = ByteBuffer.allocate(tlen);
        buf.put(new byte[] { 0x30, (byte)0x82 }).putShort(flen); // sequence + length
        buf.put(new byte[] { 0x02, 0x01, 0x00 }); // integer 0
        buf.put(new byte[] { 0x30, 0x0D, 0x06, 0x09, 0x2A, (byte)0x86, 0x48, (byte)0x86, (byte)0xF7, 0x0D, 0x01, 0x01, 0x01 }); // OID PKCS1
        buf.put(new byte[] { 0x05, 0x00 }); // null
        buf.put(new byte[] { 0x04, (byte)0x82 }).putShort(clen);
        buf.put(content);
        return buf.array();
    }

    private static PrivateKey readPrivateKey(Path file) throws IOException, GeneralSecurityException
    {
        List<String> lines = Files.readAllLines(file);
        boolean pcks1 = lines.get(0).contains("RSA PRIVATE");
        lines.removeIf(s -> s.contains("--"));
        String encoded = String.join("", lines);

        byte[] data = Base64.getDecoder().decode(encoded);
        if (pcks1)
            data = pkcs8wrap(data);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(data);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }

    private static Certificate[] readCertificates(Path file) throws CertificateException, IOException
    {
        try (InputStream inputStream = Files.newInputStream(file)) {
            final CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certs = cf.generateCertificates(inputStream);
            return certs.toArray(new Certificate[0]);
        }
    }
}
