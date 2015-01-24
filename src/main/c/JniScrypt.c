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

/** SCRYPT state */
typedef struct {
    /** SHA-256 data areas */
    UINT  DH[8];
    UINT  DX[64];
    INT   xOff;
    BYTE  xBuff[4];
    INT   xBuffOff;
    LONG  xByteCount;
    BYTE  digest[32];

    /** Digest save areas */
    UINT  sDH[8];
    UINT  sDX[64];
    INT   sxOff;
    BYTE  sxBuff[4];
    INT   sxBuffOff;
    LONG  sxByteCount;

    /** IPad digest save areas */
    UINT  ipDH[8];
    UINT  ipDX[64];
    INT   ipxOff;
    BYTE  ipxBuff[4];
    INT   ipxBuffOff;
    LONG  ipxByteCount;

    /** OPad digest save areas */
    UINT  opDH[8];
    UINT  opDX[64];
    INT   opxOff;
    BYTE  opxBuff[4];
    INT   opxBuffOff;
    LONG  opxByteCount;

    /** HMacSha256 data areas */
    BYTE  inputPad[64];
    BYTE  outputBuf[96];
    BYTE  macDigest[32];

    /** SCRYPT data areas  */
    BYTE  H[32];
    BYTE  B[132];
    UINT  V[32*1024];
    UINT  X[32];
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

/** HMAC functions */
static void initMac(State *state);
static void finishMac(State *state);

/** SHA-256 functions */
static void updateDigestByte(BYTE in, State *state);
static void updateDigest(BYTE *buffer, int inOff, int inLen, State *state);
static void finishDigest(State *state);
static void resetDigest(State *state);

/** SHA-256 helper functions */
static void processBlock(State *state);
static void processWord(BYTE *buffer, int inOff, State *state);
static void saveDigest(State *state);
static void restoreDigest(State *state);
static void saveIPadDigest(State *state);
static void restoreIPadDigest(State *state);
static void saveOPadDigest(State *state);
static void restoreOPadDigest(State *state);
static UINT Ch(UINT x, UINT y, UINT z);
static UINT Maj(UINT x, UINT y, UINT z);
static UINT Sum0(UINT x);
static UINT Sum1(UINT x);

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
        state->B[43] = (BYTE)(i + 1);
        updateDigest(state->B, 0, 44, state);
        finishMac(state);
        memcpy(state->H, state->macDigest, 32);
        for (j=0; j<8; j++) {
            state->X[i*8+j] = ((UINT)state->H[j*4+0]&0xff) | 
                              (((UINT)state->H[j*4+1]&0xff)<<8) | 
                              (((UINT)state->H[j*4+2]&0xff)<<16) | 
                              (((UINT)state->H[j*4+3]&0xff)<<24);
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
        UINT x = state->X[i];
        state->B[i*4+0] = (BYTE)(x);
        state->B[i*4+1] = (BYTE)(x >> 8);
        state->B[i*4+2] = (BYTE)(x >> 16);
        state->B[i*4+3] = (BYTE)(x >> 24);
    }
    state->B[128+3] = 1;
    updateDigest(state->B, 0, 132, state);
    finishMac(state);
    //
    // Save the digest if it satisfies the target.  Note that the digest and the target
    // are treated as 32-byte unsigned numbers in little-endian format.
    //
    BOOLEAN keepChecking = TRUE;
    BOOLEAN isSolved = TRUE;
    for (i=31; i>=0 && keepChecking; i--) {
        int b0 = (int)state->macDigest[i]&0xff;
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
 * Scrypt permutation
 * 
 * @param       state           Global state
 */
static void xorSalsa2(State *state) {
    int i;
    UINT value;
    //
    //  Process X[0]-X[15] and X[16]-X[31]
    //
    state->X[0] ^= state->X[16+0];   
    UINT x00 = state->X[0];
    state->X[1] ^= state->X[16+1];  
    UINT x01 = state->X[1];
    state->X[2] ^= state->X[16+2];   
    UINT x02 = state->X[2];
    state->X[3] ^= state->X[16+3];  
    UINT x03 = state->X[3];
    state->X[4] ^= state->X[16+4];   
    UINT x04 = state->X[4];
    state->X[5] ^= state->X[16+5];  
    UINT x05 = state->X[5];
    state->X[6] ^= state->X[16+6];   
    UINT x06 = state->X[6];
    state->X[7] ^= state->X[16+7];  
    UINT x07 = state->X[7];
    state->X[8] ^= state->X[16+8];   
    UINT x08 = state->X[8];
    state->X[9] ^= state->X[16+9];
    UINT x09 = state->X[9];
    state->X[10] ^= state->X[16+10]; 
    UINT x10 = state->X[10];
    state->X[11] ^= state->X[16+11];  
    UINT x11 = state->X[11];
    state->X[12] ^= state->X[16+12]; 
    UINT x12 = state->X[12];
    state->X[13] ^= state->X[16+13];  
    UINT x13 = state->X[13];
    state->X[14] ^= state->X[16+14]; 
    UINT x14 = state->X[14];
    state->X[15] ^= state->X[16+15];
    UINT x15 = state->X[15];
    //
    // 4x4 matrix: 0   1   2   3
    //             4   5   6   7
    //             8   9  10  11
    //            12  13  14  15
    //
    for (i=0; i<8; i+=2) {
            // Column 0-4-8-12
        value = x00+x12;  x04 ^= (value<<7) | (value>>(32-7));
        value = x04+x00;  x08 ^= (value<<9) | (value>>(32-9));
        value = x08+x04;  x12 ^= (value<<13) | (value>>(32-13));
        value = x12+x08;  x00 ^= (value<<18) | (value>>(32-18));
            // Column 1-5-9-13
        value = x05+x01;  x09 ^= (value<<7) | (value>>(32-7));
        value = x09+x05;  x13 ^= (value<<9) | (value>>(32-9));
        value = x13+x09;  x01 ^= (value<<13) | (value>>(32-13));
        value = x01+x13;  x05 ^= (value<<18) | (value>>(32-18));
            // Column 2-6-10-14
        value = x10+x06;  x14 ^= (value<<7) | (value>>(32-7));
        value = x14+x10;  x02 ^= (value<<9) | (value>>(32-9));
        value = x02+x14;  x06 ^= (value<<13) | (value>>(32-13));
        value = x06+x02;  x10 ^= (value<<18) | (value>>(32-18));
            // Column 3-7-11-15
        value = x15+x11;  x03 ^= (value<<7) | (value>>(32-7));
        value = x03+x15;  x07 ^= (value<<9) | (value>>(32-9));
        value = x07+x03;  x11 ^= (value<<13) | (value>>(32-13));
        value = x11+x07;  x15 ^= (value<<18) | (value>>(32-18));
            // Row 0-1-2-3
        value = x00+x03;  x01 ^= (value<<7) | (value>>(32-7));
        value = x01+x00;  x02 ^= (value<<9) | (value>>(32-9));
        value = x02+x01;  x03 ^= (value<<13) | (value>>(32-13));
        value = x03+x02;  x00 ^= (value<<18) | (value>>(32-18));
            // Row 4-5-6-7
        value = x05+x04;  x06 ^= (value<<7) | (value>>(32-7));
        value = x06+x05;  x07 ^= (value<<9) | (value>>(32-9));
        value = x07+x06;  x04 ^= (value<<13) | (value>>(32-13));
        value = x04+x07;  x05 ^= (value<<18) | (value>>(32-18));
            // Row 8-9-10-11
        value = x10+x09;  x11 ^= (value<<7) | (value>>(32-7));
        value = x11+x10;  x08 ^= (value<<9) | (value>>(32-9));
        value = x08+x11;  x09 ^= (value<<13) | (value>>(32-13));
        value = x09+x08;  x10 ^= (value<<18) | (value>>(32-18));
            // Row 12-13-14-15
        value = x15+x14;  x12 ^= (value<<7) | (value>>(32-7));
        value = x12+x15;  x13 ^= (value<<9) | (value>>(32-9));
        value = x13+x12;  x14 ^= (value<<13) | (value>>(32-13));
        value = x14+x13;  x15 ^= (value<<18) | (value>>(32-18));
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
    state->X[16+0] ^= state->X[0];    
    x00 = state->X[16+0];
    state->X[16+1] ^= state->X[1];  
    x01 = state->X[16+1];
    state->X[16+2] ^= state->X[2];    
    x02 = state->X[16+2];
    state->X[16+3] ^= state->X[3];  
    x03 = state->X[16+3];
    state->X[16+4] ^= state->X[4];    
    x04 = state->X[16+4];
    state->X[16+5] ^= state->X[5];  
    x05 = state->X[16+5];
    state->X[16+6] ^= state->X[6];    
    x06 = state->X[16+6];
    state->X[16+7] ^= state->X[7];  
    x07 = state->X[16+7];
    state->X[16+8] ^= state->X[8];    
    x08 = state->X[16+8];
    state->X[16+9] ^= state->X[9];
    x09 = state->X[16+9];
    state->X[16+10] ^= state->X[10];  
    x10 = state->X[16+10];
    state->X[16+11] ^= state->X[11];  
    x11 = state->X[16+11];
    state->X[16+12] ^= state->X[12];  
    x12 = state->X[16+12];
    state->X[16+13] ^= state->X[13];  
    x13 = state->X[16+13];  
    state->X[16+14] ^= state->X[14];  
    x14 = state->X[16+14];
    state->X[16+15] ^= state->X[15];
    x15 = state->X[16+15];
    for (i=0; i<8; i+=2) {
            // Column 0-4-8-12
        value = x00+x12;  x04 ^= (value<<7) | (value>>(32-7));
        value = x04+x00;  x08 ^= (value<<9) | (value>>(32-9));
        value = x08+x04;  x12 ^= (value<<13) | (value>>(32-13));
        value = x12+x08;  x00 ^= (value<<18) | (value>>(32-18));
            // Column 1-5-9-13
        value = x05+x01;  x09 ^= (value<<7) | (value>>(32-7));
        value = x09+x05;  x13 ^= (value<<9) | (value>>(32-9));
        value = x13+x09;  x01 ^= (value<<13) | (value>>(32-13));
        value = x01+x13;  x05 ^= (value<<18) | (value>>(32-18));
            // Column 2-6-10-14
        value = x10+x06;  x14 ^= (value<<7) | (value>>(32-7));
        value = x14+x10;  x02 ^= (value<<9) | (value>>(32-9));
        value = x02+x14;  x06 ^= (value<<13) | (value>>(32-13));
        value = x06+x02;  x10 ^= (value<<18) | (value>>(32-18));
            // Column 3-7-11-15
        value = x15+x11;  x03 ^= (value<<7) | (value>>(32-7));
        value = x03+x15;  x07 ^= (value<<9) | (value>>(32-9));
        value = x07+x03;  x11 ^= (value<<13) | (value>>(32-13));
        value = x11+x07;  x15 ^= (value<<18) | (value>>(32-18));
            // Row 0-1-2-3
        value = x00+x03;  x01 ^= (value<<7) | (value>>(32-7));
        value = x01+x00;  x02 ^= (value<<9) | (value>>(32-9));
        value = x02+x01;  x03 ^= (value<<13) | (value>>(32-13));
        value = x03+x02;  x00 ^= (value<<18) | (value>>(32-18));
            // Row 4-5-6-7
        value = x05+x04;  x06 ^= (value<<7) | (value>>(32-7));
        value = x06+x05;  x07 ^= (value<<9) | (value>>(32-9));
        value = x07+x06;  x04 ^= (value<<13) | (value>>(32-13));
        value = x04+x07;  x05 ^= (value<<18) | (value>>(32-18));
            // Row 8-9-10-11
        value = x10+x09;  x11 ^= (value<<7) | (value>>(32-7));
        value = x11+x10;  x08 ^= (value<<9) | (value>>(32-9));
        value = x08+x11;  x09 ^= (value<<13) | (value>>(32-13));
        value = x09+x08;  x10 ^= (value<<18) | (value>>(32-18));
            // Row 12-13-14-15
        value = x15+x14;  x12 ^= (value<<7) | (value>>(32-7));
        value = x12+x15;  x13 ^= (value<<9) | (value>>(32-9));
        value = x13+x12;  x14 ^= (value<<13) | (value>>(32-13));
        value = x14+x13;  x15 ^= (value<<18) | (value>>(32-18));
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
 * @param       state           Global state
 */
static void initMac(State *state) {
    //
    // Reset the SHA-256 digest
    //
    resetDigest(state);
    //
    // Initialize the input pad
    //
    memcpy(&state->inputPad[0], state->B, 40);
    memset(&state->inputPad[40], 0, 24);
    //
    // Initialize the output buffer
    //
    int i;
    memcpy(state->outputBuf, state->inputPad, 64);
    for (i=0; i<64; i++) {
        state->inputPad[i] ^= (BYTE)0x36;
        state->outputBuf[i] ^= (BYTE)0x5c;
    }
    //
    // Save the output digest to improve HMac reset performance
    //
    saveDigest(state);
    updateDigest(state->outputBuf, 0, 64, state);
    saveOPadDigest(state);
    restoreDigest(state);
    //
    // Save the input digest to improve HMac reset performance
    //
    updateDigest(state->inputPad, 0, 64, state);
    saveIPadDigest(state);
}

/**
 * Finish the MAC
 * 
 * @param       state           Global state
 */
static void finishMac(State *state) {
    //
    // Finish the current digest
    //
    finishDigest(state);
    memcpy(&state->outputBuf[64], state->digest, 32);
    //
    // Hash the digest output
    //
    restoreOPadDigest(state);
    updateDigest(state->outputBuf, 64, 32, state);
    //
    // Finish the MAC digest
    //
    finishDigest(state);
    memcpy(state->macDigest, state->digest, 32);
    //
    // Reset the output buffer
    //
    memset(&state->outputBuf[64], 0, 32);
    //
    // Reset the MAC digest
    //
    restoreIPadDigest(state);
}

/**
 * Reset the SHA-256 digest
 * 
 * @param       state           Global state
 */
static void resetDigest(State *state) {
    state->DH[0] = 0x6a09e667;
    state->DH[1] = 0xbb67ae85;
    state->DH[2] = 0x3c6ef372;
    state->DH[3] = 0xa54ff53a;
    state->DH[4] = 0x510e527f;
    state->DH[5] = 0x9b05688c;
    state->DH[6] = 0x1f83d9ab;
    state->DH[7] = 0x5be0cd19;
    state->xOff = 0;
    memset(state->DX, 0, 64*sizeof(UINT));
    state->xBuffOff = 0;
    state->xByteCount = 0;
}

/**
 * Save the current digest
 * 
 * @param       state       Global state
 */
static void saveDigest(State *state) {
    memcpy(state->sDH, state->DH, 8*sizeof(UINT));
    memcpy(state->sDX, state->DX, 64*sizeof(UINT));
    memcpy(state->sxBuff, state->xBuff, 4*sizeof(BYTE));
    state->sxOff = state->xOff;
    state->sxBuffOff = state->xBuffOff;
    state->sxByteCount = state->xByteCount;
}

/**
 * Restore the current digest
 * 
 * @param       state       Global state
 */
static void restoreDigest(State *state) {
    memcpy(state->DH, state->sDH, 8*sizeof(UINT));
    memcpy(state->DX, state->sDX, 64*sizeof(UINT));
    memcpy(state->xBuff, state->sxBuff, 4*sizeof(BYTE));
    state->xOff = state->sxOff;
    state->xBuffOff = state->sxBuffOff;
    state->xByteCount = state->sxByteCount;
}

/**
 * Save the current iPad digest
 * 
 * @param       state       Global state
 */
static void saveIPadDigest(State *state) {
    memcpy(state->ipDH, state->DH, 8*sizeof(UINT));
    memcpy(state->ipDX, state->DX, 64*sizeof(UINT));
    memcpy(state->ipxBuff, state->xBuff, 4*sizeof(BYTE));
    state->ipxOff = state->xOff;
    state->ipxBuffOff = state->xBuffOff;
    state->ipxByteCount = state->xByteCount;
}

/**
 * Restore the current iPad digest
 * 
 * @param       state           Global state
 */
static void restoreIPadDigest(State *state) {
    memcpy(state->DH, state->ipDH, 8*sizeof(UINT));
    memcpy(state->DX, state->ipDX, 64*sizeof(UINT));
    memcpy(state->xBuff, state->ipxBuff, 4*sizeof(BYTE));
    state->xOff = state->ipxOff;
    state->xBuffOff = state->ipxBuffOff;
    state->xByteCount = state->ipxByteCount;
}

/**
 * Save the current oPad digest
 * 
 * @param       state           Global state
 */
static void saveOPadDigest(State *state) {
    memcpy(state->opDH, state->DH, 8*sizeof(UINT));
    memcpy(state->opDX, state->DX, 64*sizeof(UINT));
    memcpy(state->opxBuff, state->xBuff, 4*sizeof(BYTE));
    state->opxOff = state->xOff;
    state->opxBuffOff = state->xBuffOff;
    state->opxByteCount = state->xByteCount;
}

/**
 * Restore the current oPad digest
 * 
 * @param       state           Global state
 */
static void restoreOPadDigest(State *state) {
    memcpy(state->DH, state->opDH, 8*sizeof(UINT));
    memcpy(state->DX, state->opDX, 64*sizeof(UINT));
    memcpy(state->xBuff, state->opxBuff, 4*sizeof(BYTE));
    state->xOff = state->opxOff;
    state->xBuffOff = state->opxBuffOff;
    state->xByteCount = state->opxByteCount;
}

/**
 * Update the digest
 * 
 * @param       in              Data
 * @param       state           Global state
 */
static void updateDigestByte(BYTE in, State *state) {
    state->xBuff[state->xBuffOff++] = in;
    if (state->xBuffOff == 4) {
        state->DX[state->xOff++] = (((UINT)state->xBuff[0]&0xff) << 24) |
                                   (((UINT)state->xBuff[1]&0xff) << 16) |
                                   (((UINT)state->xBuff[2]&0xff) << 8) |
                                   ((UINT)state->xBuff[3]&0xff);
        if (state->xOff == 16)
            processBlock(state);
        state->xBuffOff = 0;
    }
    state->xByteCount++;
}

/**
 * Update the digest
 * 
 * @param       buffer          Data buffer
 * @param       inOff           Buffer offset to start of data
 * @param       inLen           Data length
 * @param       state           Global state
 */
static void updateDigest(BYTE *buffer, int inOff, int inLen, State *state) {
    int len = inLen;
    int offset = inOff;
    //
    // Fill the current word
    //
    while (state->xBuffOff!=0 && len>0) {
        updateDigestByte(buffer[offset++], state);
        len--;
    }
    //
    // Process whole words.
    //
    while (len>4) {
        processWord(buffer, offset, state);
        offset += 4;
        len -= 4;
        state->xByteCount += 4;
    }
    //
    // Load in the remainder.
    //
    while (len > 0) {
        updateDigestByte(buffer[offset++], state);
        len--;
    }
}

/**
 * Finish the digest
 * 
 * @param       state       Global state
 */
static void finishDigest(State *state) {
    LONG bitLength = (state->xByteCount << 3);
    //
    // Add the pad bytes
    //
    // The first byte is 0x80 followed by 0x00.  The last two bytes
    // contain the data length in bits
    //
    updateDigestByte((BYTE)128, state);
    while (state->xBuffOff != 0)
        updateDigestByte((BYTE)0, state);
    if (state->xOff > 14)
        processBlock(state);
    state->DX[14] = (UINT)(bitLength >> 32);
    state->DX[15] = (UINT)(bitLength & 0xffffffff);
    //
    // Process the last block
    //
    processBlock(state);
    //
    // Convert the digest to bytes in big-endian format
    //
    int i;
    for (i=0; i<8; i++) {
        state->digest[i*4] = (BYTE)(state->DH[i] >> 24);
        state->digest[i*4+1] = (BYTE)(state->DH[i] >> 16);
        state->digest[i*4+2] = (BYTE)(state->DH[i] >> 8);
        state->digest[i*4+3] = (BYTE)(state->DH[i]);
    }
    //
    // Reset the digest
    //
    resetDigest(state);
}

/**
 * Process a word (4 bytes)
 * 
 * @param       buffer          Data buffer
 * @param       inOff           Buffer offset to start of word 
 * @param       state           Global state
 */
static void processWord(BYTE *buffer, int inOff, State *state) {
    state->DX[state->xOff++] = (((UINT)buffer[inOff]&0xff) << 24) |
                               (((UINT)buffer[inOff+1]&0xff) << 16) |
                               (((UINT)buffer[inOff+2]&0xff) << 8) |
                               ((UINT)buffer[inOff+3]&0xff);
    if (state->xOff == 16)
        processBlock(state);
}
    
/**
 * Process a 16-word block
 * 
 * @param       state           Global state
 */
static void processBlock(State *state) {
    int i, t;
    UINT x, r0, r1;
    for (t=16; t<64; t++) {
        x = state->DX[t-15];
        r0 = ((x >> 7) | (x << 25)) ^ ((x >> 18) | (x << 14)) ^ (x >> 3);
        x = state->DX[t-2];
        int r1 = ((x >> 17) | (x << 15)) ^ ((x >> 19) | (x << 13)) ^ (x >> 10);
        state->DX[t] = r1 + state->DX[t-7] + r0 + state->DX[t-16];
    }
    UINT a = state->DH[0];  UINT b = state->DH[1];
    UINT c = state->DH[2];  UINT d = state->DH[3];
    UINT e = state->DH[4];  UINT f = state->DH[5]; 
    UINT g = state->DH[6];  UINT h = state->DH[7];
    t = 0;     
    for(i=0; i<8; i ++) {
        h += Sum1(e) + Ch(e, f, g) + K[t] + state->DX[t];
        d += h;
        h += Sum0(a) + Maj(a, b, c);
        ++t;

        g += Sum1(d) + Ch(d, e, f) + K[t] + state->DX[t];
        c += g;
        g += Sum0(h) + Maj(h, a, b);
        ++t;

        f += Sum1(c) + Ch(c, d, e) + K[t] + state->DX[t];
        b += f;
        f += Sum0(g) + Maj(g, h, a);
        ++t;

        e += Sum1(b) + Ch(b, c, d) + K[t] + state->DX[t];
        a += e;
        e += Sum0(f) + Maj(f, g, h);
        ++t;

        d += Sum1(a) + Ch(a, b, c) + K[t] + state->DX[t];
        h += d;
        d += Sum0(e) + Maj(e, f, g);
        ++t;

        c += Sum1(h) + Ch(h, a, b) + K[t] + state->DX[t];
        g += c;
        c += Sum0(d) + Maj(d, e, f);
        ++t;

        b += Sum1(g) + Ch(g, h, a) + K[t] + state->DX[t];
        f += b;
        b += Sum0(c) + Maj(c, d, e);
        ++t;

        a += Sum1(f) + Ch(f, g, h) + K[t] + state->DX[t];
        e += a;
        a += Sum0(b) + Maj(b, c, d);
        ++t;
    }
    state->DH[0] += a;
    state->DH[1] += b;
    state->DH[2] += c;
    state->DH[3] += d;
    state->DH[4] += e;
    state->DH[5] += f;
    state->DH[6] += g;
    state->DH[7] += h;
    state->xOff = 0;
    memset(state->DX, 0, 16*sizeof(UINT));
}

static UINT Ch(UINT x, UINT y, UINT z) {
    return (x & y) ^ ((~x) & z);
}

static UINT Maj(UINT x, UINT y, UINT z) {
    return (x & y) ^ (x & z) ^ (y & z);
}

static UINT Sum0(UINT x) {
    return ((x >> 2) | (x << 30)) ^ ((x >> 13) | (x << 19)) ^ ((x >> 22) | (x << 10));
}

static UINT Sum1(UINT x) {
    return ((x >> 6) | (x << 26)) ^ ((x >> 11) | (x << 21)) ^ ((x >> 25) | (x << 7));
}

