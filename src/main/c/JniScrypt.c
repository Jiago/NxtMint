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
 */
#include <stdlib.h>
#include <stdio.h>
#include <memory.h>
#include "org_ScripterRon_NxtMint_HashScrypt.h"

/** Addition Java<->C definitions */
typedef unsigned char      BYTE;
typedef int                INT;
typedef unsigned int       UINT;
typedef long long          LONG;
typedef unsigned long long ULONG;
typedef unsigned char      BOOLEAN;

#define TRUE  1
#define FALSE 0

/** SHA-256 digest */
typedef struct {
    UINT    DH[8];
    UINT    DX[64];
    INT     xOff;
    LONG    xByteCount;
} Digest;

/** SCRYPT state */
typedef struct {
    Digest  digest;             // HMAC digest
    Digest  ipadDigest;         // Input pad digest
    Digest  opadDigest;         // Output pad digest
    BYTE    B[132];             // Work buffer
    UINT    V[32*1024];         // Pad buffer
    UINT    X[32];              // Block mixer
} State;

/** SHA-256 constants */
static const UINT K[] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

/** Hash function */
static BOOLEAN doHash(BYTE *input, BYTE *target, ULONG nonce, State *state);
static void xorSalsa2(State *state);
#define rotateLeft(x, y) (((x)<<(y)) | ((x)>>(32-y)))

/** HMAC functions */
static void initMac(State *state);
static void finishMac(BYTE *out, State *state);

/** SHA-256 functions */
static void updateDigest(BYTE *buffer, int inOff, int inLen, Digest *digest);
static void finishDigest(BYTE *out, Digest *digest);
static void resetDigest(Digest * digest);

/** SHA-256 helper functions */
static void processBlock(Digest *digest);
#define Ch(x, y, z)  (((x) & (y)) ^ ((~(x)) & (z)))
#define Maj(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define Sum0(x) ((((x)>>2) | ((x)<<30)) ^ (((x)>>13) | ((x)<<19)) ^ (((x)>>22) | ((x)<<10)))
#define Sum1(x) ((((x)>>6) | ((x)<<26)) ^ (((x)>>11) | ((x)<<21)) ^ (((x)>>25) | ((x)<<7)))

/**
 * Native Scrypt hash function
 *
 * @param       inputBytes      Input bytes
 * @param       targetBytes     Target bytes
 * @param       initialNonce    Initial nonce
 * @Param       count           Iteration count
 * @return                      Hash result
*/
JNIEXPORT jobject JNICALL Java_org_ScripterRon_NxtMint_HashScrypt_JniHash(JNIEnv *envp, jclass this,
                                jobjectArray jniInputBytes, jobjectArray jniTargetBytes,
                                jlong initialNonce, jint count) {
    ULONG nonce = (ULONG)initialNonce;
    int loop;
    //
    // Get the input data
    //
    jsize inputLength = (*envp)->GetArrayLength(envp, jniInputBytes);
    if (inputLength != 40) {
        printf("Input length is not 40 bytes\n");
        return NULL;
    }
    jbyte *inputBytes = (*envp)->GetByteArrayElements(envp, jniInputBytes, NULL);
    if (inputBytes == NULL) {
        printf("Unable to create input buffer\n");
        return NULL;
    }
    //
    // Get the target
    //
    jsize targetLength = (*envp)->GetArrayLength(envp, jniTargetBytes);
    if (targetLength != 32) {
        printf("Target length is not 32 bytes\n");
        return NULL;
    }
    jbyte *targetBytes = (*envp)->GetByteArrayElements(envp, jniTargetBytes, NULL);
    if (targetBytes == NULL) {
        printf("Unable to create trget buffer\n");
        return NULL;
    }
    //
    // Allocate the Scrypt state
    //
    State *state = malloc(sizeof(State));
    if (state == NULL) {
        printf("Unable to allocate Scrypt state storage");
        return NULL;
    }
    //
    // Iterate until we find a solution or the maximum loop count is reached
    //
    // The nonce is stored in the first 8 bytes of the input data in
    // little-endian format.  We will increment it for each hash pass.
    //
    int hashCount = 0;
    BOOLEAN meetsTarget = FALSE;
    for (loop=0; loop<count && !meetsTarget; loop++) {
        nonce++;
        meetsTarget = doHash(inputBytes, targetBytes, nonce, state);
        hashCount++;
    }
    //
    // Release the input parameters
    //
    (*envp)->ReleaseByteArrayElements(envp, jniInputBytes, inputBytes, 0);
    (*envp)->ReleaseByteArrayElements(envp, jniTargetBytes, targetBytes, 0);
    //
    // Return the result as a JniHashResult object
    //
    jclass class = (*envp)->FindClass(envp, "org/ScripterRon/NxtMint/JniHashResult");
    if (class == NULL) {
        printf("JniHashResult class not found");
        return NULL;
    }
    jmethodID mid = (*envp)->GetMethodID(envp, class, "<init>", "(ZJI)V");
    if (mid == NULL) {
        printf("JniHashResult method not found");
        return NULL;
    }
    jobject result = (*envp)->NewObject(envp, class, mid,
                     (jboolean)meetsTarget, (jlong)nonce, (jint)hashCount);
    //
    // Release the Scrypt state
    //
    free(state);
    return result;
}

/**
 * Perform a single hash
 *
 * @param       input           Input data
 * @param       target          Target
 * @param       nonce           Nonce
 * @param       state           Global state
 * @return                      TRUE if the target was met
 */
static BOOLEAN doHash(BYTE *input, BYTE *target, ULONG nonce, State *state) {
    int i, j;
    //
    // Initialize B from the input data
    //
    // The nonce is stored in the first 8 bytes of the input data
    //
    state->B[0] = (BYTE)nonce;
    state->B[1] = (BYTE)(nonce>>8);
    state->B[2] = (BYTE)(nonce>>16);
    state->B[3] = (BYTE)(nonce>>24);
    state->B[4] = (BYTE)(nonce>>32);
    state->B[5] = (BYTE)(nonce>>40);
    state->B[6] = (BYTE)(nonce>>48);
    state->B[7] = (BYTE)(nonce>>56);
    memcpy(&state->B[8], &input[8], 40-8);
    memset(&state->B[40], 0, 132-40);
    //
    // Initialize state
    //
    initMac(state);
    for (i=0; i<4; i++) {
        BYTE H[32];
        state->B[43] = (BYTE)(i + 1);
        updateDigest(state->B, 0, 44, &state->digest);
        finishMac(H, state);
        for (j=0; j<8; j++) {
            state->X[i*8+j] = ((UINT)H[j*4+0]&0xff) |
                              (((UINT)H[j*4+1]&0xff)<<8) |
                              (((UINT)H[j*4+2]&0xff)<<16) |
                              (((UINT)H[j*4+3]&0xff)<<24);
        }
    }
    //
    // Perform the hashes
    //
    for (i=0; i<1024; i++) {
        memcpy(&state->V[i*32], state->X, 32*sizeof(UINT));
        xorSalsa2(state);
    }
    for (i=0; i<1024; i++) {
        int k = (state->X[16]&1023)*32;
        for (j=0; j<32; j++)
            state->X[j] ^= state->V[k+j];
        xorSalsa2(state);
    }
    for (i=0; i<32; i++) {
        state->B[i*4+0] = (BYTE)(state->X[i]);
        state->B[i*4+1] = (BYTE)(state->X[i] >> 8);
        state->B[i*4+2] = (BYTE)(state->X[i] >> 16);
        state->B[i*4+3] = (BYTE)(state->X[i] >> 24);
    }
    state->B[128+3] = 1;
    updateDigest(state->B, 0, 132, &state->digest);
    BYTE digest[32];
    finishMac(digest, state);
    //
    // Save the digest if it satisfies the target.  Note that the digest and the target
    // are treated as 32-byte unsigned numbers in little-endian format.
    //
    BOOLEAN keepChecking = TRUE;
    BOOLEAN isSolved = TRUE;
    for (i=31; i>=0 && keepChecking; i--) {
        int b0 = (int)digest[i]&0xff;
        int b1 = (int)target[i]&0xff;
        if (b0 < b1) {
            keepChecking = FALSE;
        } else if (b0 > b1) {
            keepChecking = FALSE;
            isSolved = FALSE;
        }
    }
    return isSolved;
}

/**
 * Scrypt blockmix
 *
 * @param       state           Global state
 */
static inline void xorSalsa2(State * state) {
    int i;
    UINT value;
    //
    //  Process X[0]-X[15] and X[16]-X[31]
    //
    UINT x00 = state->X[0] ^= state->X[16+0];
    UINT x01 = state->X[1] ^= state->X[16+1];
    UINT x02 = state->X[2] ^= state->X[16+2];
    UINT x03 = state->X[3] ^= state->X[16+3];
    UINT x04 = state->X[4] ^= state->X[16+4];
    UINT x05 = state->X[5] ^= state->X[16+5];
    UINT x06 = state->X[6] ^= state->X[16+6];
    UINT x07 = state->X[7] ^= state->X[16+7];
    UINT x08 = state->X[8] ^= state->X[16+8];
    UINT x09 = state->X[9] ^= state->X[16+9];
    UINT x10 = state->X[10] ^= state->X[16+10];
    UINT x11 = state->X[11] ^= state->X[16+11];
    UINT x12 = state->X[12] ^= state->X[16+12];
    UINT x13 = state->X[13] ^= state->X[16+13];
    UINT x14 = state->X[14] ^= state->X[16+14];
    UINT x15 = state->X[15] ^= state->X[16+15];
    //
    // 4x4 matrix: 0   1   2   3
    //             4   5   6   7
    //             8   9  10  11
    //            12  13  14  15
    //
    for (i=0; i<8; i+=2) {
            // Column 0-4-8-12
        x04 ^= rotateLeft(x00+x12, 7);
        x08 ^= rotateLeft(x04+x00, 9);
        x12 ^= rotateLeft(x08+x04, 13);
        x00 ^= rotateLeft(x12+x08, 18);
            // Column 1-5-9-13
        x09 ^= rotateLeft(x05+x01, 7);
        x13 ^= rotateLeft(x09+x05, 9);
        x01 ^= rotateLeft(x13+x09, 13);
        x05 ^= rotateLeft(x01+x13, 18);
            // Column 2-6-10-14
        x14 ^= rotateLeft(x10+x06, 7);
        x02 ^= rotateLeft(x14+x10, 9);
        x06 ^= rotateLeft(x02+x14, 13);
        x10 ^= rotateLeft(x06+x02, 18);
            // Column 3-7-11-15
        x03 ^= rotateLeft(x15+x11, 7);
        x07 ^= rotateLeft(x03+x15, 9);
        x11 ^= rotateLeft(x07+x03, 13);
        x15 ^= rotateLeft(x11+x07, 18);
            // Row 0-1-2-3
        x01 ^= rotateLeft(x00+x03, 7);
        x02 ^= rotateLeft(x01+x00, 9);
        x03 ^= rotateLeft(x02+x01, 13);
        x00 ^= rotateLeft(x03+x02, 18);
            // Row 4-5-6-7
        x06 ^= rotateLeft(x05+x04, 7);
        x07 ^= rotateLeft(x06+x05, 9);
        x04 ^= rotateLeft(x07+x06, 13);
        x05 ^= rotateLeft(x04+x07, 18);
            // Row 8-9-10-11
        x11 ^= rotateLeft(x10+x09, 7);
        x08 ^= rotateLeft(x11+x10, 9);
        x09 ^= rotateLeft(x08+x11, 13);
        x10 ^= rotateLeft(x09+x08, 18);
            // Row 12-13-14-15
        x12 ^= rotateLeft(x15+x14, 7);
        x13 ^= rotateLeft(x12+x15, 9);
        x14 ^= rotateLeft(x13+x12, 13);
        x15 ^= rotateLeft(x14+x13, 18);
    }
    state->X[0] += x00;   state->X[1] += x01;
    state->X[2] += x02;   state->X[3] += x03;
    state->X[4] += x04;   state->X[5] += x05;
    state->X[6] += x06;   state->X[7] += x07;
    state->X[8] += x08;   state->X[9] += x09;
    state->X[10] += x10;  state->X[11] += x11;
    state->X[12] += x12;  state->X[13] += x13;
    state->X[14] += x14;  state->X[15] += x15;
    //
    //  Process X[16]-X[31] and X[0]-X[15]
    //
    x00 = state->X[16+0] ^= state->X[0];
    x01 = state->X[16+1] ^= state->X[1];
    x02 = state->X[16+2] ^= state->X[2];
    x03 = state->X[16+3] ^= state->X[3];
    x04 = state->X[16+4] ^= state->X[4];
    x05 = state->X[16+5] ^= state->X[5];
    x06 = state->X[16+6] ^= state->X[6];
    x07 = state->X[16+7] ^= state->X[7];
    x08 = state->X[16+8] ^= state->X[8];
    x09 = state->X[16+9] ^= state->X[9];
    x10 = state->X[16+10] ^= state->X[10];
    x11 = state->X[16+11] ^= state->X[11];
    x12 = state->X[16+12] ^= state->X[12];
    x13 = state->X[16+13] ^= state->X[13];
    x14 = state->X[16+14] ^= state->X[14];
    x15 = state->X[16+15] ^= state->X[15];
    for (i=0; i<8; i+=2) {
            // Column 0-4-8-12
        x04 ^= rotateLeft(x00+x12, 7);
        x08 ^= rotateLeft(x04+x00, 9);
        x12 ^= rotateLeft(x08+x04, 13);
        x00 ^= rotateLeft(x12+x08, 18);
            // Column 1-5-9-13
        x09 ^= rotateLeft(x05+x01, 7);
        x13 ^= rotateLeft(x09+x05, 9);
        x01 ^= rotateLeft(x13+x09, 13);
        x05 ^= rotateLeft(x01+x13, 18);
            // Column 2-6-10-14
        x14 ^= rotateLeft(x10+x06, 7);
        x02 ^= rotateLeft(x14+x10, 9);
        x06 ^= rotateLeft(x02+x14, 13);
        x10 ^= rotateLeft(x06+x02, 18);
            // Column 3-7-11-15
        x03 ^= rotateLeft(x15+x11, 7);
        x07 ^= rotateLeft(x03+x15, 9);
        x11 ^= rotateLeft(x07+x03, 13);
        x15 ^= rotateLeft(x11+x07, 18);
            // Row 0-1-2-3
        x01 ^= rotateLeft(x00+x03, 7);
        x02 ^= rotateLeft(x01+x00, 9);
        x03 ^= rotateLeft(x02+x01, 13);
        x00 ^= rotateLeft(x03+x02, 18);
            // Row 4-5-6-7
        x06 ^= rotateLeft(x05+x04, 7);
        x07 ^= rotateLeft(x06+x05, 9);
        x04 ^= rotateLeft(x07+x06, 13);
        x05 ^= rotateLeft(x04+x07, 18);
            // Row 8-9-10-11
        x11 ^= rotateLeft(x10+x09, 7);
        x08 ^= rotateLeft(x11+x10, 9);
        x09 ^= rotateLeft(x08+x11, 13);
        x10 ^= rotateLeft(x09+x08, 18);
            // Row 12-13-14-15
        x12 ^= rotateLeft(x15+x14, 7);
        x13 ^= rotateLeft(x12+x15, 9);
        x14 ^= rotateLeft(x13+x12, 13);
        x15 ^= rotateLeft(x14+x13, 18);
    }
    state->X[16+0] += x00;  state->X[16+1] += x01;
    state->X[16+2] += x02;  state->X[16+3] += x03;
    state->X[16+4] += x04;  state->X[16+5] += x05;
    state->X[16+6] += x06;  state->X[16+7] += x07;
    state->X[16+8] += x08;  state->X[16+9] += x09;
    state->X[16+10] += x10; state->X[16+11] += x11;
    state->X[16+12] += x12; state->X[16+13] += x13;
    state->X[16+14] += x14; state->X[16+15] += x15;
}

/**
 * Initialize the MAC
 *
 * @param       state           SCRYPT state
 */
static void initMac(State *state) {
    BYTE        inputPad[64];
    BYTE        outputBuf[64];
    //
    // Reset the SHA-256 digest
    //
    resetDigest(&state->digest);
    //
    // Initialize the HMAC buffers
    //
    int i;
    for (i=0; i<40; i++) {
        outputBuf[i] = state->B[i] ^ (BYTE)0x5c;
        inputPad[i] = state->B[i] ^ (BYTE)0x36;
    }
    for (i=40; i<64; i++) {
        outputBuf[i] = (BYTE)0x5c;
        inputPad[i] = (BYTE)0x36;
    }
    //
    // Save the output digest to improve HMAC reset performance
    //
    memcpy(&state->opadDigest, &state->digest, sizeof(Digest));
    updateDigest(outputBuf, 0, 64, &state->opadDigest);
    //
    // Save the input digest to improve HMAC reset performance
    //
    updateDigest(inputPad, 0, 64, &state->digest);
    memcpy(&state->ipadDigest, &state->digest, sizeof(Digest));
}

/**
 * Finish the MAC
 *
 * @param       out             32-byte output buffer
 * @param       state           SCRYPT state
 */
static void finishMac(BYTE *out, State *state) {
    //
    // Finish the current digest
    //
    finishDigest(out, &state->digest);
    //
    // Hash the digest output using the saved opad digest
    //
    memcpy(&state->digest, &state->opadDigest, sizeof(Digest));
    updateDigest(out, 0, 32, &state->digest);
    //
    // Finish the MAC digest
    //
    finishDigest(out, &state->digest);
    //
    // Reset the MAC digest from the saved ipad digest
    //
    memcpy(&state->digest, &state->ipadDigest, sizeof(Digest));
}

/**
 * Reset the SHA-256 digest
 *
 * @param       digest          SHA-256 digest
 */
static void resetDigest(Digest *digest) {
    digest->DH[0] = 0x6a09e667;
    digest->DH[1] = 0xbb67ae85;
    digest->DH[2] = 0x3c6ef372;
    digest->DH[3] = 0xa54ff53a;
    digest->DH[4] = 0x510e527f;
    digest->DH[5] = 0x9b05688c;
    digest->DH[6] = 0x1f83d9ab;
    digest->DH[7] = 0x5be0cd19;
    digest->xOff = 0;
    memset(digest->DX, 0, 64*sizeof(UINT));
    digest->xByteCount = 0;
}

/**
 * Update the digest
 *
 * @param       buffer          Data buffer
 * @param       inOff           Offset to start of data
 * @param       inLen           Data length (must be multiple of 4)
 * @param       digest          SHA-256 digest
 */
static void updateDigest(BYTE *buffer, int inOff, int inLen, Digest *digest) {
    int len = inLen;
    int offset = inOff;
    //
    // Process whole words
    //
    while (len>0) {
        digest->DX[digest->xOff++] = (((UINT)buffer[offset]&0xff)<<24) |
                                     (((UINT)buffer[offset+1]&0xff)<<16) |
                                     (((UINT)buffer[offset+2]&0xff)<<8) |
                                     ((UINT)buffer[offset+3]&0xff);
        if (digest->xOff == 16)
            processBlock(digest);
        offset += 4;
        len -= 4;
        digest->xByteCount += 4;
    }
}

/**
 * Finish the digest
 *
 * @param       out         Digest output buffer (32 bytes)
 * @param       digest      SHA-256 digest
 */
static void finishDigest(BYTE *out, Digest *digest) {
    LONG bitLength = (digest->xByteCount<<3);
    //
    // Add the pad bytes
    //
    // The first byte is 0x80 followed by 0x00.  The last two bytes
    // contain the data length in bits
    //
    digest->DX[digest->xOff++] = 0x80000000U;
    if (digest->xOff > 14)
        processBlock(digest);
    digest->DX[14] = (UINT)(bitLength >> 32);
    digest->DX[15] = (UINT)bitLength;
    //
    // Process the last block
    //
    processBlock(digest);
    //
    // Convert the digest to bytes in big-endian format
    //
    int i;
    for (i=0; i<8; i++) {
        out[i*4] =   (BYTE)(digest->DH[i] >> 24);
        out[i*4+1] = (BYTE)(digest->DH[i] >> 16);
        out[i*4+2] = (BYTE)(digest->DH[i] >> 8);
        out[i*4+3] = (BYTE)(digest->DH[i]);
    }
    //
    // Reset the digest
    //
    resetDigest(digest);
}

/**
 * Process a 16-word block
 *
 * @param       digest          SHA-256 digest
 */
static void processBlock(Digest *digest) {
    int t, i;
    for (t=16; t<64; t++) {
        UINT x = digest->DX[t-15];
        UINT r0 = ((x>>7) | (x<<25)) ^ ((x>>18) | (x<<14)) ^ (x>>3);
        x = digest->DX[t-2];
        UINT r1 = ((x>>17) | (x<<15)) ^ ((x>>19) | (x<<13)) ^ (x>>10);
        digest->DX[t] = r1 + digest->DX[t-7] + r0 + digest->DX[t-16];
    }
    UINT a = digest->DH[0];  UINT b = digest->DH[1];
    UINT c = digest->DH[2];  UINT d = digest->DH[3];
    UINT e = digest->DH[4];  UINT f = digest->DH[5];
    UINT g = digest->DH[6];  UINT h = digest->DH[7];
    t = 0;
    for(i=0; i<8; i ++) {
        h += Sum1(e) + Ch(e, f, g) + K[t] + digest->DX[t];
        d += h;
        h += Sum0(a) + Maj(a, b, c);
        ++t;

        g += Sum1(d) + Ch(d, e, f) + K[t] + digest->DX[t];
        c += g;
        g += Sum0(h) + Maj(h, a, b);
        ++t;

        f += Sum1(c) + Ch(c, d, e) + K[t] + digest->DX[t];
        b += f;
        f += Sum0(g) + Maj(g, h, a);
        ++t;

        e += Sum1(b) + Ch(b, c, d) + K[t] + digest->DX[t];
        a += e;
        e += Sum0(f) + Maj(f, g, h);
        ++t;

        d += Sum1(a) + Ch(a, b, c) + K[t] + digest->DX[t];
        h += d;
        d += Sum0(e) + Maj(e, f, g);
        ++t;

        c += Sum1(h) + Ch(h, a, b) + K[t] + digest->DX[t];
        g += c;
        c += Sum0(d) + Maj(d, e, f);
        ++t;

        b += Sum1(g) + Ch(g, h, a) + K[t] + digest->DX[t];
        f += b;
        b += Sum0(c) + Maj(c, d, e);
        ++t;

        a += Sum1(f) + Ch(f, g, h) + K[t] + digest->DX[t];
        e += a;
        a += Sum0(b) + Maj(b, c, d);
        ++t;
    }
    digest->DH[0] += a;
    digest->DH[1] += b;
    digest->DH[2] += c;
    digest->DH[3] += d;
    digest->DH[4] += e;
    digest->DH[5] += f;
    digest->DH[6] += g;
    digest->DH[7] += h;
    digest->xOff = 0;
    memset(digest->DX, 0, 16*sizeof(UINT));
}
