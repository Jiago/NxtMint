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
    private final MessageDigest digest;
    
    /**
     * Create a SHA-3 hash function
     */
    public HashSha3() {
        digest = new SHA3.DigestSHA3(256);
    }
    
    /**
     * Hash the input bytes
     * 
     * @param       input           Input bytes
     * @return                      Hash digest (32 bytes)
     */
    @Override
    public byte[] hash(byte[] input) {
        return digest.digest(input);
    }
}
