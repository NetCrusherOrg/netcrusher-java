package org.netcrusher.test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Md5DigestFactory {

    public static final byte[] HEAD_TOKEN = { 0, 1, 2, 3, 4, 5, 6, 7 };

    private Md5DigestFactory() {
    }

    public static MessageDigest createDigest() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Fail to create digest", e);
        }

        md.reset();

        return md;
    }

}
