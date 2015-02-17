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

/*
 * SHA3-256 hash algorithm for Monetary System currencies
 *
 * Implementation of SHA-3 based on KeccakNISTInterface.c available
 * from http://keccak.noekeon.org/
 *
 * The code has been optimized for SHA3-256 with rate=1088 and capacity=512.
 * It will not work for any other values.
 */

/** Addition Java<->C definitions */
typedef unsigned char   BYTE;
typedef int             INT;
typedef unsigned int    UINT;
typedef long            LONG;
typedef unsigned long   ULONG;
typedef unsigned int    BOOLEAN;

#define TRUE  1
#define FALSE 0

/** Keccak round constants */
__constant ULONG KeccakRoundConstants[] = {
    0x0000000000000001UL, 0x0000000000008082UL, 0x800000000000808AUL,
    0x8000000080008000UL, 0x000000000000808BUL, 0x0000000080000001UL,
    0x8000000080008081UL, 0x8000000000008009UL, 0x000000000000008AUL,
    0x0000000000000088UL, 0x0000000080008009UL, 0x000000008000000AUL,
    0x000000008000808BUL, 0x800000000000008BUL, 0x8000000000008089UL,
    0x8000000000008003UL, 0x8000000000008002UL, 0x8000000000000080UL,
    0x000000000000800AUL, 0x800000008000000AUL, 0x8000000080008081UL,
    0x8000000000008080UL, 0x0000000080000001UL, 0x8000000080008008UL
};

/** Keccak RHO offsets */
__constant UINT KeccakRhoOffsets[] = {
    0x00000000U, 0x00000001U, 0x0000003EU, 0x0000001CU, 0x0000001BU,
    0x00000024U, 0x0000002CU, 0x00000006U, 0x00000037U, 0x00000014U,
    0x00000003U, 0x0000000AU, 0x0000002BU, 0x00000019U, 0x00000027U,
    0x00000029U, 0x0000002DU, 0x0000000FU, 0x00000015U, 0x00000008U,
    0x00000012U, 0x00000002U, 0x0000003DU, 0x00000038U, 0x0000000EU
};

/** 
 * Kernel arguments 
 */
typedef struct This_s {
    __global uchar * input;         /* Input data */
    __global ulong * target;        /* Hash target */
    __global ulong * solution;      /* Solution nonce */
             int     passId;        /* Pass identifier */
} This;

/** Hash function */
static void hash(This *this);

/** Helper functions */
#ifdef USE_ROTATE
#define rotateLeft(v, c) rotate(v, (ulong)c)
#else
#define rotateLeft(v, c) (((v)<<(c)) | ((v)>>(64-(c))))
#endif

/*
 * Perform a single SHA3-256 hash.  The Keccak rate is 1088 and the
 * capacity is 512 (yielding a 32-byte digest).
 *
 * @param       this                Kernel data
 */
static void hash(This *this) {
    ULONG state[25];
    //
    // Initialize the state from the input data
    //
    int i;
    for (i=0; i<5; i++)
        state[i] =  ((ULONG)this->input[i*8+0]&0xff)      | (((ULONG)this->input[i*8+1]&0xff)<<8) |
                   (((ULONG)this->input[i*8+2]&0xff)<<16) | (((ULONG)this->input[i*8+3]&0xff)<<24) |
                   (((ULONG)this->input[i*8+4]&0xff)<<32) | (((ULONG)this->input[i*8+5]&0xff)<<40) |
                   (((ULONG)this->input[i*8+6]&0xff)<<48) | (((ULONG)this->input[i*8+7]&0xff)<<56);
    for (i=5; i<25; i++)
        state[i] = 0;
    state[5]  = 0x0000000000000001UL;
    state[16] = 0x8000000000000000UL;
    //
    // The nonce is stored in the first 8 bytes of the input data in little-endian format.
    // We will modify the nonce based on our global and pass identifiers.
    //
    ULONG nonce = state[0] += (ULONG)get_global_id(0) + ((ULONG)this->passId<<32);
    //
    // Perform the Keccak permutations
    //
    for (i=0; i<24; i++) {
        // theta(state))
        ULONG C[5];
        int x, y;
        for (x=0; x<5; x++) {
            C[x] = 0;
            #pragma unroll
            for (y=0; y<5; y++)
                C[x] ^= state[x+5*y];
        }
        for (x=0; x<5; x++) {
            ULONG dX = rotateLeft(C[(x+1)%5], 1) ^ C[(x+4)%5];
            #pragma unroll
            for (y=0; y<5; y++)
                state[x+5*y] ^= dX;
        }
        // rho(state)
        for (x=0; x<5; x++) {
            #pragma unroll
            for (y=0; y<5; y++) {
                int index = x+5*y;
                state[index] = (KeccakRhoOffsets[index]!=0 ?
                    rotateLeft(state[index], KeccakRhoOffsets[index]) : state[index]);
            }
        }
        // pi(state)
        ULONG tempA[25];
        #pragma unroll
        for (x=0; x<25; x++)
            tempA[x] = state[x];
        for (x=0; x<5; x++) {
            #pragma unroll
            for (y=0; y<5; y++)
                state[y+5 * ((2*x + 3*y) % 5)] = tempA[x+5*y];
        }
        // chi(state)
        ULONG chiC[5];
        for (y=0; y<5; y++) {
            #pragma unroll
            for (x=0; x<5; x++)
                chiC[x] = state[x+5*y] ^ ((~state[(((x+1)%5)+5*y)]) & state[(((x+2)%5)+5*y)]);
            #pragma unroll
            for (x=0; x<5; x++)
                state[x+5*y] = chiC[x];
        }
        // iota(state, i)
        state[0] ^= KeccakRoundConstants[i];
    }
    //
    // Check if we met the target
    //
    BOOLEAN isSolved = TRUE;
    BOOLEAN keepChecking = TRUE;
    for (i=3; i>=0 && keepChecking; i--) {
        if (state[i] < this->target[i]) {
            keepChecking = FALSE;
        } else if (state[i] > this->target[i]) {
            isSolved = FALSE;
            keepChecking = FALSE;
        }
    }
    //
    // Return the nonce if we met the target
    //
    if (isSolved)
        *this->solution = nonce;
}

/**
 * Run the kernel
 */
__kernel void run(__global uchar  *kernelData, 
                           int    passId) {
    //
    // Pass kernel arguments to internal routines
    //
    This thisStruct;
    This* this=&thisStruct;
    this->input = kernelData+0;
    this->target = (__global ulong *)(kernelData+40);
    this->solution = (__global ulong *)(kernelData+72);
    this->passId = passId;
    //
    // Hash the input data
    //
    hash(this);
}
