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
            // theta(state)
        ULONG C[5], dX;
        C[0] = state[0] ^ state[5] ^ state[10] ^ state[15] ^ state[20];
        C[1] = state[1] ^ state[6] ^ state[11] ^ state[16] ^ state[21];
        C[2] = state[2] ^ state[7] ^ state[12] ^ state[17] ^ state[22];
        C[3] = state[3] ^ state[8] ^ state[13] ^ state[18] ^ state[23];
        C[4] = state[4] ^ state[9] ^ state[14] ^ state[19] ^ state[24];
        dX = rotateLeft(C[1], 1) ^ C[4];
        state[0] ^= dX;  state[5] ^= dX;  state[10] ^= dX;  state[15] ^= dX;  state[20] ^= dX;
        dX = rotateLeft(C[2], 1) ^ C[0];
        state[1] ^= dX;  state[6] ^= dX;  state[11] ^= dX;  state[16] ^= dX;  state[21] ^= dX;
        dX = rotateLeft(C[3], 1) ^ C[1];
        state[2] ^= dX;  state[7] ^= dX;  state[12] ^= dX;  state[17] ^= dX;  state[22] ^= dX;
        dX = rotateLeft(C[4], 1) ^ C[2];
        state[3] ^= dX;  state[8] ^= dX;  state[13] ^= dX;  state[18] ^= dX;  state[23] ^= dX;
        dX = rotateLeft(C[0], 1) ^ C[3];
        state[4] ^= dX;  state[9] ^= dX;  state[14] ^= dX;  state[19] ^= dX;  state[24] ^= dX;
            // rho(state)
        //state[0] = rotateLeft(state[0], 0);
        state[5] = rotateLeft(state[5], 36);
        state[10] = rotateLeft(state[10], 3);
        state[15] = rotateLeft(state[15], 41);
        state[20] = rotateLeft(state[20], 18);
        
        state[1] = rotateLeft(state[1], 1);
        state[6] = rotateLeft(state[6], 44);
        state[11] = rotateLeft(state[11], 10);
        state[16] = rotateLeft(state[16], 45);
        state[21] = rotateLeft(state[21], 2);
        
        state[2] = rotateLeft(state[2], 62);
        state[7] = rotateLeft(state[7], 6);
        state[12] = rotateLeft(state[12], 43);
        state[17] = rotateLeft(state[17], 15);
        state[22] = rotateLeft(state[22], 61);
        
        state[3] = rotateLeft(state[3], 28);
        state[8] = rotateLeft(state[8], 55);
        state[13] = rotateLeft(state[13], 25);
        state[18] = rotateLeft(state[18], 21);
        state[23] = rotateLeft(state[23], 56);
        
        state[4] = rotateLeft(state[4], 27);
        state[9] = rotateLeft(state[9], 20);
        state[14] = rotateLeft(state[14], 39);
        state[19] = rotateLeft(state[19], 8);
        state[24] = rotateLeft(state[24], 14);
            // pi(state)
        ULONG tempA[25];
        tempA[0]  = state[0];  tempA[1]  = state[1];  tempA[2]  = state[2];  tempA[3]  = state[3];  tempA[4]  = state[4];
        tempA[5]  = state[5];  tempA[6]  = state[6];  tempA[7]  = state[7];  tempA[8]  = state[8];  tempA[9]  = state[9];
        tempA[10] = state[10]; tempA[11] = state[11]; tempA[12] = state[12]; tempA[13] = state[13]; tempA[14] = state[14];
        tempA[15] = state[15]; tempA[16] = state[16]; tempA[17] = state[17]; tempA[18] = state[18]; tempA[19] = state[19];
        tempA[20] = state[20]; tempA[21] = state[21]; tempA[22] = state[22]; tempA[23] = state[23]; tempA[24] = state[24];
        
        state[0]  = tempA[0]; state[16] = tempA[5]; state[7]  = tempA[10]; state[23] = tempA[15]; state[14] = tempA[20];
        state[10] = tempA[1]; state[1]  = tempA[6]; state[17] = tempA[11]; state[8]  = tempA[16]; state[24] = tempA[21];
        state[20] = tempA[2]; state[11] = tempA[7]; state[2]  = tempA[12]; state[18] = tempA[17]; state[9]  = tempA[22];
        state[5]  = tempA[3]; state[21] = tempA[8]; state[12] = tempA[13]; state[3]  = tempA[18]; state[19] = tempA[23];
        state[15] = tempA[4]; state[6]  = tempA[9]; state[22] = tempA[14]; state[13] = tempA[19]; state[4]  = tempA[24];
            // chi(state)
        ULONG chiC[5];
        int y;
        for (y=0; y<5; y++) {
            chiC[0] = state[0+5*y] ^ ((~state[1+5*y]) & state[2+5*y]);
            chiC[1] = state[1+5*y] ^ ((~state[2+5*y]) & state[3+5*y]);
            chiC[2] = state[2+5*y] ^ ((~state[3+5*y]) & state[4+5*y]);
            chiC[3] = state[3+5*y] ^ ((~state[4+5*y]) & state[0+5*y]);
            chiC[4] = state[4+5*y] ^ ((~state[0+5*y]) & state[1+5*y]);
            state[0+5*y] = chiC[0];  state[1+5*y] = chiC[1];  state[2+5*y] = chiC[2];
            state[3+5*y] = chiC[3];  state[4+5*y] = chiC[4];
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
