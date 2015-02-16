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
import static org.ScripterRon.NxtMint.Main.log;

import org.bouncycastle.jcajce.provider.digest.SHA3;
import java.security.MessageDigest;
import static org.ScripterRon.NxtMint.HashFunction.jniAvailable;

/**
 * SHA-3 hash function
 */
public class HashSha3 extends HashFunction {

    /** SHA-3 message digest */
    private final MessageDigest md;

    /** Input data */
    private final byte[] input = new byte[40];

    /** Target data */
    private final byte[] target = new byte[32];

    /** JNI hash function */
    private native JniHashResult JniHash(byte[] input, byte[] target, long nonce, int count);

    /**
     * Create a SHA-3 hash function
     */
    public HashSha3() {
        md = new SHA3.DigestSHA3(256);
    }

    /**
     * Hash the input bytes
     *
     * @param       inputBytes      Input bytes (40 bytes)
     * @param       targetBytes     Target (32 bytes)
     * @param       initialNonce    Initial nonce
     * @return                      Hash digest (32 bytes)
     */
    @Override
    public boolean hash(byte[] inputBytes, byte[] targetBytes, long initialNonce) {
        int count = 1024*1024;
        boolean meetsTarget = false;
        //
        // Use the JNI hash function if it is available
        //
        if (jniAvailable) {
            JniHashResult result = JniHash(inputBytes, targetBytes, initialNonce, 2*count);
            if (result != null) {
                meetsTarget = result.isSolved();
                nonce = result.getNonce();
                hashCount = result.getCount();
            } else {
                log.error("No result returned by JniSha3");
            }
            return meetsTarget;
        }
        //
        // Use the Java hash function
        //
        System.arraycopy(inputBytes, 0, input, 0, 40);
        System.arraycopy(targetBytes, 0, target, 0, 32);
        nonce = initialNonce;
        hashCount = 0;
        Thread thread = Thread.currentThread();
        //
        // Keep hashing until we meet the target or the maximum loop count is reached
        //
        for (int i=0; i<count && !meetsTarget; i++) {
            if (thread.isInterrupted())
                break;
            //
            // Note that the nonce is stored in the first 8 bytes of the input data.  We will increment
            // it each time through the hash loop.
            //
            nonce++;
            input[0] = (byte)nonce;
            input[1] = (byte)(nonce>>8);
            input[2] = (byte)(nonce>>16);
            input[3] = (byte)(nonce>>24);
            input[4] = (byte)(nonce>>32);
            input[5] = (byte)(nonce>>40);
            input[6] = (byte)(nonce>>48);
            input[7] = (byte)(nonce>>56);
            //
            // Do the hash
            //
            byte[] output = md.digest(input);
            hashCount++;
            //
            // Check if we have met the target
            //
            meetsTarget = true;
            for (int j=31; j>=0; j--) {
                int b0 = (int)output[j]&0xff;
                int b1 = (int)target[j]&0xff;
                if (b0 < b1)
                    break;
                if (b0 > b1) {
                    meetsTarget = false;
                    break;
                }
            }
            if (meetsTarget)
                System.arraycopy(output, 0, digest, 0, 32);
        }
        return meetsTarget;
    }
}
