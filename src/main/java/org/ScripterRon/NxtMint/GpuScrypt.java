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

import com.amd.aparapi.Range;

/**
 * SCRYPT hash algorithm for Monetary System currencies
 * 
 * GpuScrypt uses the Aparapi package to calculate hashes using the graphics card
 * GPU.  The Aparapi and OpenCL runtime libraries must be available in either the
 * system path or the Java library path.  This GPU version of HashScrypt is optimized
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
 *   o Primitive data types defined within a function must be assigned a value at the time of definition.
 *   o Only data belonging to the enclosing Java class can be copied to/from GPU memory.
 *   o Primitives and objects can be read from kernel code but only objects can be
 *     written by kernel code.
 *   o Aparapi normally defines kernel groups based on the capabilities of the graphics card.
 *     The Range object can be used to define an explicit grouping.
 *   o Untagged data is shared by all instances of the kernel.
 *   o Constant data is not fetched upon completion of kernel execution.  Constant data is
 *     indicated by prefixing the data definition with @Constant.
 *   o Local data is shared by all instances in the same kernel group.  Local data is indicated
 *     by prefixing the data definition with @Local
 *   o Private data is not shared and each kernel instance has its own copy of the data.  Private
 *     data is indicated by prefixing the data definition with @PrivateMemorySpace(nnn) where 'nnn'
 *     is the array dimension.  Private data cannot be passed as a function parameter since it is
 *     in a separate address space and this information is not passed on the function call.
 */
public class GpuScrypt extends GpuFunction {

    /** SHA-256 constants */
    private final int K[] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    /** SHA-256 data areas */
    private final int[] DH;
    private final int[] DX;
    private final int[] xOff;
    private final byte[] xBuff;
    private final int[] xBuffOff;
    private final long[] xByteCount;
    private final byte[] digestBuf;
    private final byte[] digest;

    /** Digest private save areas */
    private final int[] sDH;
    private final int[] sDX;
    private final int[] sxOff;
    private final byte[] sxBuff;
    private final int[] sxBuffOff;
    private final long[] sxByteCount;

    /** IPad digest private save areas */
    private final int[] ipDH;
    private final int[] ipDX;
    private final int[] ipxOff;
    private final byte[] ipxBuff;
    private final int[] ipxBuffOff;
    private final long[] ipxByteCount;

    /** OPad digest private save areas */
    private final int[] opDH;
    private final int[] opDX;
    private final int[] opxOff;
    private final byte[] opxBuff;
    private final int[] opxBuffOff;
    private final long[] opxByteCount;

    /** HMacSha256 private data areas */
    private final byte[] inputPad;
    private final byte[] outputBuf;
    private final byte[] macDigest;

    /** SCRYPT private data areas */
    private final byte[] H;
    private final byte[] B;
    private final int[] X;
    private final int[] V;

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
    
    /** Local size */
    private final int[] localSize = new int[1];
    
    /** Local size multiplier */
    private final int localScale = 4;
    
    /** Global size */
    private final int globalSize;

    /**
     * Create the GPU hash function
     */
    public GpuScrypt() {
        //
        // Get the group size for this GPU
        //
        Range range = Range.create(1024);
        localSize[0] = range.getLocalSize(0);
        globalSize = localSize[0]*localScale;
        log.debug(String.format("SCRYPT GPU local size %d, group size %d", localSize[0], globalSize));
        //
        // Allocate storage
        //
        DH = new int[globalSize*8];
        DX = new int[globalSize*64];
        xOff = new int[globalSize*1];
        xBuff = new byte[globalSize*4];
        xBuffOff = new int[globalSize*1];
        xByteCount = new long[globalSize*1];
        digestBuf = new byte[globalSize*132];
        digest = new byte[globalSize*32];

        /** Digest private save areas */
        sDH = new int[globalSize*8];
        sDX = new int[globalSize*64];
        sxOff = new int[globalSize*1];
        sxBuff = new byte[globalSize*4];
        sxBuffOff = new int[globalSize*1];
        sxByteCount = new long[globalSize*1];

        /** IPad digest private save areas */
        ipDH = new int[globalSize*8];
        ipDX = new int[globalSize*64];
        ipxOff = new int[globalSize*1];
        ipxBuff = new byte[globalSize*4];
        ipxBuffOff = new int[globalSize*1];
        ipxByteCount = new long[globalSize*1];

        /** OPad digest private save areas */
        opDH = new int[globalSize*8];
        opDX = new int[globalSize*64];
        opxOff = new int[globalSize*1];
        opxBuff = new byte[globalSize*4];
        opxBuffOff = new int[globalSize*1];
        opxByteCount = new long[globalSize*1];

        /** HMacSha256 private data areas */
        inputPad = new byte[globalSize*64];
        outputBuf = new byte[globalSize*96];
        macDigest = new byte[globalSize*32];

        /** SCRYPT private data areas */
        H = new byte[globalSize*32];
        B = new byte[globalSize*132];
        X = new int[globalSize*32];
        V = new int[globalSize*32*1024];
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
     * Return the execution range for the specified execution count
     * 
     * @param       count           Execution count
     * @return                      Execution range
     */
    @Override
    public Range getRange(int count) {
        Range range;
        if (count <= globalSize)
            range = Range.create((count/localSize[0])*localSize[0], localSize[0]);
        else
            range = Range.create(globalSize, localSize[0]);
        return range;
    }
    
    /**
     * Put data to the GPU before kernel execution.  All local data must be initialized properly
     * before calling this method.  This function is normally not needed since Aparapi
     * will handle data transfer automatically.  But explicit control is needed
     * in cases where the kernel program uses large amounts of temporary data which does not
     * need to be transferred between CPU and GPU.
     */
    @Override
    public void putKernelData() {
        setExplicit(true);
        put(localSize);
        put(input);
        put(target);
        put(output);
        put(nonce);
        put(done);
    }
    
    /**
     * Get data from the GPU after kernel execution
     */
    @Override
    public void getKernelData() {
        get(output);
        get(nonce);
        get(done);
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
     * Return the global memory base index
     * 
     * @return                      Global memory base index
     */
    private int getBaseIndex(int dim) {
        return (getGlobalId(dim)/localSize[0])*localSize[0]+getLocalId(dim);
    }

    /**
     * Hash the input bytes
     */
    private void hash() {
        int i=0, j=0, k=0;
        int id = getBaseIndex(0);
        int base = id*32;
        int bBase = id*132;
        int vBase = id*32*1024;
        //
        // Modify the nonce for this kernel instance
        //
        long n = ((long)input[0]&0xff) | (((long)input[1]&0xff)<<8) | 
                            (((long)input[2]&0xff)<<16) | (((long)input[3]&0xff)<<24) |
                            (((long)input[4]&0xff)<<32) | (((long)input[5]&0xff)<<40) |
                            (((long)input[6]&0xff)<<48) | (((long)input[7]&0xff)<<56);
        n += (long)(getGlobalId() + getPassId()*65536);
        B[bBase+0] = (byte)n;
        B[bBase+1] = (byte)(n>>8);
        B[bBase+2] = (byte)(n>>16);
        B[bBase+3] = (byte)(n>>24);
        B[bBase+4] = (byte)(n>>32);
        B[bBase+5] = (byte)(n>>40);
        B[bBase+6] = (byte)(n>>48);
        B[bBase+7] = (byte)(n>>56);
        //
        // Initialize B from the input data
        //
        for (i=8; i<40; i++)
            B[bBase+i] = input[i];
        for (i=40; i<132; i++)
            B[bBase+i] = (byte)0;
        //
        // Initialize HmacSha256
        //
        initMac();
        //
        // Hash the data
        //
        for (i=0; i<4; i++) {
            B[bBase+43] = (byte)(i + 1);
            updateMac(B, bBase, 44);
            finishMac();
            for (j=0; j<32; j++)
                H[base+j] = macDigest[base+j];
            for (j=0; j<8; j++)
                X[base+i*8+j] = ((int)H[base+j*4+0]&0xff) | (((int)H[base+j*4+1]&0xff)<<8) | 
                                (((int)H[base+j*4+2]&0xff)<<16) | (((int)H[base+j*4+3]&0xff)<< 24);
        }
        for (i=0; i<1024; i++) {
            for (j=0; j<32; j++)
                V[vBase+i*32+j] = X[base+j];
            xorSalsa8(0, 16);
            xorSalsa8(16, 0);
        }
        for (i=0; i<1024; i++) {
            k = (X[base+16]&1023)*32;
            for (j=0; j<32; j++)
                X[base+j] ^= V[vBase+k+j];
            xorSalsa8(0, 16);
            xorSalsa8(16, 0);
        }
        for (i=0; i<32; i++) {
            B[bBase+i*4+0] = (byte) (X[base+i]);
            B[bBase+i*4+1] = (byte) (X[base+i] >> 8);
            B[bBase+i*4+2] = (byte) (X[base+i] >> 16);
            B[bBase+i*4+3] = (byte) (X[base+i] >> 24);
        }
        B[bBase+128+3] = 1;
        updateMac(B, bBase, 132);
        finishMac();
        //
        // Save the digest if it satisfies the target.  Note that the digest and the target
        // are treated as 32-byte unsigned numbers in little-endian format.
        //
        boolean keepChecking = true;
        boolean isSolved = true;
        for (i=31; i>=0&&keepChecking; i--) {
            int b0 = (int)macDigest[base+i]&0xff;
            int b1 = (int)target[i]&0xff;
            if (b0 < b1)
                keepChecking = false;
            if (b0 > b1) {
                keepChecking = false;
                isSolved = false;
            }
        }
        if (isSolved) {
            done[0] = true;
            for (i=0; i<32; i++)
                output[i] = macDigest[base+i];
            nonce[0] = n;
        }        
    }

    /**
     * Scrypt permutation
     * 
     * @param       idi     First block offset
     * @param       ixi     Second block offset
     */
    private void xorSalsa8(int idi, int ixi) {
        int base = getBaseIndex(0)*32;
        int di = idi+base;
        int xi = ixi+base;
        X[di+0] ^= X[xi+0];
        X[di+1] ^= X[xi+1];
        X[di+2] ^= X[xi+2];
        X[di+3] ^= X[xi+3];
        X[di+4] ^= X[xi+4];
        X[di+5] ^= X[xi+5];
        X[di+6] ^= X[xi+6];
        X[di+7] ^= X[xi+7];
        X[di+8] ^= X[xi+8];
        X[di+9] ^= X[xi+9];
        X[di+10] ^= X[xi+10];
        X[di+11] ^= X[xi+11];
        X[di+12] ^= X[xi+12];
        X[di+13] ^= X[xi+13];
        X[di+14] ^= X[xi+14];
        X[di+15] ^= X[xi+15];
        int x00 = X[di+0];
        int x01 = X[di+1];
        int x02 = X[di+2];
        int x03 = X[di+3];
        int x04 = X[di+4];
        int x05 = X[di+5];
        int x06 = X[di+6];
        int x07 = X[di+7];
        int x08 = X[di+8];
        int x09 = X[di+9];
        int x10 = X[di+10];
        int x11 = X[di+11];
        int x12 = X[di+12];
        int x13 = X[di+13];
        int x14 = X[di+14];
        int x15 = X[di+15];
        for (int i=0; i<8; i+=2) {
            int value = x00+x12;
            x04 ^= (value<<7) | (value>>>(32-7));
            value = x04+x00;
            x08 ^= (value<<9) | (value>>>(32-9));
            value = x08+x04;
            x12 ^= (value<<13) | (value>>>(32-13));
            value = x12+x08;
            x00 ^= (value<<18) | (value>>>(32-18));
            value = x05+x01;
            x09 ^= (value<<7) | (value>>>(32-7));
            value = x09+x05;
            x13 ^= (value<<9) | (value>>>(32-9));
            value = x13+x09;
            x01 ^= (value<<13) | (value>>>(32-13));
            value = x01+x13;
            x05 ^= (value<<18) | (value>>>(32-18));
            value = x10+x06;
            x14 ^= (value<<7) | (value>>>(32-7));
            value = x14+x10;
            x02 ^= (value<<9) | (value>>>(32-9));
            value = x02+x14;
            x06 ^= (value<<13) | (value>>>(32-13));
            value = x06+x02;
            x10 ^= (value<<18) | (value>>>(32-18));
            value = x15+x11;
            x03 ^= (value<<7) | (value>>>(32-7));
            value = x03+x15;
            x07 ^= (value<<9) | (value>>>(32-9));
            value = x07+x03;
            x11 ^= (value<<13) | (value>>>(32-13));
            value = x11+x07;
            x15 ^= (value<<18) | (value>>>(32-18));
            value = x00+x03;
            x01 ^= (value<<7) | (value>>>(32-7));
            value = x01+x00;
            x02 ^= (value<<9) | (value>>>(32-9));
            value = x02+x01;
            x03 ^= (value<<13) | (value>>>(32-13));
            value = x03+x02;
            x00 ^= (value<<18) | (value>>>(32-18));
            value = x05+x04;
            x06 ^= (value<<7) | (value>>>(32-7));
            value = x06+x05;
            x07 ^= (value<<9) | (value>>>(32-9));
            value = x07+x06;
            x04 ^= (value<<13) | (value>>>(32-13));
            value = x04+x07;
            x05 ^= (value<<18) | (value>>>(32-18));
            value = x10+x09;
            x11 ^= (value<<7) | (value>>>(32-7));
            value = x11+x10;
            x08 ^= (value<<9) | (value>>>(32-9));
            value = x08+x11;
            x09 ^= (value<<13) | (value>>>(32-13));
            value = x09+x08;
            x10 ^= (value<<18) | (value>>>(32-18));
            value = x15+x14;
            x12 ^= (value<<7) | (value>>>(32-7));
            value = x12+x15;
            x13 ^= (value<<9) | (value>>>(32-9));
            value = x13+x12;
            x14 ^= (value<<13) | (value>>>(32-13));
            value = x14+x13;
            x15 ^= (value<<18) | (value>>>(32-18));
        }
        X[di+0] += x00;
        X[di+1] += x01;
        X[di+2] += x02;
        X[di+3] += x03;
        X[di+4] += x04;
        X[di+5] += x05;
        X[di+6] += x06;
        X[di+7] += x07;
        X[di+8] += x08;
        X[di+9] += x09;
        X[di+10] += x10;
        X[di+11] += x11;
        X[di+12] += x12;
        X[di+13] += x13;
        X[di+14] += x14;
        X[di+15] += x15;
    }

    /**
     * Initialize the MAC
     */
    private void initMac() {
        int id = getBaseIndex(0);
        int bBase = id*132;
        int iBase = id*64;
        int oBase = id*96;
        //
        // Reset the SHA-256 digest
        //
        resetDigest();
        //
        // Initialize the input pad
        //
        for (int i=0; i<40; i++)
            inputPad[iBase+i] = B[bBase+i];
        for (int i=40; i<64; i++)
            inputPad[iBase+i] = (byte)0;
        //
        // Initialize the output buffer
        //
        for (int i=0; i<64; i++) {
            outputBuf[oBase+i] = inputPad[iBase+i];
            inputPad[iBase+i] ^= (byte)0x36;
            outputBuf[oBase+i] ^= (byte)0x5c;
        }
        //
        // Save the output digest to improve HMac reset performance
        //
        saveDigest();
        updateDigest(outputBuf, oBase, 64);
        saveOPadDigest();
        restoreDigest();
        //
        // Save the input digest to improve HMac reset performance
        //
        updateDigest(inputPad, iBase, 64);
        saveIPadDigest();
    }

    /**
     * Update the MAC
     * 
     * @param       buffer          Data buffer
     * @param       offset          Offset to the start of the data
     * @param       length          Data length
     */
    private void updateMac(byte[] buffer, int offset, int length) {
        updateDigest(buffer, offset, length);
    }

    /**
     * Finish the MAC
     */
    private void finishMac() {
        int id = getBaseIndex(0);
        int base = id*32;
        int oBase = id*96;
        //
        // Finish the current digest
        //
        finishDigest();
        for (int i=0; i<32; i++)
            outputBuf[oBase+i+64] = digest[base+i];
        //
        // Hash the digest output
        //
        restoreOPadDigest();
        updateDigest(outputBuf, oBase+64, 32);
        //
        // Finish the MAC digest
        //
        finishDigest();
        for (int i=0; i<32; i++)
            macDigest[base+i] = digest[base+i];
        //
        // Reset the output buffer
        //
        for (int i=64; i<96; i++)
            outputBuf[oBase+i] = (byte)0;
        //
        // Reset the MAC digest
        //
        restoreIPadDigest();
    }

    /**
     * Reset the SHA-256 digest
     */
    private void resetDigest() {
        int id = getBaseIndex(0);
        int hBase = id*8;
        int xBase = id*64;
        DH[hBase+0] = 0x6a09e667;
        DH[hBase+1] = 0xbb67ae85;
        DH[hBase+2] = 0x3c6ef372;
        DH[hBase+3] = 0xa54ff53a;
        DH[hBase+4] = 0x510e527f;
        DH[hBase+5] = 0x9b05688c;
        DH[hBase+6] = 0x1f83d9ab;
        DH[hBase+7] = 0x5be0cd19;
        xOff[id] = 0;
        for (int i=0; i<64; i++)
            DX[xBase+i] = 0;
        xBuffOff[id] = 0;
        xByteCount[id] = 0;
    }

    /**
     * Save the current digest
     */
    private void saveDigest() {
        int id = getBaseIndex(0);
        int bBase = id*4;
        int hBase = id*8;
        int xBase = id*64;
        for (int i=0; i<8; i++)
            sDH[hBase+i] = DH[hBase+i];
        for (int i=0; i<64; i++)
            sDX[xBase+i] = DX[xBase+i];
        for (int i=0; i<4; i++)
            sxBuff[bBase+i] = xBuff[bBase+i];
        sxOff[id] = xOff[id];
        sxBuffOff[id] = xBuffOff[id];
        sxByteCount[id] = xByteCount[id];
    }

    /**
     * Restore the current digest
     */
    private void restoreDigest() {
        int id = getBaseIndex(0);
        int bBase = id*4;
        int hBase = id*8;
        int xBase = id*64;
        for (int i=0; i<8; i++)
            DH[hBase+i] = sDH[hBase+i];
        for (int i=0; i<64; i++)
            DX[xBase+i] = sDX[xBase+i];
        for (int i=0; i<4; i++)
            xBuff[bBase+i] = sxBuff[bBase+i];
        xOff[id] = sxOff[id];
        xBuffOff[id] = sxBuffOff[id];
        xByteCount[id] = sxByteCount[id];
    }

    /**
     * Save the current iPad digest
     */
    private void saveIPadDigest() {
        int id = getBaseIndex(0);
        int bBase = id*4;
        int hBase = id*8;
        int xBase = id*64;
        for (int i=0; i<8; i++)
            ipDH[hBase+i] = DH[hBase+i];
        for (int i=0; i<64; i++)
            ipDX[xBase+i] = DX[xBase+i];
        for (int i=0; i<4; i++)
            ipxBuff[bBase+i] = xBuff[bBase+i];
        ipxOff[id] = xOff[id];
        ipxBuffOff[id] = xBuffOff[id];
        ipxByteCount[id] = xByteCount[id];
    }

    /**
     * Restore the current iPad digest
     */
    private void restoreIPadDigest() {
        int id = getBaseIndex(0);
        int bBase = id*4;
        int hBase = id*8;
        int xBase = id*64;
        for (int i=0; i<8; i++)
            DH[hBase+i] = ipDH[hBase+i];
        for (int i=0; i<64; i++)
            DX[xBase+i] = ipDX[xBase+i];
        for (int i=0; i<4; i++)
            xBuff[bBase+i] = ipxBuff[bBase+i];
        xOff[id] = ipxOff[id];
        xBuffOff[id] = ipxBuffOff[id];
        xByteCount[id] = ipxByteCount[id];
    }

    /**
     * Save the current oPad digest
     */
    private void saveOPadDigest() {
        int id = getBaseIndex(0);
        int bBase = id*4;
        int hBase = id*8;
        int xBase = id*64;
        for (int i=0; i<8; i++)
            opDH[hBase+i] = DH[hBase+i];
        for (int i=0; i<64; i++)
            opDX[xBase+i] = DX[xBase+i];
        for (int i=0; i<4; i++)
            opxBuff[bBase+i] = xBuff[bBase+i];
        opxOff[id] = xOff[id];
        opxBuffOff[id] = xBuffOff[id];
        opxByteCount[id] = xByteCount[id];
    }

    /**
     * Restore the current oPad digest
     */
    private void restoreOPadDigest() {
        int id = getBaseIndex(0);
        int bBase = id*4;
        int hBase = id*8;
        int xBase = id*64;
        for (int i=0; i<8; i++)
            DH[hBase+i] = opDH[hBase+i];
        for (int i=0; i<64; i++)
            DX[xBase+i] = opDX[xBase+i];
        for (int i=0; i<4; i++)
            xBuff[bBase+i] = opxBuff[bBase+i];
        xOff[id] = opxOff[id];
        xBuffOff[id] = opxBuffOff[id];
        xByteCount[id] = opxByteCount[id];
    }

    /**
     * Update the digest
     * 
     * @param       in              Data
     */
    private void updateDigestByte(byte in) {
        int id = getBaseIndex(0);
        int bBase = id*4;
        int xBase = id*64;
        xBuff[bBase+xBuffOff[id]] = in;
        xBuffOff[id]++;
        if (xBuffOff[id] == 4) {
            DX[xBase+xOff[id]] = (((int)xBuff[bBase+0]&0xff) << 24) |
                                (((int)xBuff[bBase+1]&0xff) << 16) |
                                (((int)xBuff[bBase+2]&0xff) << 8) |
                                ((int)xBuff[bBase+3]&0xff);
            xOff[id]++;
            if (xOff[id] == 16)
                processBlock();
            xBuffOff[id] = 0;
        }
        xByteCount[id]++;
    }

    /**
     * Update the digest
     * 
     * Note: OpenCL fails if an array in the private address space is passed as
     *       a parameter.  So the input data must be copied to digestBuf before
     *       calling updateDigest.
     * 
     * @param       buffer          Data buffer
     * @param       inOff           Buffer offset to start of data
     * @param       inLen           Data length
     */
    private void updateDigest(byte[] buffer, int inOff, int inLen) {
        int id = getBaseIndex(0);
        int len = inLen;
        int offset = inOff;
        //
        // Fill the current word
        //
        while (xBuffOff[id]!=0 && len>0) {
            updateDigestByte(buffer[offset]);
            offset++;
            len--;
        }
        //
        // Process whole words.
        //
        while (len>4) {
            processWord(buffer, offset);
            offset += 4;
            len -= 4;
            xByteCount[id] += 4;
        }
        //
        // Load in the remainder.
        //
        while (len > 0) {
            updateDigestByte(buffer[offset]);
            offset++;
            len--;
        }
    }

    /**
     * Finish the digest
     */
    private void finishDigest() {
        int id = getBaseIndex(0);
        int base = id*32;
        int hBase = id*8;
        int xBase = id*64;
        long bitLength = (xByteCount[id] << 3);
        //
        // Add the pad bytes
        //
        // The first byte is 0x80 followed by 0x00.  The last two bytes
        // contain the data length in bits
        //
        updateDigestByte((byte)128);
        while (xBuffOff[id] != 0)
            updateDigestByte((byte)0);
        if (xOff[id] > 14)
            processBlock();
        DX[xBase+14] = (int)(bitLength >>> 32);
        DX[xBase+15] = (int)(bitLength & 0xffffffff);
        //
        // Process the last block
        //
        processBlock();
        //
        // Convert the digest to bytes in big-endian format
        //
        for (int i=0; i<8; i++) {
            digest[base+i*4] = (byte)(DH[hBase+i]>>>24);
            digest[base+i*4+1] = (byte)(DH[hBase+i]>>>16);
            digest[base+i*4+2] = (byte)(DH[hBase+i]>>>8);
            digest[base+i*4+3] = (byte)(DH[hBase+i]);
        }
        //
        // Reset the digest
        //
        resetDigest();
    }

    /**
     * Process a word (4 bytes)
     * 
     * Note: OpenCL fails if an array in the private address space is passed as
     *       a parameter.  So the input data must be copied to digestBuf before
     *       calling processWord.
     * 
     * @param       buffer          Data buffer
     * @param       inOff           Buffer offset to start of word 
     */
    private void processWord(byte[] buffer, int inOff) {
        int id = getBaseIndex(0);
        int xBase = id*64;
        DX[xBase+xOff[id]] = (((int)buffer[inOff]&0xff) << 24) |
                      (((int)buffer[inOff+1]&0xff) << 16) |
                      (((int)buffer[inOff+2]&0xff) << 8) |
                      ((int)buffer[inOff+3]&0xff);
        xOff[id]++;
        if (xOff[id] == 16)
            processBlock();
    }

    /**
     * Process a 16-word block
     */
    private void processBlock() {
        int id = getBaseIndex(0);
        int hBase = id*8;
        int xBase = id*64;
        for (int t=16; t<64; t++) {
            int x = DX[xBase+t-15];
            int r0 = ((x >>> 7) | (x << 25)) ^ ((x >>> 18) | (x << 14)) ^ (x >>> 3);
            x = DX[xBase+t-2];
            int r1 = ((x >>> 17) | (x << 15)) ^ ((x >>> 19) | (x << 13)) ^ (x >>> 10);
            DX[xBase+t] = r1 + DX[xBase+t-7] + r0 + DX[xBase+t-16];
        }
        int a = DH[hBase+0], b = DH[hBase+1], c = DH[hBase+2], d = DH[hBase+3], 
                    e = DH[hBase+4], f = DH[hBase+5], g = DH[hBase+6], h = DH[hBase+7];
        int t = 0;     
        for(int i=0; i<8; i ++) {
            h += Sum1(e) + Ch(e, f, g) + K[t] + DX[xBase+t];
            d += h;
            h += Sum0(a) + Maj(a, b, c);
            ++t;

            g += Sum1(d) + Ch(d, e, f) + K[t] + DX[xBase+t];
            c += g;
            g += Sum0(h) + Maj(h, a, b);
            ++t;

            f += Sum1(c) + Ch(c, d, e) + K[t] + DX[xBase+t];
            b += f;
            f += Sum0(g) + Maj(g, h, a);
            ++t;

            e += Sum1(b) + Ch(b, c, d) + K[t] + DX[xBase+t];
            a += e;
            e += Sum0(f) + Maj(f, g, h);
            ++t;

            d += Sum1(a) + Ch(a, b, c) + K[t] + DX[xBase+t];
            h += d;
            d += Sum0(e) + Maj(e, f, g);
            ++t;

            c += Sum1(h) + Ch(h, a, b) + K[t] + DX[xBase+t];
            g += c;
            c += Sum0(d) + Maj(d, e, f);
            ++t;

            b += Sum1(g) + Ch(g, h, a) + K[t] + DX[xBase+t];
            f += b;
            b += Sum0(c) + Maj(c, d, e);
            ++t;

            a += Sum1(f) + Ch(f, g, h) + K[t] + DX[xBase+t];
            e += a;
            a += Sum0(b) + Maj(b, c, d);
            ++t;
        }
        DH[hBase+0] += a;
        DH[hBase+1] += b;
        DH[hBase+2] += c;
        DH[hBase+3] += d;
        DH[hBase+4] += e;
        DH[hBase+5] += f;
        DH[hBase+6] += g;
        DH[hBase+7] += h;
        xOff[id] = 0;
        for (int i=0; i<16; i++)
            DX[xBase+i] = 0;
    }

    private int Ch(int x, int y, int z) {
        return (x & y) ^ ((~x) & z);
    }

    private int Maj(int x, int y, int    z) {
        return (x & y) ^ (x & z) ^ (y & z);
    }

    private int Sum0(int x) {
        return ((x >>> 2) | (x << 30)) ^ ((x >>> 13) | (x << 19)) ^ ((x >>> 22) | (x << 10));
    }

    private int Sum1(int x) {
        return ((x >>> 6) | (x << 26)) ^ ((x >>> 11) | (x << 21)) ^ ((x >>> 25) | (x << 7));
    }
}