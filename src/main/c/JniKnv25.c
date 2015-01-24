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
#include <stdlib.h>
#include <stdio.h>
#include "org_ScripterRon_NxtMint_HashKnv25.h"

/** Addition Java<->C definitions */
typedef unsigned char      BYTE;
typedef long long          LONG;
typedef unsigned long long ULONG;
typedef unsigned char      BOOLEAN;

#define TRUE  1
#define FALSE 0
        
/** Keccak25 algorithm constants */
static const LONG constants[] = {
    1LL,                     32898LL,               -9223372036854742902LL, -9223372034707259392LL, 
    32907LL,                 2147483649LL,          -9223372034707259263LL, -9223372036854743031LL, 
    138LL,                   136LL,                  2147516425LL,           2147483658LL, 
    2147516555LL,           -9223372036854775669LL, -9223372036854742903LL, -9223372036854743037LL, 
    -9223372036854743038LL, -9223372036854775680LL,  32778LL,               -9223372034707292150LL,
    -9223372034707259263LL, -9223372036854742912LL,  2147483649LL,          -9223372034707259384LL,  
    1LL
};

/** Hash function */
static BOOLEAN doHash(ULONG *input, BYTE *target);

/**
 * Native Keccak25 hash function
 *
 * @param       inputBytes      Input bytes
 * @param       targetBytes     Target bytes
 * @param       initialNonce    Initial nonce
 * @Param       count           Iteration count
 * @return                      Hash result
*/
JNIEXPORT jobject JNICALL Java_org_ScripterRon_NxtMint_HashKnv25_JniHash(JNIEnv *envp, jclass this, 
                                jobjectArray jniInputBytes, jobjectArray jniTargetBytes,
                                jlong initialNonce, jint count) {     
    ULONG input[5];
    ULONG nonce = (ULONG)initialNonce;
    int i, offset, loop;
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
    // Convert the input data to an array of unsigned longs
    //
    for (i=0, offset=0; i<5; i++, offset+=8) {
        input[i] = ((ULONG)inputBytes[offset]&0xFF) | 
                       (((ULONG)inputBytes[offset+1]&0xFF) << 8) | 
                       (((ULONG)inputBytes[offset+2]&0xFF) << 16) | 
                       (((ULONG)inputBytes[offset+3]&0xFF) << 24) | 
                       (((ULONG)inputBytes[offset+4]&0xFF) << 32) | 
                       (((ULONG)inputBytes[offset+5]&0xFF) << 40) | 
                       (((ULONG)inputBytes[offset+6]&0xFF) << 48) | 
                       (((ULONG)inputBytes[offset+7]&0xFF) << 56);
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
        input[0] = nonce;
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
 * Perform a single hash
 * 
 * @param       input           Input data
 * @param       target          Target
 * @return                      TRUE if the target was met
 */
static BOOLEAN doHash(ULONG *input, BYTE *target) {
    ULONG state0, state1, state2, state3, state4, state5, state6, state7, 
          state8, state9, state10, state11, state12, state13, state14, state15, 
          state16, state17, state18, state19, state20, state21, state22, state23, state24;
    int i, j;
    state0 = input[0];  state1 = input[1];
    state2 = input[2];  state3 = input[3];
    state4 = input[4];
    state5 = 1;   state6 = 0;   state7 = 0;   state8 = 0;   state9 = 0;
    state10 = 0;  state11 = 0;  state12 = 0;  state13 = 0;  state14 = 0;
    state15 = 0;  state16 = 0x8000000000000000LL;           
    state17 = 0;  state18 = 0;  state19 = 0;  state20 = 0;  state21 = 0;
    state22 = 0;  state23 = 0;  state24 = 0;
    for (i=0; i<25;) {
        ULONG t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19;            
        t1 = state0 ^ state5 ^ state10 ^ state15 ^ state20;
        t2 = state2 ^ state7 ^ state12 ^ state17 ^ state22;
        t3 = t1 ^ ((t2 << 1) | (t2 >> (64-1)));
        t12 = state1 ^ t3;

        t4 = state1 ^ state6 ^ state11 ^ state16 ^ state21;
        t5 = state3 ^ state8 ^ state13 ^ state18 ^ state23;
        t6 = t4 ^ ((t5 << 1) | (t5 >> (64-1)));
        t13 = state2 ^ t6;

        t7 = state4 ^ state9 ^ state14 ^ state19 ^ state24;
        t8 = ((t4 << 1) | (t4 >> (64-1))) ^ t7;
        t16 = state6 ^ t3;
        t16 = ((t16 << 44) | (t16 >> (64-44)));
        state2 = state12 ^ t6;
        state2 = (state2 << 43) | (state2 >> (64-43));
        t9 = state0 ^ t8;
        state0 = t9 ^ (~t16 & state2) ^ (ULONG)constants[i++];

        t10 = ((t7 << 1) | (t7 >> (64-1))) ^ t2;
        t14 = state3 ^ t10;
        state3 = state18 ^ t10;
        state3 = (state3 << 21) | (state3 >> (64-21));
        state1 = t16 ^ (~state2 & state3);

        t11 = ((t1 << 1) | (t1 >> (64-1))) ^ t5;
        t15 = state4 ^ t11;
        state4 = state24 ^ t11;
        state4 = (state4 << 14) | (state4 >> (64-14));
        state2 ^= ((~state3) & state4);

        state3 ^= ((~state4) & t9);
        state4 ^= ((~t9) & t16);
        t16 = state5 ^ t8;
        t17 = state7 ^ t6;
        t19 = state9 ^ t11;
        t19 = (t19 << 20) | (t19 >> (64-20));
        t14 = (t14 << 28) | (t14 >> (64-28));
        state7 = state10 ^ t8;
        state7 = (state7 << 3) | (state7 >> (64-3));
        state5 = t14 ^ (~t19 & state7);

        t18 = state8 ^ t10;
        state8 = state16 ^ t3;
        state8 = (state8 << 45) | (state8 >> (64-45));
        state6 = t19 ^ (~state7 & state8);

        state9 = state22 ^ t6;
        state9 = (state9 << 61) | (state9 >> (64-61));
        state7 ^= ((~state8) & state9);

        state8 ^= ((~state9) & t14);
        state9 ^= ((~t14) & t19);
        t19 = state11 ^ t3;

        t12 = (t12 << 1) | (t12 >> (64-1));
        t17 = (t17 << 6) | (t17 >> (64-6));
        state12 = state13 ^ t10;
        state12 = (state12 << 25) | (state12 >> (64-25));
        state10 = t12 ^ (~t17 & state12);

        state13 = state19 ^ t11;
        state13 = (state13 << 8) | (state13 >> (64-8));
        state11 = t17 ^ (~state12 & state13);

        t14 = state14 ^ t11;
        state14 = state20 ^ t8;
        state14 = (state14 << 18) | (state14 >> (64-18));
        state12 ^= ((~state13) & state14);

        state13 ^= ((~state14) & t12);
        state14 ^= ((~t12) & t17);
        t12 = state15 ^ t8;
        t17 = state17 ^ t6;

        t16 = (t16 << 36) | (t16 >> (64-36));
        t15 = (t15 << 27) | (t15 >> (64-27));
        state17 = (t19 << 10) | (t19 >> (64-10));
        state15 = t15 ^ (~t16 & state17);

        state18 = (t17 << 15) | (t17 >> (64-15));
        state16 = t16 ^ (~state17 & state18);

        state19 = state23 ^ t10;
        state19 = (state19 << 56) | (state19 >> (64-56));
        state17 ^= ((~state18) & state19);

        state18 ^= ((~state19) & t15);
        state19 ^= ((~t15) & t16);
        t19 = state21 ^ t3;

        t13 = (t13 << 62) | (t13 >> (64-62));
        t18 = (t18 << 55) | (t18 >> (64-55));
        state22 = (t14 << 39) | (t14 >> (64-39));
        state20 = t13 ^ (~t18 & state22);

        state23 = (t12 << 41) | (t12 >> (64-41));
        state21 = t18 ^ (~state22 & state23);

        state24 = (t19 << 2) | (t19 >> (64-2));
        state22 ^= ((~state23) & state24);
        state23 ^= ((~state24) & t13);
        state24 ^= ((~t13) & t18);
    }    
    //
    // Check if we met the target
    //
    BOOLEAN isSolved = TRUE;
    BOOLEAN keepChecking = TRUE;
    ULONG check;
    for (i=3; i>=0 && keepChecking; i--) {
        if (i == 0)
            check = state0;
        else if (i == 1)
            check = state1;
        else if (i == 2)
            check = state2;
        else
            check = state3;
        for (j=7; j>=0 && keepChecking; j--) {
            int b0 = (int)(check>>(j*8))&0xff;
            int b1 = (int)(target[i*8+j])&0xff;
            if (b0 < b1) {
                keepChecking = FALSE;
            } if (b0 > b1) {
                isSolved = FALSE;
                keepChecking = FALSE;
            }
        }
    }
    return isSolved;
}
