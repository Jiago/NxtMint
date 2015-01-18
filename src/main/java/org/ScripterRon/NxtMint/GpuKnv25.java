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
 * KECCAK25 hash algorithm for Monetary System currencies
 * 
 * GpuKnv25 uses the Aparapi package to calculate hashes using the graphics card
 * GPU.  The Aparapi and OpenCL runtime libraries must be available in either the
 * system path or the Java library path.  This GPU version of HashKnv25 is optimized
 * for the GPU and does not support generalized hashing.
 * 
 * Aparapi builds an OpenCL kernel from the Java bytecodes when the Kernel.execute()
 * method is invoked.  The compiled program is saved for subsequent executions by
 * the same kernel.
 * 
 * Aparapi supports a subset of Java functions:
 *   o Only primitive data types and single-dimension arrays are supported.
 *   o Objects cannot be created by the kernel program.  This means that kernel
 *     code cannot use the 'new' operation.
 *   o Java exceptions, enhanced 'for' statements and 'break' statements are not supported.
 *   o Variable assignment during expression evaluation is not supported.
 *   o Only data belonging to the enclosing class can be copied to/from GPU memory.
 *   o Primitives and objects can be read from kernel code but only objects can be
 *     written by kernel code.
 *   o Aparapi normally defines kernel groups based on the capabilities of the graphics card.
 *     The Range object can be used to define an explicit grouping.
 *   o Untagged data is shared by all instances of the kernel while data tagged as local is shared
 *     by all instances in the same kernel group.  Data is tagged by appending "_$local$" to
 *     the object name.
 */
public class GpuKnv25 extends GpuFunction {
        
    /** Hash algorithm constants */
    private final long[] constants = {
        1L, 32898L, -9223372036854742902L, -9223372034707259392L, 32907L,
        2147483649L, -9223372034707259263L, -9223372036854743031L, 138L, 136L,
        2147516425L, 2147483658L, 2147516555L, -9223372036854775669L, -9223372036854742903L,
        -9223372036854743037L, -9223372036854743038L, -9223372036854775680L, 32778L, -9223372034707292150L,
        -9223372034707259263L, -9223372036854742912L, 2147483649L, -9223372034707259384L, 1L
    };

    /** Input data */
    private final long[] input = new long[5];
    
    /** Hash target */
    private final byte[] target = new byte[32];
    
    /** Hash digest */
    private final long[] output = new long[4];
    
    /** TRUE if the target is met */
    private final boolean[] done = new boolean[1];
    
    /** Nonce used to solve the hash */
    private final long[] nonce = new long[1];

    /**
     * Create the GPU hash function
     */
    public GpuKnv25() {
    }
    
    /**
     * Return the GPU intensity scaling factor.  The kernel execution range is calculated
     * as the GPU intensity times the scaling factor.
     * 
     * @return                      Scaling factor
     */
    @Override
    public int getScale() {
        return 1024;
    }

    /**
     * Set the input data and the hash target
     * 
     * The input data is in the following format:
     *     Bytes 0-7:   Initial nonce (modified for each kernel instance)
     *     Bytes 8-15:  Currency identifier
     *     Bytes 16-23: Currency units
     *     Bytes 24-31: Minting counter
     *     Bytes 32-39: Account identifier
     * 
     * The hash target and hash digest are treated as unsigned 32-byte numbers in little-endian format.
     * The digest must be less than the target in order to be a solution.
     * 
     * @param       inputBytes      Bytes to be hashed (40 bytes)
     * @param       targetBytes     Hash target (32 bytes)
     */
    @Override
    public void setInput(byte[] inputBytes, byte[] targetBytes) {
        if (inputBytes.length != 40)
            throw new IllegalArgumentException("Input data length must be 40 bytes");
        if (targetBytes.length != 32)
            throw new IllegalArgumentException("Target data length must be 32 bytes");
        //
        // Convert the input data to an array of unsigned longs
        //
        for (int i=0, offset=0; i<5; i++, offset+=8)
            input[i] = ((long)(inputBytes[offset] & 0xFF)) | 
                       (((long)(inputBytes[offset+1] & 0xFF)) << 8) | 
                       (((long)(inputBytes[offset+2] & 0xFF)) << 16) | 
                       (((long)(inputBytes[offset+3] & 0xFF)) << 24) | 
                       (((long)(inputBytes[offset+4] & 0xFF)) << 32) | 
                       (((long)(inputBytes[offset+5] & 0xFF)) << 40) | 
                       (((long)(inputBytes[offset+6] & 0xFF)) << 48) | 
                       (((long)(inputBytes[offset+7] & 0xFF)) << 56);
        //
        // Save the hash target
        //
        System.arraycopy(targetBytes, 0, target, 0, target.length);
        //
        // Indicate no solution found yet
        //
        done[0] = false;
        nonce[0] = 0;
    }

    /**
     * Check if we found a solution
     * 
     * @return                      TRUE if we found a solution
     */
    @Override
    public boolean isSolved() {
        return done[0];
    }
    
    /**
     * Return the nonce used to solve the hash
     * 
     * @return                      Nonce
     */
    @Override
    public long getNonce() {
        return nonce[0];
    }

    /**
     * Return the solution digest
     * 
     * @return                      Hash digest
     */
    @Override
    public byte[] getDigest() {
        byte[] outputBytes = new byte[32];
        if (done[0]) {
            outputBytes[0] = (byte)(output[0]);
            outputBytes[1] = (byte)(output[0] >> 8);
            outputBytes[2] = (byte)(output[0] >> 16);
            outputBytes[3] = (byte)(output[0] >> 24);
            outputBytes[4] = (byte)(output[0] >> 32);
            outputBytes[5] = (byte)(output[0] >> 40);
            outputBytes[6] = (byte)(output[0] >> 48);
            outputBytes[7] = (byte)(output[0] >> 56);
            outputBytes[8] = (byte)(output[1]);
            outputBytes[9] = (byte)(output[1] >> 8);
            outputBytes[10] = (byte)(output[1] >> 16);
            outputBytes[11] = (byte)(output[1] >> 24);
            outputBytes[12] = (byte)(output[1] >> 32);
            outputBytes[13] = (byte)(output[1] >> 40);
            outputBytes[14] = (byte)(output[1] >> 48);
            outputBytes[15] = (byte)(output[1] >> 56);
            outputBytes[16] = (byte)(output[2]);
            outputBytes[17] = (byte)(output[2] >> 8);
            outputBytes[18] = (byte)(output[2] >> 16);
            outputBytes[19] = (byte)(output[2] >> 24);
            outputBytes[20] = (byte)(output[2] >> 32);
            outputBytes[21] = (byte)(output[2] >> 40);
            outputBytes[22] = (byte)(output[2] >> 48);
            outputBytes[23] = (byte)(output[2] >> 56);
            outputBytes[24] = (byte)(output[3]);
            outputBytes[25] = (byte)(output[3] >> 8);
            outputBytes[26] = (byte)(output[3] >> 16);
            outputBytes[27] = (byte)(output[3] >> 24);
            outputBytes[28] = (byte)(output[3] >> 32);
            outputBytes[29] = (byte)(output[3] >> 40);
            outputBytes[30] = (byte)(output[3] >> 48);
            outputBytes[31] = (byte)(output[3] >> 56);
        }
        return outputBytes;
    }

    /**
     * GPU kernel code
     */
    @Override
    public void run() {
        //
        // Hash the input if we haven't found a solution yet
        //
        if (!done[0])
            hash();
    }

    /**
     * Compute the hash
     */
    private void hash() {
        int i=0;
        //
        // Set the initial state and modify the nonce by the kernel instance identifier.
        // We will modify the nonce (first 8 bytes of the input data) for each execution
        // instance.
        //
        long state0 = input[0] + (long)getGlobalId();
        long state1 = input[1];
        long state2 = input[2];
        long state3 = input[3];
        long state4 = input[4];
        long state5 = 1;
        long state16 = -9223372036854775808L;
        long state6=0, state7=0, state8=0, state9=0, state10=0, state11=0, state12=0, 
                state13=0, state14=0, state15=0, state17=0, state18=0, state19=0, 
                state20=0, state21=0, state22=0, state23=0, state24=0;
        //
        // Iterate to calculate the digest
        //
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
        // Save the digest if it satisfies the target.  Note that the digest and the target
        // are treated as 32-byte unsigned numbers in little-endian format.
        //
        boolean keepChecking = true;
        boolean isSolved = true;
        long check = 0;
        for (i=3; i>=0&&keepChecking; i--) {
            if (i == 0)
                check = state0;
            else if (i == 1)
                check = state1;
            else if (i == 2)
                check = state2;
            else
                check = state3;
            for (int j=7; j>=0&&keepChecking; j--) {
                int b0 = (int)(check>>(j*8))&0xff;
                int b1 = (int)(target[i*8+j])&0xff;
                if (b0 < b1) 
                    keepChecking = false;
                if (b0 > b1) {
                    isSolved = false;
                    keepChecking = false;
                }
            }
        }
        if (isSolved) {
            done[0] = true;
            output[0] = state0;
            output[1] = state1;
            output[2] = state2;
            output[3] = state3;
            nonce[0] = input[0] + (long)getGlobalId();
        }
    }
}
