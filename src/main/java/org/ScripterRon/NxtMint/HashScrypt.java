/*
 * Copyright 2015 Ronald W Hoffman.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ScripterRon.NxtMint;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * SCRYPT hash algorithm for Monetary System currencies
 * 
 * Distributed as part of the Nxt reference software (NRS)
 */
@SuppressWarnings({"PointlessBitwiseExpression", "PointlessArithmeticExpression"})
public class HashScrypt extends HashFunction {

    /** Scrypt message digest */
    private final Mac mac;
    
    private final byte[] H = new byte[32];
    private final byte[] B = new byte[128 + 4];
    private final int[] X = new int[32];
    private final int[] V = new int[32 * 1024];
    
    /**
     * Create a Scrypt hash function
     */
    public HashScrypt() {
        try {
            mac = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException exc) {
            throw new IllegalStateException("Unable to get HmacSHA256 message digest", exc);
        }
    }

    /**
     * Hash the input bytes
     * @param       input           Input (length must not exceed 40 bytes)
     * @return                      Hash output (32 bytes)
     */
    @Override
    public byte[] hash(final byte input[]) {
        int i, j, k;
        if (input.length > 40)
            throw new IllegalArgumentException("Input length is greater than 40 bytes");
        System.arraycopy(input, 0, B, 0, input.length);
        if (input.length<40)
            Arrays.fill(B, input.length, 40, (byte)0);
        try {
            mac.init(new SecretKeySpec(B, 0, 40, "HmacSHA256"));
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
        B[40] = 0;
        B[41] = 0;
        B[42] = 0;
        for (i=0; i<4; i++) {
            B[43] = (byte) (i + 1);
            mac.update(B, 0, 44);
            try {
                mac.doFinal(H, 0);
            } catch (ShortBufferException e) {
                throw new IllegalStateException(e);
            }
            for (j=0; j<8; j++) {
                X[i*8+j] = (H[j*4+0] & 0xff) << 0
                        | (H[j*4+1] & 0xff) << 8
                        | (H[j*4+2] & 0xff) << 16
                        | (H[j*4+3] & 0xff) << 24;
            }
        }
        for (i=0; i<1024; i++) {
            System.arraycopy(X, 0, V, i*32, 32);
            xorSalsa8(0, 16);
            xorSalsa8(16, 0);
        }
        for (i=0; i<1024; i++) {
            k = (X[16] & 1023) * 32;
            for (j=0; j<32; j++)
                X[j] ^= V[k+j];
            xorSalsa8(0, 16);
            xorSalsa8(16, 0);
        }
        for (i=0; i<32; i++) {
            B[i*4+0] = (byte) (X[i] >> 0);
            B[i*4+1] = (byte) (X[i] >> 8);
            B[i*4+2] = (byte) (X[i] >> 16);
            B[i*4+3] = (byte) (X[i] >> 24);
        }
        B[128+3] = 1;
        mac.update(B, 0, 128 + 4);
        try {
            mac.doFinal(H, 0);
        } catch (ShortBufferException e) {
            throw new IllegalStateException(e);
        }
        return H;
    }

    private void xorSalsa8(int di, int xi) {
        int x00 = (X[di + 0] ^= X[xi + 0]);
        int x01 = (X[di + 1] ^= X[xi + 1]);
        int x02 = (X[di + 2] ^= X[xi + 2]);
        int x03 = (X[di + 3] ^= X[xi + 3]);
        int x04 = (X[di + 4] ^= X[xi + 4]);
        int x05 = (X[di + 5] ^= X[xi + 5]);
        int x06 = (X[di + 6] ^= X[xi + 6]);
        int x07 = (X[di + 7] ^= X[xi + 7]);
        int x08 = (X[di + 8] ^= X[xi + 8]);
        int x09 = (X[di + 9] ^= X[xi + 9]);
        int x10 = (X[di + 10] ^= X[xi + 10]);
        int x11 = (X[di + 11] ^= X[xi + 11]);
        int x12 = (X[di + 12] ^= X[xi + 12]);
        int x13 = (X[di + 13] ^= X[xi + 13]);
        int x14 = (X[di + 14] ^= X[xi + 14]);
        int x15 = (X[di + 15] ^= X[xi + 15]);
        for (int i = 0; i < 8; i += 2) {
            x04 ^= Integer.rotateLeft(x00 + x12, 7);
            x08 ^= Integer.rotateLeft(x04 + x00, 9);
            x12 ^= Integer.rotateLeft(x08 + x04, 13);
            x00 ^= Integer.rotateLeft(x12 + x08, 18);
            x09 ^= Integer.rotateLeft(x05 + x01, 7);
            x13 ^= Integer.rotateLeft(x09 + x05, 9);
            x01 ^= Integer.rotateLeft(x13 + x09, 13);
            x05 ^= Integer.rotateLeft(x01 + x13, 18);
            x14 ^= Integer.rotateLeft(x10 + x06, 7);
            x02 ^= Integer.rotateLeft(x14 + x10, 9);
            x06 ^= Integer.rotateLeft(x02 + x14, 13);
            x10 ^= Integer.rotateLeft(x06 + x02, 18);
            x03 ^= Integer.rotateLeft(x15 + x11, 7);
            x07 ^= Integer.rotateLeft(x03 + x15, 9);
            x11 ^= Integer.rotateLeft(x07 + x03, 13);
            x15 ^= Integer.rotateLeft(x11 + x07, 18);
            x01 ^= Integer.rotateLeft(x00 + x03, 7);
            x02 ^= Integer.rotateLeft(x01 + x00, 9);
            x03 ^= Integer.rotateLeft(x02 + x01, 13);
            x00 ^= Integer.rotateLeft(x03 + x02, 18);
            x06 ^= Integer.rotateLeft(x05 + x04, 7);
            x07 ^= Integer.rotateLeft(x06 + x05, 9);
            x04 ^= Integer.rotateLeft(x07 + x06, 13);
            x05 ^= Integer.rotateLeft(x04 + x07, 18);
            x11 ^= Integer.rotateLeft(x10 + x09, 7);
            x08 ^= Integer.rotateLeft(x11 + x10, 9);
            x09 ^= Integer.rotateLeft(x08 + x11, 13);
            x10 ^= Integer.rotateLeft(x09 + x08, 18);
            x12 ^= Integer.rotateLeft(x15 + x14, 7);
            x13 ^= Integer.rotateLeft(x12 + x15, 9);
            x14 ^= Integer.rotateLeft(x13 + x12, 13);
            x15 ^= Integer.rotateLeft(x14 + x13, 18);
        }
        X[di + 0] += x00;
        X[di + 1] += x01;
        X[di + 2] += x02;
        X[di + 3] += x03;
        X[di + 4] += x04;
        X[di + 5] += x05;
        X[di + 6] += x06;
        X[di + 7] += x07;
        X[di + 8] += x08;
        X[di + 9] += x09;
        X[di + 10] += x10;
        X[di + 11] += x11;
        X[di + 12] += x12;
        X[di + 13] += x13;
        X[di + 14] += x14;
        X[di + 15] += x15;
    }
}
