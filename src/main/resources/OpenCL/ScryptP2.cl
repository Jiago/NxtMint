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
 * SHA-256 work data
 */
typedef struct {
    UINT    DH[8];
    UINT    DX[64];
    BYTE    xBuff[4];
    INT     xOff;
    INT     xBuffOff;
    INT     xByteCount;
} Digest;

/**
 * SCRYPT state
 */
typedef struct {
    __global Digest * restrict digest;          /* SHA-256 digest (Phase 1 and 3) */
    __global Digest * restrict ipadDigest;      /* Saved input pad digest (Phase 1 and 3) */
    __global Digest * restrict opadDigest;      /* Saved output pad digest (Phase 1 and 3) */
             BYTE   * restrict B;               /* Hash buffer (Phase 1 and 3) */
             uint16 * restrict X0;              /* First half of Salsa array (All phases) */
             uint16 * restrict X1;              /* Second half of Salsa array (All phases) */
} State;

/** 
 * Kernel arguments 
 */
typedef struct This_s {
    __global uchar * restrict input;            /* Input data (Phase 1 and Phase 3) */
    __global ulong * restrict target;           /* Hash target (Phase 3) */
    __global ulong * restrict solution;         /* Solution nonce (Phase 3) */
             int              passId;           /* Pass identifier */
    __global uint  *          V;                /* Pad cache (Phase 2) */
} This;

/** Hash functions */
static void hash(This *this, State *state);
static void xorSalsa8(uint16 * restrict X0, uint16 * restrict X1);

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
    __global uint * vBase = this->V + (get_group_id(0)*groupSize*32*1024 + get_local_id(0)*32);
    int vInc = groupSize*32;
    //
    // Perform the hashes
    //
    __global uint * pV = vBase;
    for (i=0; i<1024; i++, pV+=vInc) {
        *(__global uint16 *)pV = *state->X0;
        *(__global uint16 *)(pV+16) = *state->X1;
        xorSalsa8(state->X0, state->X1);
        xorSalsa8(state->X1, state->X0);
    }
    for (i=0; i<1024; i++) {
        pV = vBase + (((*state->X1).s0 & 1023) * vInc);
        *state->X0 ^= *(__global uint16 *)pV;
        *state->X1 ^= *(__global uint16 *)(pV+16);
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
    uint16 W = *X0;
    //
    // We have a 4x4 matrix where we operate on the columns first and then on the rows
    //
    for (int i=0; i<8; i+=2) {
            // Column operations
        W.s49E3 ^= rotate((uint4)(W.s0 + W.sC,
                                  W.s5 + W.s1,
                                  W.sA + W.s6,
                                  W.sF + W.sB), 7);
        W.s8D27 ^= rotate((uint4)(W.s4 + W.s0,
                                  W.s9 + W.s5,
                                  W.sE + W.sA,
                                  W.s3 + W.sF), 9);
        W.sC16B ^= rotate((uint4)(W.s8 + W.s4,
                                  W.sD + W.s9,
                                  W.s2 + W.sE,
                                  W.s7 + W.s3), 13);
        W.s05AF ^= rotate((uint4)(W.sC + W.s8,
                                  W.s1 + W.sD,
                                  W.s6 + W.s2,
                                  W.sB + W.s7), 18);
            // Row operations
        W.s16BC ^= rotate((uint4)(W.s0 + W.s3,
                                  W.s5 + W.s4,
                                  W.sA + W.s9,
                                  W.sF + W.sE), 7);
        W.s278D ^= rotate((uint4)(W.s1 + W.s0,
                                  W.s6 + W.s5,
                                  W.sB + W.sA,
                                  W.sC + W.sF), 9);
        W.s349E ^= rotate((uint4)(W.s2 + W.s1,
                                  W.s7 + W.s6,
                                  W.s8 + W.sB,
                                  W.sD + W.sC), 13);
        W.s05AF ^= rotate((uint4)(W.s3 + W.s2,
                                  W.s4 + W.s7,
                                  W.s9 + W.s8,
                                  W.sE + W.sD), 18);
    }
    *X0 += W;
}

/**
 * Run the kernel
 */
__kernel void run(__global uchar * kernelData, 
                  __global uchar * stateBytes,
                  __global uint  * V,
                           int     passId) {
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
    __global uchar *stateData = stateBytes+(get_global_id(0)*(3*304+2*64));
    State state;
    uint16 X0, X1;
    X0 = vload16(0, (__global uint *)(stateData+3*304));
    X1 = vload16(0, (__global uint *)(stateData+3*304+64));
    state.X0 = &X0;
    state.X1 = &X1;
    //
    // Hash the input data if we haven't found a solution yet
    //
    hash(this, &state);
    vstore16(X0, 0, (__global uint *)(stateData+3*304));
    vstore16(X1, 0, (__global uint *)(stateData+3*304+64));
}
