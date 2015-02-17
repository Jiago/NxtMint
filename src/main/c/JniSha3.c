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
 * SHA3-256 hash algorithm for Monetary System currencies
 *
 * Implementation of SHA-3 based on KeccakNISTInterface.c available
 * from http://keccak.noekeon.org/
 *
 * The code has been optimized for SHA3-256 with rate=1088 and capacity=512.
 * It will not work for any other values.
 */
#include <stdlib.h>
#include <stdio.h>
#include <memory.h>
#include "org_ScripterRon_NxtMint_HashSha3.h"

/** Addition Java<->C definitions */
typedef unsigned char      BYTE;
typedef int                INT;
typedef unsigned int       UINT;
typedef long long          LONG;
typedef unsigned long long ULONG;
typedef unsigned int       BOOLEAN;

#define TRUE  1
#define FALSE 0

/** Keccak round constants */
static const ULONG KeccakRoundConstants[] = {
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
static const UINT KeccakRhoOffsets[] = {
    0x00000000U, 0x00000001U, 0x0000003EU, 0x0000001CU, 0x0000001BU,
    0x00000024U, 0x0000002CU, 0x00000006U, 0x00000037U, 0x00000014U,
    0x00000003U, 0x0000000AU, 0x0000002BU, 0x00000019U, 0x00000027U,
    0x00000029U, 0x0000002DU, 0x0000000FU, 0x00000015U, 0x00000008U,
    0x00000012U, 0x00000002U, 0x0000003DU, 0x00000038U, 0x0000000EU
};

/** Hash function */
static BOOLEAN doHash(BYTE *input, BYTE *target);

/** Helper functions */
#define rotateLeft(x, c) (((x)<<(c)) ^ ((x)>>(64-(c))))

/**
 * Native SHA3-256 hash function
 *
 * @param       inputBytes          Input bytes
 * @param       targetBytes         Target bytes
 * @param       initialNonce        Initial nonce
 * @Param       count               Iteration count
 * @return                          Hash result
*/
JNIEXPORT jobject JNICALL Java_org_ScripterRon_NxtMint_HashSha3_JniHash(JNIEnv *envp, jclass this,
                                jobjectArray jniInputBytes, jobjectArray jniTargetBytes,
                                jlong initialNonce, jint count) {
    ULONG nonce = (ULONG)initialNonce;
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
    BYTE input[40];
    memcpy(input, inputBytes, 40);
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
    // Iterate until we find a solution or the maximum loop count is reached
    //
    // The nonce is stored in the first 8 bytes of the input data in
    // little-endian format.  We will increment it for each hash pass.
    //
    int hashCount = 0;
    BOOLEAN meetsTarget = FALSE;
    int loop;
    for (loop=0; loop<count && !meetsTarget; loop++) {
        nonce++;
        input[0] = (BYTE)(nonce);
        input[1] = (BYTE)(nonce>>8);
        input[2] = (BYTE)(nonce>>16);
        input[3] = (BYTE)(nonce>>24);
        input[4] = (BYTE)(nonce>>32);
        input[5] = (BYTE)(nonce>>40);
        input[6] = (BYTE)(nonce>>48);
        input[7] = (BYTE)(nonce>>56);
        meetsTarget = doHash(input, targetBytes);
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
    return result;
}

/**
 * Perform a single SHA3-256 hash.  The Keccak rate is 1088 and the
 * capacity is 512 (yielding a 32-byte digest).
 *
 * @param       input               Input data
 * @param       target              Target data
 * @return                          TRUE if the target was met
 */
static BOOLEAN doHash(BYTE *input, BYTE *target) {
    ULONG state[25];
    //
    // Initialize the state from the input data
    //
    int i;
    for (i=0; i<5; i++)
        state[i] =  ((ULONG)input[i*8+0]&0xff)      | (((ULONG)input[i*8+1]&0xff)<<8) |
                   (((ULONG)input[i*8+2]&0xff)<<16) | (((ULONG)input[i*8+3]&0xff)<<24) |
                   (((ULONG)input[i*8+4]&0xff)<<32) | (((ULONG)input[i*8+5]&0xff)<<40) |
                   (((ULONG)input[i*8+6]&0xff)<<48) | (((ULONG)input[i*8+7]&0xff)<<56);
    for (i=5; i<25; i++)
        state[i] = 0;
    state[5]  = 0x0000000000000001UL;
    state[16] = 0x8000000000000000UL;
    //
    // Perform the Keccak permutations
    //
    for (i=0; i<24; i++) {
        // theta(state))
        ULONG C[5];
        int x, y;
        for (x=0; x<5; x++) {
            C[x] = 0;
            for (y=0; y<5; y++)
                C[x] ^= state[x+5*y];
        }
        for (x=0; x<5; x++) {
            ULONG dX = rotateLeft(C[(x+1)%5], 1) ^ C[(x+4)%5];
            for (y=0; y<5; y++)
                state[x+5*y] ^= dX;
        }
        // rho(state)
        for (x=0; x<5; x++) {
            for (y=0; y<5; y++) {
                int index = x+5*y;
                state[index] = (KeccakRhoOffsets[index]!=0 ?
                    rotateLeft(state[index], KeccakRhoOffsets[index]) : state[index]);
            }
        }
        // pi(state)
        ULONG tempA[25];
        memcpy(tempA, state, 25*sizeof(ULONG));
        for (x=0; x<5; x++) {
            for (y=0; y<5; y++)
                state[y+5 * ((2*x + 3*y) % 5)] = tempA[x+5*y];
        }
        // chi(state)
        ULONG chiC[5];
        for (y=0; y<5; y++) {
            for (x=0; x<5; x++)
                chiC[x] = state[x+5*y] ^ ((~state[(((x+1)%5)+5*y)]) & state[(((x+2)%5)+5*y)]);
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
        if (state[i] < ((ULONG *)target)[i]) {
            keepChecking = FALSE;
        } else if (state[i] > ((ULONG *)target)[i]) {
            isSolved = FALSE;
            keepChecking = FALSE;
        }
    }
    return isSolved;
}
