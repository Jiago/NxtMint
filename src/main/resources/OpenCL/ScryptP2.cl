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
    __global Digest  *digest;           /* SHA-256 digest (Phase 1 and 3) */
    __global Digest  *ipadDigest;       /* Saved input pad digest (Phase 1 and 3) */
    __global Digest  *opadDigest;       /* Saved output pad digest (Phase 1 and 3) */
    BYTE    *B;                         /* Hash buffer (Phase 1 and 3) */
    uint16  *X0;                        /* First half of Salsa array (All phases) */
    uint16  *X1;                        /* Second half of Salsa array (All phases) */
} State;

/** 
 * Kernel arguments 
 */
typedef struct This_s {
    __global uchar  *input;             /* Input data */
    __global uchar  *target;            /* Hash target */
    __global uint   *done;              /* Solution found indicator */
    __global uchar  *solution;          /* Solution nonce */
             int    passId;             /* Pass identifier */
    __global uint   *V;                 /* SCRYPT salsa storage (Phase 2) */
} This;

/** Hash functions */
static void hash(This *this, State *state);
static void xorSalsa8(uint16 *X0, uint16 *X1);

/** Rotate left for unsigned integer value */
#define rotateLeft(x, c) (((x)<<c) | ((x)>>(32-c)))

/**
 * Do the hash
 */
static void hash(This *this, State *state) {
    uint    *pX0 = (uint *)state->X0;
    uint    *pX1 = (uint *)state->X1;
    int     i;
    //
    // Perform the hashes
    //
    for (i=0; i<1024; i++) {
        *(__global uint16 *)&this->V[i*32] = *state->X0;
        *(__global uint16 *)&this->V[i*32+16] = *state->X1;
        xorSalsa8(state->X0, state->X1);
        xorSalsa8(state->X1, state->X0);
    }
    for (i=0; i<1024; i++) {
        int k = ((*state->X1).s0 & 1023) * 32;
        *state->X0 ^= *(__global uint16 *)&this->V[k];
        *state->X1 ^= *(__global uint16 *)&this->V[k+16];
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
static void xorSalsa8(uint16 *X0, uint16 *X1) {
    *X0 ^= *X1;
    UINT x00 = (*X0).s0;  UINT x01 = (*X0).s1;   UINT x02 = (*X0).s2;   UINT x03 = (*X0).s3;
    UINT x04 = (*X0).s4;  UINT x05 = (*X0).s5;   UINT x06 = (*X0).s6;   UINT x07 = (*X0).s7;
    UINT x08 = (*X0).s8;  UINT x09 = (*X0).s9;   UINT x10 = (*X0).sA;   UINT x11 = (*X0).sB;
    UINT x12 = (*X0).sC;  UINT x13 = (*X0).sD;   UINT x14 = (*X0).sE;   UINT x15 = (*X0).sF;
    int i;
    for (i=0; i<8; i+=2) {
        x04 ^= rotateLeft(x00 + x12, 7);
        x08 ^= rotateLeft(x04 + x00, 9);
        x12 ^= rotateLeft(x08 + x04, 13);
        x00 ^= rotateLeft(x12 + x08, 18);
        x09 ^= rotateLeft(x05 + x01, 7);
        x13 ^= rotateLeft(x09 + x05, 9);
        x01 ^= rotateLeft(x13 + x09, 13);
        x05 ^= rotateLeft(x01 + x13, 18);
        x14 ^= rotateLeft(x10 + x06, 7);
        x02 ^= rotateLeft(x14 + x10, 9);
        x06 ^= rotateLeft(x02 + x14, 13);
        x10 ^= rotateLeft(x06 + x02, 18);
        x03 ^= rotateLeft(x15 + x11, 7);
        x07 ^= rotateLeft(x03 + x15, 9);
        x11 ^= rotateLeft(x07 + x03, 13);
        x15 ^= rotateLeft(x11 + x07, 18);
        x01 ^= rotateLeft(x00 + x03, 7);
        x02 ^= rotateLeft(x01 + x00, 9);
        x03 ^= rotateLeft(x02 + x01, 13);
        x00 ^= rotateLeft(x03 + x02, 18);
        x06 ^= rotateLeft(x05 + x04, 7);
        x07 ^= rotateLeft(x06 + x05, 9);
        x04 ^= rotateLeft(x07 + x06, 13);
        x05 ^= rotateLeft(x04 + x07, 18);
        x11 ^= rotateLeft(x10 + x09, 7);
        x08 ^= rotateLeft(x11 + x10, 9);
        x09 ^= rotateLeft(x08 + x11, 13);
        x10 ^= rotateLeft(x09 + x08, 18);
        x12 ^= rotateLeft(x15 + x14, 7);
        x13 ^= rotateLeft(x12 + x15, 9);
        x14 ^= rotateLeft(x13 + x12, 13);
        x15 ^= rotateLeft(x14 + x13, 18);
    }
    *X0 += (uint16)(x00, x01, x02, x03, x04, x05, x06, x07, x08, x09, x10, x11, x12, x13, x14, x15);
}

/**
 * Run the kernel
 */
__kernel void run(__global uchar  *kernelData, 
                  __global uchar  *stateBytes,
                  __global uint   *V,
                           int    passId) {
    //
    // Pass kernel arguments to internal routines
    //
    This thisStruct;
    This* this = &thisStruct;
    this->input = kernelData+0;
    this->target = kernelData+40;
    this->solution = kernelData+72;
    this->done = (__global uint *)(kernelData+80);
    this->passId = passId;
    this->V = &V[get_global_id(0)*32*1024];
    //
    // Build the SCRYPT state
    //
    // X0 and X1 are allocated in private memory and are preserved in global memory
    //
    __global uchar *stateData = stateBytes+(get_global_id(0)*(3*304+2*64));
    State state;
    uint16 X0 = vload16(0, (__global uint *)(stateData+3*304));
    state.X0 = &X0;
    uint16 X1 = vload16(0, (__global uint *)(stateData+3*304+64));
    state.X1 = &X1;
    //
    // Hash the input data if we haven't found a solution yet
    //
    if (this->done[0] == 0) {
        hash(this, &state);
        vstore16(X0, 0, (__global uint *)(stateData+3*304));
        vstore16(X1, 0, (__global uint *)(stateData+3*304+64));
    }    
}
