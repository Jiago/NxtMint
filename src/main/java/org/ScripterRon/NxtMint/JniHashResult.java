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

/**
 * Result returned by a JNI hash function
 */
public class JniHashResult {
    
    /** Nonce meets target */
    private final boolean meetsTarget;
    
    /** Nonce */
    private final long nonce;
    
    /** Hash count */
    private final int count;
    
    /**
     * Create the hash result
     * 
     * @param       meetsTarget         Nonce meets the target
     * @param       nonce               Nonce
     * @param       count               Hash count
     */
    public JniHashResult(boolean meetsTarget, long nonce, int count) {
        this.meetsTarget = meetsTarget;
        this.nonce = nonce;
        this.count = count;
    }
    
    /**
     * Check if the target was meet
     * 
     * @return                          TRUE if the target was met
     */
    public boolean isSolved() {
        return meetsTarget;
    }
    
    /**
     * Return the nonce
     * 
     * @return                          Nonce
     */
    public long getNonce() {
        return nonce;
    }
    
    /**
     * Return the hash count
     * 
     * @return                          Hash count
     */
    public int getCount() {
        return count;
    }
}
