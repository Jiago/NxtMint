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

/**
 * KECCAK25 hash algorithm for Monetary System currencies
 */

/** Addition Java<->C definitions */
typedef uchar      BYTE;
typedef long       LONG;
typedef ulong      ULONG;
typedef uchar      BOOLEAN;

#define TRUE  1
#define FALSE 0

/** 
 * Kernel arguments 
 */
typedef struct This_s {
             ulong * restrict input;        /* Input data */
    __global uchar * restrict target;       /* Hash target */
    __global uint  * restrict done;         /* Solution found indicator */
    __global uchar * restrict solution;     /* Solution nonce */
             int              passId;       /* Pass identifier */
} This;

/**        
 * Keccak25 algorithm constants 
 */
__constant LONG constants[] = {
    1L,                     32898L,               -9223372036854742902L, -9223372034707259392L, 
    32907L,                 2147483649L,          -9223372034707259263L, -9223372036854743031L, 
    138L,                   136L,                  2147516425L,           2147483658L, 
    2147516555L,           -9223372036854775669L, -9223372036854742903L, -9223372036854743037L, 
    -9223372036854743038L, -9223372036854775680L,  32778L,               -9223372034707292150L,
    -9223372034707259263L, -9223372036854742912L,  2147483649L,          -9223372034707259384L,  
    1L
};

/** Helper functions */
#define rotateLeft(x, c) rotate(x, (ulong)(c))

/**
 * Perform a single hash
 */
static void hash(This *this) {
    int i, j;
    ULONG state[25];
    ULONG t[19];
    state[0] = this->input[0] + (ULONG)get_global_id(0) + ((ULONG)this->passId<<32);  
    state[1] = this->input[1];
    state[2] = this->input[2];  
    state[3] = this->input[3];
    state[4] = this->input[4];
    state[5] = 1;   state[6] = 0;   state[7] = 0;   state[8] = 0;   state[9] = 0;
    state[10] = 0;  state[11] = 0;  state[12] = 0;  state[13] = 0;  state[14] = 0;
    state[15] = 0;  state[16] = 0x8000000000000000L;           
    state[17] = 0;  state[18] = 0;  state[19] = 0;  state[20] = 0;  state[21] = 0;
    state[22] = 0;  state[23] = 0;  state[24] = 0;
    for (i=0; i<25;) {
        t[0] = state[0] ^ state[5] ^ state[10] ^ state[15] ^ state[20];
        t[1] = state[2] ^ state[7] ^ state[12] ^ state[17] ^ state[22];
        t[2] = t[0] ^ rotateLeft(t[1], 1);
        t[11] = state[1] ^ t[2];

        t[3] = state[1] ^ state[6] ^ state[11] ^ state[16] ^ state[21];
        t[4] = state[3] ^ state[8] ^ state[13] ^ state[18] ^ state[23];
        t[5] = t[3] ^ rotateLeft(t[4], 1);
        t[12] = state[2] ^ t[5];

        t[6] = state[4] ^ state[9] ^ state[14] ^ state[19] ^ state[24];
        t[7] = rotateLeft(t[3], 1) ^ t[6];
        t[15] = state[6] ^ t[2];
        t[15] = rotateLeft(t[15], 44);
        state[2] = state[12] ^ t[5];
        state[2] = rotateLeft(state[2], 43);
        t[8] = state[0] ^ t[7];
        state[0] = t[8] ^ (~t[15] & state[2]) ^ (ULONG)constants[i++];

        t[9] = rotateLeft(t[6], 1) ^ t[1];
        t[13] = state[3] ^ t[9];
        state[3] = state[18] ^ t[9];
        state[3] = rotateLeft(state[3], 21);
        state[1] = t[15] ^ (~state[2] & state[3]);

        t[10] = rotateLeft(t[0], 1) ^ t[4];
        t[14] = state[4] ^ t[10];
        state[4] = state[24] ^ t[10];
        state[4] = rotateLeft(state[4], 14);
        state[2] ^= ((~state[3]) & state[4]);

        state[3] ^= ((~state[4]) & t[8]);
        state[4] ^= ((~t[8]) & t[15]);
        t[15] = state[5] ^ t[7];
        t[16] = state[7] ^ t[5];
        t[18] = state[9] ^ t[10];
        t[18] = rotateLeft(t[18], 20);
        t[13] = rotateLeft(t[13], 28);
        state[7] = state[10] ^ t[7];
        state[7] = rotateLeft(state[7], 3);
        state[5] = t[13] ^ (~t[18] & state[7]);

        t[17] = state[8] ^ t[9];
        state[8] = state[16] ^ t[2];
        state[8] = rotateLeft(state[8], 45);
        state[6] = t[18] ^ (~state[7] & state[8]);

        state[9] = state[22] ^ t[5];
        state[9] = rotateLeft(state[9], 61);
        state[7] ^= ((~state[8]) & state[9]);

        state[8] ^= ((~state[9]) & t[13]);
        state[9] ^= ((~t[13]) & t[18]);
        t[18] = state[11] ^ t[2];

        t[11] = rotateLeft(t[11], 1);
        t[16] = rotateLeft(t[16], 6);
        state[12] = state[13] ^ t[9];
        state[12] = (state[12] << 25) | (state[12] >> (64-25));
        state[10] = t[11] ^ (~t[16] & state[12]);

        state[13] = state[19] ^ t[10];
        state[13] = rotateLeft(state[13], 8);
        state[11] = t[16] ^ (~state[12] & state[13]);

        t[13] = state[14] ^ t[10];
        state[14] = state[20] ^ t[7];
        state[14] = rotateLeft(state[14], 18);
        state[12] ^= ((~state[13]) & state[14]);

        state[13] ^= ((~state[14]) & t[11]);
        state[14] ^= ((~t[11]) & t[16]);
        t[11] = state[15] ^ t[7];
        t[16] = state[17] ^ t[5];

        t[15] = rotateLeft(t[15], 36);
        t[14] = rotateLeft(t[14], 27);
        state[17] = rotateLeft(t[18], 10);
        state[15] = t[14] ^ (~t[15] & state[17]);

        state[18] = rotateLeft(t[16], 15);
        state[16] = t[15] ^ (~state[17] & state[18]);

        state[19] = state[23] ^ t[9];
        state[19] = rotateLeft(state[19], 56);
        state[17] ^= ((~state[18]) & state[19]);

        state[18] ^= ((~state[19]) & t[14]);
        state[19] ^= ((~t[14]) & t[15]);
        t[18] = state[21] ^ t[2];

        t[12] = rotateLeft(t[12], 62);
        t[17] = rotateLeft(t[17], 55);
        state[22] = rotateLeft(t[13], 39);
        state[20] = t[12] ^ (~t[17] & state[22]);

        state[23] = rotateLeft(t[11], 41);
        state[21] = t[17] ^ (~state[22] & state[23]);

        state[24] = rotateLeft(t[18], 2);
        state[22] ^= ((~state[23]) & state[24]);
        state[23] ^= ((~state[24]) & t[12]);
        state[24] ^= ((~t[12]) & t[17]);
    }    
    //
    // Check if we met the target
    //
    BOOLEAN isSolved = TRUE;
    BOOLEAN keepChecking = TRUE;
    ULONG check;
    for (i=3; i>=0 && keepChecking; i--) {
        if (i == 0)
            check = state[0];
        else if (i == 1)
            check = state[1];
        else if (i == 2)
            check = state[2];
        else
            check = state[3];
        for (j=7; j>=0 && keepChecking; j--) {
            int b0 = (int)(check>>(j*8))&0xff;
            int b1 = (int)(this->target[i*8+j])&0xff;
            if (b0 < b1) {
                keepChecking = FALSE;
            } if (b0 > b1) {
                isSolved = FALSE;
                keepChecking = FALSE;
            }
        }
    }
    //
    // Return the nonce if we have a solution
    //
    if (isSolved!=0 && atomic_cmpxchg(this->done, 0, 1)==0) {
        ULONG nonce = this->input[0] + (ULONG)get_global_id(0) + ((ULONG)this->passId<<32);
        for (i=0; i<8; i++)
            this->solution[i] = (uchar)(nonce>>(i*8));
    }
    return;
}

/**
 * Run the kernel
 */
__kernel void run(__global uchar  *kernelData, 
                           int    passId) {
    int i, offset;
    //
    // Convert the input data to unsigned long integers
    //
    ULONG input[5];
    for (i=0; i<5; i++)
        input[i] = ((ULONG)kernelData[i*8+0]) |
                   ((ULONG)kernelData[i*8+1] << 8) |
                   ((ULONG)kernelData[i*8+2] << 16) |
                   ((ULONG)kernelData[i*8+3] << 24) |
                   ((ULONG)kernelData[i*8+4] << 32) |
                   ((ULONG)kernelData[i*8+5] << 40) |
                   ((ULONG)kernelData[i*8+6] << 48) |
                   ((ULONG)kernelData[i*8+7] << 56);
    //
    // Pass kernel arguments to internal routines
    //
    This thisStruct;
    This* this=&thisStruct;
    this->input = input;
    this->target = kernelData+40;
    this->solution = kernelData+72;
    this->done = (__global uint *)(kernelData+80);
    this->passId = passId;
    //
    // Hash the input data if we haven't found a solution yet
    //
    if (this->done[0]==0) {
        hash(this);
    }
    return;
}
