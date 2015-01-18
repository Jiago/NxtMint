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
 * SHA-256 hash algorithm for Monetary System currencies
 * 
 * GpuSha256 uses the Aparapi package to calculate hashes using the graphics card
 * GPU.  The Aparapi and OpenCL runtime libraries must be available in either the
 * system path or the Java library path.  This GPU version of HashSha256 is optimized
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
public class GpuSha256 extends GpuFunction {
        
    /** SHA-256 constants */
    @Constant private final int[] k = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
        0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
        0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
        0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

    /** Input data */
    private final byte[] input = new byte[64];
    
    /** Hash target */
    private final byte[] target = new byte[32];
    
    /** Hash digest */
    private final byte[] output = new byte[32];
    
    /** TRUE if the target is met */
    private final boolean[] done = new boolean[1];
    
    /** Nonce used to solve the hash */
    private final long[] nonce = new long[1];

    /**
     * Create the GPU hash function
     */
    public GpuSha256() {
    }
    
    /**
     * Return the GPU intensity scaling factor.  The kernel execution range is calculated
     * as the GPU intensity times the scaling factor.
     * 
     * @return                      Scaling factor
     */
    @Override
    public int getScale() {
        return 1024*1024;
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
        // Copy the input data
        //
        System.arraycopy(inputBytes, 0, input, 0, inputBytes.length);
        //
        // Pad the buffer
        //
        // SHA-256 processes data in 64-byte blocks where the data bit count
        // is stored in the last 8 bytes in big-endian format.  The first pad
        // byte is 0x80 and the remaining pad bytes are 0x00.  Since we have
        // 40 bytes of data, the data bit count is 320 (0x140).
        //
        input[40] = (byte)0x80;
        input[62] = (byte)0x01;
        input[63] = (byte)0x40;
        //
        // Save the hash target
        //
        System.arraycopy(targetBytes, 0, target, 0, targetBytes.length);
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
        return output;
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
        //
        // Initialization
        //
        int A = 0x6a09e667;
        int B = 0xbb67ae85;
        int C = 0x3c6ef372;
        int D = 0xa54ff53a;
        int E = 0x510e527f;
        int F = 0x9b05688c;
        int G = 0x1f83d9ab;
        int H = 0x5be0cd19;
        int r, T, T2;
        int offset = 0;
        int w0=0, w1=0, w2=0, w3=0, w4=0, w5=0, w6=0, w7=0, w8=0, w9=0, w10=0;
        int w11=0, w12=0, w13=0, w14=0, w15=0, w16=0;
        long n = 0;
        //
        // Transform the data
        //
        // We will modify the nonce (first 8 bytes of the input data) for each execution instance
        //
        for (r=0; r<64; r++) {
            if (r < 16) {
                w16 = (input[offset++] << 24 | (input[offset++] & 0xFF) << 16 | 
                            (input[offset++] & 0xFF) << 8 | (input[offset++] & 0xFF));
                if (r == 0)
                    n = (long)w16 << 32;
                if (r == 1) {
                    w16 += getGlobalId();
                    n |= ((long)w16 & 0x00000000ffffffffL);
                }
            } else {
                T =  w14;
                T2 = w1;
                w16 = ((((T >>> 17) | (T << 15)) ^ ((T >>> 19) | (T << 13)) ^ (T >>> 10)) + w9 + 
                        (((T2 >>> 7) | (T2 << 25)) ^ ((T2 >>> 18) | (T2 << 14)) ^ (T2 >>> 3)) + w0);
            }
            T = (H + (((E >>> 6) | (E << 26)) ^ ((E >>> 11) | (E << 21)) ^ ((E >>> 25) | (E << 7))) +
                        ((E & F) ^ (~E & G)) + k[r] + w16);
            T2 = ((((A >>> 2) | (A << 30)) ^ ((A >>> 13) | (A << 19)) ^ ((A >>> 22) | (A << 10))) + 
                        ((A & B) ^ (A & C) ^ (B & C)));
            w0 = w1; w1 = w2; w2 = w3; w3 = w4; w4 = w5; w5 = w6; w6 = w7; w7 = w8; w8 = w9;
            w9 = w10; w10 = w11; w11 = w12; w12 = w13; w13 = w14; w14 = w15; w15 = w16;
            H = G; G = F; F = E;
            E = D + T;
            D = C; C = B; B = A;
            A = T + T2;
        }
        //
        // Finish the digest
        //
        int h0 = A + 0x6a09e667;
        int h1 = B + 0xbb67ae85;
        int h2 = C + 0x3c6ef372;
        int h3 = D + 0xa54ff53a;
        int h4 = E + 0x510e527f;
        int h5 = F + 0x9b05688c;
        int h6 = G + 0x1f83d9ab;
        int h7 = H + 0x5be0cd19;
        //
        // Save the digest if it satisfies the target.  Note that the digest and the target
        // are treated as 32-byte unsigned numbers in little-endian format.
        //
        boolean keepChecking = true;
        boolean isSolved = true;
        int check = 0;
        for (int i=7; i>=0&&keepChecking; i--) {
            if (i == 0)
                check = h0;
            else if (i == 1)
                check = h1;
            else if (i == 2)
                check = h2;
            else if (i == 3)
                check = h3;
            else if (i == 4)
                check = h4;
            else if (i == 5)
                check = h5;
            else if (i == 6)
                check = h6;
            else
                check = h7;
            for (int j=0; j<4&&keepChecking; j++) {
                int b0 = (int)(check>>(j*8))&0xff;
                int b1 = (int)(target[i*4+(3-j)])&0xff;
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
            //
            // Create the output digest bytes
            //
            output[0] = (byte)(h0 >>> 24); 
            output[1] = (byte)(h0 >>> 16);
            output[2] = (byte)(h0 >>> 8);
            output[3] = (byte) h0;
            output[4] = (byte)(h1 >>> 24);
            output[5] = (byte)(h1 >>> 16);
            output[6] = (byte)(h1 >>> 8);
            output[7] = (byte) h1;
            output[8] = (byte)(h2 >>> 24);
            output[9] = (byte)(h2 >>> 16);
            output[10] = (byte)(h2 >>> 8);
            output[11] = (byte) h2;
            output[12] = (byte)(h3 >>> 24);
            output[13] = (byte)(h3 >>> 16); 
            output[14] = (byte)(h3 >>> 8);
            output[15] = (byte) h3;
            output[16] = (byte)(h4 >>> 24);
            output[17] = (byte)(h4 >>> 16); 
            output[18] = (byte)(h4 >>> 8);
            output[19] = (byte) h4;
            output[20] = (byte)(h5 >>> 24);
            output[21] = (byte)(h5 >>> 16); 
            output[22] = (byte)(h5 >>> 8);
            output[23] = (byte) h5;
            output[24] = (byte)(h6 >>> 24);
            output[25] = (byte)(h6 >>> 16); 
            output[26] = (byte)(h6 >>> 8);
            output[27] = (byte) h6;
            output[28] = (byte)(h7 >>> 24);
            output[29] = (byte)(h7 >>> 16); 
            output[30] = (byte)(h7 >>> 8);
            output[31] = (byte) h7;
            //
            // Save the nonce in little-endian format
            //
            nonce[0] = (n>>>56) | ((n>>40)&0x000000000000ff00L) | 
                        ((n>>24)&0x0000000000ff0000L) | ((n>>8)&0x00000000ff000000L) | 
                        ((n<<8)&0x000000ff00000000L) | ((n<<24)&0x0000ff0000000000L) | 
                        ((n<<40)&0x00ff000000000000L) | (n<<56);
        }        
    }
}
