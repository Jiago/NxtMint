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

import org.bouncycastle.jcajce.provider.digest.SHA3;
import java.security.MessageDigest;

/**
 * SHA-3 hash function
 */
public class HashSha3 extends HashFunction {
    
    /** SHA-3 message digest */
    private final MessageDigest md;
    
    /**
     * Create a SHA-3 hash function
     */
    public HashSha3() {
        md = new SHA3.DigestSHA3(256);
    }
    
    /**
     * Hash the input bytes
     * 
     * @param       input           Input bytes (40 bytes)
     * @param       target          Target (32 bytes)
     * @param       initialNonce    Initial nonce
     * @return                      Hash digest (32 bytes)
     */
    @Override
    public boolean hash(byte[] input, byte[] target, long initialNonce) {
        nonce = initialNonce;
        hashCount = 0;
        boolean meetsTarget = false;
        Thread thread = Thread.currentThread();
        //
        // Keep hashing until we meet the target or the maximum loop count is reached
        //
        for (int i=0; i<512*1024 && !meetsTarget; i++) {
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
