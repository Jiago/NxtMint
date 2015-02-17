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
 * SCRYPT hash algorithm for Monetary System currencies
 *
 * This is Pass 2: Perform the Salsa8 shuffles
 */

/** Addition Java<->C definitions */
typedef uchar    BYTE;
typedef int      INT;
typedef uint     UINT;
typedef long     LONG;
typedef ulong    ULONG;
typedef uchar    BOOLEAN;

#define TRUE  1
#define FALSE 0

/**
 * SCRYPT state
 */
typedef struct {
             uint16 * restrict X0;              /* First half of Salsa array (All phases) */
             uint16 * restrict X1;              /* Second half of Salsa array (All phases) */
} State;

/** 
 * Kernel arguments 
 */
typedef struct This_s {
    __global uchar  * restrict input;           /* Input data (Phase 1 and Phase 3) */
    __global ulong  * restrict target;          /* Hash target (Phase 3) */
    __global ulong  * restrict solution;        /* Solution nonce (Phase 3) */
             int               passId;          /* Pass identifier */
    __global uint16 *          V;               /* Pad cache (Phase 2) */
} This;

/** Hash functions */
static void hash(This *this, State *state);
static void xorSalsa8(uint16 * restrict X0, uint16 * restrict X1);

/** Helper functions */
#ifdef USE_ROTATE
#define rotateLeft(x, c) rotate((x), (uint)(c))
#else
#define rotateLeft(x, c) (((x)<<c) | ((x)>>(32-c)))
#endif

/**
 * Do the hash
 */
static void hash(This *this, State *state) {
    int    i;
    //
    // The V array holds the pad cache.  To improve memory access performance, we
    // will group the cache entries together for each pass within the same work group instead
    // of grouping the entries together for each work item.  Each cache entry is 32
    // unsigned integers and there are 1024 cache entries for each work item.
    // 
    int groupSize = get_local_size(0);
    __global uint16 * vBase = this->V + (get_group_id(0)*groupSize*2*1024 + get_local_id(0)*2);
    int vInc = groupSize*2;
    //
    // Perform the hashes
    //
    __global uint16 * pV = vBase;
    for (i=0; i<1024; i++, pV+=vInc) {
        *pV = *state->X0;
        *(pV+1) = *state->X1;
        xorSalsa8(state->X0, state->X1);
        xorSalsa8(state->X1, state->X0);
    }
    for (i=0; i<1024; i++) {
        pV = vBase + (((*state->X1).s0 & 1023) * vInc);
        *state->X0 ^= *pV;
        *state->X1 ^= *(pV+1);
        xorSalsa8(state->X0, state->X1);
        xorSalsa8(state->X1, state->X0);
    }
}

/**
 * Scrypt permutation
 * 
 * @param       X0              First block
 * @param       X1              Second block
 */
static void xorSalsa8(uint16 * restrict X0, uint16 * restrict X1) {
    *X0 ^= *X1;
    uint W0  = (*X0).s0;   uint W1  = (*X0).s1;   uint W2  = (*X0).s2;    uint W3  = (*X0).s3;
    uint W4  = (*X0).s4;   uint W5  = (*X0).s5;   uint W6  = (*X0).s6;    uint W7  = (*X0).s7;
    uint W8  = (*X0).s8;   uint W9  = (*X0).s9;   uint W10 = (*X0).sA;    uint W11 = (*X0).sB;
    uint W12 = (*X0).sC;   uint W13 = (*X0).sD;   uint W14 = (*X0).sE;    uint W15 = (*X0).sF;
    //
    // We have a 4x4 matrix where we operate on the columns first and then on the rows
    //
    #pragma unroll
    for (int i=0; i<4; i++) {
            // Column operations
        W4  ^= rotateLeft(W0 + W12, 7);
        W9  ^= rotateLeft(W5 + W1, 7);
        W14 ^= rotateLeft(W10 + W6, 7);
        W3  ^= rotateLeft(W15 + W11, 7);
        
        W8  ^= rotateLeft(W4 + W0, 9);
        W13 ^= rotateLeft(W9 + W5, 9);
        W2  ^= rotateLeft(W14 + W10, 9);
        W7  ^= rotateLeft(W3 + W15, 9);
        
        W12 ^= rotateLeft(W8 + W4, 13);
        W1  ^= rotateLeft(W13 + W9, 13);
        W6  ^= rotateLeft(W2 + W14, 13);
        W11 ^= rotateLeft(W7 + W3, 13);
        
        W0  ^= rotateLeft(W12 + W8, 18);
        W5  ^= rotateLeft(W1 + W13, 18);
        W10 ^= rotateLeft(W6 + W2, 18);
        W15 ^= rotateLeft(W11 + W7, 18);
            // Row operations
        W1  ^= rotateLeft(W0 + W3, 7);
        W6  ^= rotateLeft(W5 + W4, 7);
        W11 ^= rotateLeft(W10 + W9, 7);
        W12 ^= rotateLeft(W15 + W14, 7);
        
        W2  ^= rotateLeft(W1 + W0, 9);
        W7  ^= rotateLeft(W6 + W5, 9);
        W8  ^= rotateLeft(W11 + W10, 9);
        W13 ^= rotateLeft(W12 + W15, 9);
        
        W3  ^= rotateLeft(W2 + W1, 13);
        W4  ^= rotateLeft(W7 + W6, 13);
        W9  ^= rotateLeft(W8 + W11, 13);
        W14 ^= rotateLeft(W13 + W12, 13);
        
        W0  ^= rotateLeft(W3 + W2, 18);
        W5  ^= rotateLeft(W4 + W7, 18);
        W10 ^= rotateLeft(W9 + W8, 18);
        W15 ^= rotateLeft(W14 + W13, 18);
    }
    (*X0).s0 += W0;  (*X0).s1 += W1;  (*X0).s2 += W2;  (*X0).s3 += W3;
    (*X0).s4 += W4;  (*X0).s5 += W5;  (*X0).s6 += W6;  (*X0).s7 += W7;
    (*X0).s8 += W8;  (*X0).s9 += W9;  (*X0).sA += W10; (*X0).sB += W11;
    (*X0).sC += W12; (*X0).sD += W13; (*X0).sE += W14; (*X0).sF += W15;
}

/**
 * Run the kernel
 */
__kernel void run(__global uchar  * kernelData, 
                  __global uchar  * stateBytes,
                  __global uint16 * V,
                           int      passId) {
    //
    // Pass kernel arguments to internal routines
    //
    This thisStruct;
    This* this = &thisStruct;
    this->passId = passId;
    this->V = V;
    //
    // Build the SCRYPT state
    //
    // X0 and X1 are allocated in private memory and are preserved in global memory
    //
    __global uchar *stateData = stateBytes+(get_global_id(0)*(3*296+8+2*64));
    State state;
    __global uint16 *pX0 = (__global uint16 *)(stateData+3*296+8);
    __global uint16 *pX1 = (__global uint16 *)(stateData+3*296+8+64);
    uint16 X0 = *pX0;
    uint16 X1 = *pX1;
    state.X0 = &X0;
    state.X1 = &X1;
    //
    // Hash the input data if we haven't found a solution yet
    //
    hash(this, &state);
    *pX0 = X0;
    *pX1 = X1;
}
