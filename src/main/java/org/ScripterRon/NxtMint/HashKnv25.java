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

/**
 * KECCAK25 hash algorithm for Monetary System currencies
 * 
 * Distributed as part of the Nxt reference software (NRS)
 */
public class HashKnv25 extends HashFunction {

    /** Hash constants */
    private static final long[] constants = {
        1L, 32898L, -9223372036854742902L, -9223372034707259392L, 32907L,
        2147483649L, -9223372034707259263L, -9223372036854743031L, 138L, 136L,
        2147516425L, 2147483658L, 2147516555L, -9223372036854775669L, -9223372036854742903L,
        -9223372036854743037L, -9223372036854743038L, -9223372036854775680L, 32778L, -9223372034707292150L,
        -9223372034707259263L, -9223372036854742912L, 2147483649L, -9223372034707259384L, 1L
    };
    
    /** Input data */
    private final byte[] input = new byte[40];
    
    /** Target data */
    private final byte[] target = new byte[32];
    
    /**
     * Create a new KECCAK25 hash function
     */
    public HashKnv25() {
        super();
    }
    
    /** JNI hash function */
    private native JniHashResult JniHash(byte[] input, byte[] target, long nonce, int count);

    /**
     * Hash the input bytes
     * @param       inputBytes      Input bytes (40 bytes)
     * @param       targetBytes     Target bytes (32 bytes)
     * @param       initialNonce    Initial nonce
     * @return                      TRUE if the target is met
     */
    @Override
    public boolean hash(byte[] inputBytes, byte[] targetBytes, long initialNonce) {
        int count = 512*1024;
        boolean meetsTarget = false;
        //
        // Use the JNI hash function if it is available
        //
        if (jniAvailable) {
            JniHashResult result = JniHash(inputBytes, targetBytes, initialNonce, count);
            if (result != null) {
                meetsTarget = result.isSolved();
                nonce = result.getNonce();
                hashCount = result.getCount();
            } else {
                log.error("No result returned by JniKnv25");
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
            meetsTarget = doHash();
            hashCount++;
        }
        return meetsTarget;
    }
    
    /**
     * Perform a single hash
     * 
     * @return                      TRUE if the target is met
     */
    private boolean doHash() {
        //
        // Initialize the hash state
        //
        // Note that the nonce is stored in the first 8 bytes of the input data.  We will increment
        // it each time through the hash loop.
        //
        nonce++;
        long state0 = nonce;
        long state1 = ((long)input[8] & 0xFF) | 
                        (((long)input[9] & 0xFF) << 8) | 
                        (((long)input[10] & 0xFF) << 16) | 
                        (((long)input[11] & 0xFF) << 24) | 
                        (((long)input[12] & 0xFF) << 32) | 
                        (((long)input[13] & 0xFF) << 40) | 
                        (((long)input[14] & 0xFF) << 48) | 
                        (((long)input[15] & 0xFF) << 56);
        long state2 = ((long)input[16] & 0xFF) | 
                        (((long)input[17] & 0xFF) << 8) | 
                        (((long)input[18] & 0xFF) << 16) | 
                        (((long)input[19] & 0xFF) << 24) | 
                        (((long)input[20] & 0xFF) << 32) | 
                        (((long)input[21] & 0xFF) << 40) | 
                        (((long)input[22] & 0xFF) << 48) | 
                        (((long)input[23] & 0xFF) << 56);
        long state3 = ((long)input[24] & 0xFF) | 
                        (((long)input[25] & 0xFF) << 8) | 
                        (((long)input[26] & 0xFF) << 16) | 
                        (((long)input[27] & 0xFF) << 24) | 
                        (((long)input[28] & 0xFF) << 32) | 
                        (((long)input[29] & 0xFF) << 40) | 
                        (((long)input[30] & 0xFF) << 48) | 
                        (((long)input[31] & 0xFF) << 56);
        long state4 = ((long)input[32] & 0xFF) | 
                        (((long)input[33] & 0xFF) << 8) | 
                        (((long)input[34] & 0xFF) << 16) | 
                        (((long)input[35] & 0xFF) << 24) | 
                        (((long)input[36] & 0xFF) << 32) | 
                        (((long)input[37] & 0xFF) << 40) | 
                        (((long)input[38] & 0xFF) << 48) | 
                        (((long)input[39] & 0xFF) << 56);      
        long state5 = 1;
        long state16 = -9223372036854775808L;
        long state6=0, state7=0, state8=0, state9=0, state10=0, state11=0, state12=0, 
                state13=0, state14=0, state15=0, state17=0, state18=0, state19=0, 
                state20=0, state21=0, state22=0, state23=0, state24=0;
        //
        // Calculate the hash digest
        //
        int i;
        for (i=0; i<25;) {
            long t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19;
            t1 = state0 ^ state5 ^ state10 ^ state15 ^ state20;
            t2 = state2 ^ state7 ^ state12 ^ state17 ^ state22;
            t3 = t1 ^ ((t2 << 1) | (t2 >>> (64-1)));
            t12 = state1 ^ t3;

            t4 = state1 ^ state6 ^ state11 ^ state16 ^ state21;
            t5 = state3 ^ state8 ^ state13 ^ state18 ^ state23;
            t6 = t4 ^ ((t5 << 1) | (t5 >>> (64-1)));
            t13 = state2 ^ t6;

            t7 = state4 ^ state9 ^ state14 ^ state19 ^ state24;
            t8 = ((t4 << 1) | (t4 >>> (64-1))) ^ t7;
            t16 = state6 ^ t3;
            t16 = ((t16 << 44) | (t16 >>> (64-44)));
            state2 = state12 ^ t6;
            state2 = (state2 << 43) | (state2 >>> (64-43));
            t9 = state0 ^ t8;
            state0 = t9 ^ (~t16 & state2) ^ constants[i++];

            t10 = ((t7 << 1) | (t7 >>> (64-1))) ^ t2;
            t14 = state3 ^ t10;
            state3 = state18 ^ t10;
            state3 = (state3 << 21) | (state3 >>> (64-21));
            state1 = t16 ^ (~state2 & state3);

            t11 = ((t1 << 1) | (t1 >>> (64-1))) ^ t5;
            t15 = state4 ^ t11;
            state4 = state24 ^ t11;
            state4 = (state4 << 14) | (state4 >>> (64-14));
            state2 ^= (~state3) & state4;

            state3 ^= (~state4) & t9;
            state4 ^= (~t9) & t16;
            t16 = state5 ^ t8;
            t17 = state7 ^ t6;
            t19 = state9 ^ t11;
            t19 = (t19 << 20) | (t19 >>> (64-20));
            t14 = (t14 << 28) | (t14 >>> (64-28));
            state7 = state10 ^ t8;
            state7 = (state7 << 3) | (state7 >>> (64-3));
            state5 = t14 ^ (~t19 & state7);

            t18 = state8 ^ t10;
            state8 = state16 ^ t3;
            state8 = (state8 << 45) | (state8 >>> (64-45));
            state6 = t19 ^ (~state7 & state8);

            state9 = state22 ^ t6;
            state9 = (state9 << 61) | (state9 >>> (64-61));
            state7 ^= (~state8) & state9;

            state8 ^= ~state9 & t14;
            state9 ^= ~t14 & t19;
            t19 = state11 ^ t3;

            t12 = (t12 << 1) | (t12 >>> (64-1));
            t17 = (t17 << 6) | (t17 >>> (64-6));
            state12 = state13 ^ t10;
            state12 = (state12 << 25) | (state12 >>> (64-25));
            state10 = t12 ^ (~t17 & state12);

            state13 = state19 ^ t11;
            state13 = (state13 << 8) | (state13 >>> (64-8));
            state11 = t17 ^ (~state12 & state13);

            t14 = state14 ^ t11;
            state14 = state20 ^ t8;
            state14 = (state14 << 18) | (state14 >>> (64-18));
            state12 ^= ~state13 & state14;

            state13 ^= ~state14 & t12;
            state14 ^= ~t12 & t17;
            t12 = state15 ^ t8;
            t17 = state17 ^ t6;

            t16 = (t16 << 36) | (t16 >>> (64-36));
            t15 = (t15 << 27) | (t15 >>> (64-27));
            state17 = (t19 << 10) | (t19 >>> (64-10));
            state15 = t15 ^ (~t16 & state17);

            state18 = (t17 << 15) | (t17 >>> (64-15));
            state16 = t16 ^ (~state17 & state18);

            state19 = state23 ^ t10;
            state19 = (state19 << 56) | (state19 >>> (64-56));
            state17 ^= ~state18 & state19;

            state18 ^= (~state19) & t15;
            state19 ^= (~t15) & t16;
            t19 = state21 ^ t3;

            t13 = (t13 << 62) | (t13 >>> (64-62));
            t18 = (t18 << 55) | (t18 >>> (64-55));
            state22 = (t14 << 39) | (t14 >>> (64-39));
            state20 = t13 ^ (~t18 & state22);

            state23 = (t12 << 41) | (t12 >>> (64-41));
            state21 = t18 ^ (~state22 & state23);

            state24 = (t19 << 2) | (t19 >>> (64-2));
            state22 ^= ~state23 & state24;
            state23 ^= ~state24 & t13;
            state24 ^= ~t13 & t18;
        }
        //
        // Check if we met the target
        //
        boolean isSolved = true;
        long check;
        checkLoop: for (i=3; i>=0; i--) {
            if (i == 0)
                check = state0;
            else if (i == 1)
                check = state1;
            else if (i == 2)
                check = state2;
            else
                check = state3;
            for (int j=7; j>=0; j--) {
                int b0 = (int)(check>>(j*8))&0xff;
                int b1 = (int)(target[i*8+j])&0xff;
                if (b0 < b1)
                    break checkLoop;
                if (b0 > b1) {
                    isSolved = false;
                    break checkLoop;
                }
            }
        }
        //
        // Set the digest if we have a match
        //
        if (isSolved) {
            digest[0] = (byte)(state0);
            digest[1] = (byte)(state0 >> 8);
            digest[2] = (byte)(state0 >> 16);
            digest[3] = (byte)(state0 >> 24);
            digest[4] = (byte)(state0 >> 32);
            digest[5] = (byte)(state0 >> 40);
            digest[6] = (byte)(state0 >> 48);
            digest[7] = (byte)(state0 >> 56);
            digest[8] = (byte)(state1);
            digest[9] = (byte)(state1 >> 8);
            digest[10] = (byte)(state1 >> 16);
            digest[11] = (byte)(state1 >> 24);
            digest[12] = (byte)(state1 >> 32);
            digest[13] = (byte)(state1 >> 40);
            digest[14] = (byte)(state1 >> 48);
            digest[15] = (byte)(state1 >> 56);
            digest[16] = (byte)(state2);
            digest[17] = (byte)(state2 >> 8);
            digest[18] = (byte)(state2 >> 16);
            digest[19] = (byte)(state2 >> 24);
            digest[20] = (byte)(state2 >> 32);
            digest[21] = (byte)(state2 >> 40);
            digest[22] = (byte)(state2 >> 48);
            digest[23] = (byte)(state2 >> 56);
            digest[24] = (byte)(state3);
            digest[25] = (byte)(state3 >> 8);
            digest[26] = (byte)(state3 >> 16);
            digest[27] = (byte)(state3 >> 24);
            digest[28] = (byte)(state3 >> 32);
            digest[29] = (byte)(state3 >> 40);
            digest[30] = (byte)(state3 >> 48);
            digest[31] = (byte)(state3 >> 56); 
        }
        return isSolved;
    }
}
