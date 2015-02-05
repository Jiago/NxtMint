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
    __global ulong * restrict input;        /* Input data */
    __global ulong * restrict target;       /* Hash target */
    __global ulong * restrict solution;     /* Solution nonce */
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
#ifdef USE_ROTATE
#define rotateLeft(x, c) rotate((x), (ulong)(c))
#else
#define rotateLeft(x, c) (((x)<<c) | ((x)>>(64-c)))
#endif

/**
 * Perform a single hash
 */
static void hash(This *this) {
    ULONG nonce = this->input[0] + (ULONG)get_global_id(0) + ((ULONG)this->passId<<32);  
    ULONG state0 = nonce;
    ULONG state1 = this->input[1];
    ULONG state2 = this->input[2];  
    ULONG state3 = this->input[3];
    ULONG state4 = this->input[4];
    ULONG state5 = 1;   ULONG state6 = 0;   ULONG state7 = 0;   ULONG state8 = 0;   ULONG state9 = 0;
    ULONG state10 = 0;  ULONG state11 = 0;  ULONG state12 = 0;  ULONG state13 = 0;  ULONG state14 = 0;
    ULONG state15 = 0;  ULONG state16 = 0x8000000000000000L;           
    ULONG state17 = 0;  ULONG state18 = 0;  ULONG state19 = 0;  ULONG state20 = 0;  ULONG state21 = 0;
    ULONG state22 = 0;  ULONG state23 = 0;  ULONG state24 = 0;
    int i;
    for (i=0; i<25; i++) {
        ULONG t0 = state0 ^ state5 ^ state10 ^ state15 ^ state20;
        ULONG t1 = state1 ^ state6 ^ state11 ^ state16 ^ state21;
        ULONG t2 = state2 ^ state7 ^ state12 ^ state17 ^ state22;
        ULONG t3 = state3 ^ state8 ^ state13 ^ state18 ^ state23;
        ULONG t4 = state4 ^ state9 ^ state14 ^ state19 ^ state24;
        
        ULONG u0 = t0 ^ rotateLeft(t2, 1);
        ULONG w0 = rotateLeft(state6 ^ u0, 44);
        ULONG u1 = t1 ^ rotateLeft(t3, 1);
        ULONG w1 = rotateLeft(state7 ^ u1, 6);
        ULONG u2 = t2 ^ rotateLeft(t4, 1);
        ULONG w2 = rotateLeft(state8 ^ u2, 55);
        ULONG u3 = t3 ^ rotateLeft(t0, 1);
        ULONG w3 = rotateLeft(state9 ^ u3, 20);
        ULONG u4 = t4 ^ rotateLeft(t1, 1);
        
        t0 = state0 ^ u4;
        t1 = rotateLeft(state1 ^ u0, 1);
        t2 = rotateLeft(state2 ^ u1, 62);
        t3 = rotateLeft(state3 ^ u2, 28);
        t4 = rotateLeft(state4 ^ u3, 27);
        
        state2 = rotateLeft(state12 ^ u1, 43);
        state0 = t0 ^ (~w0 & state2) ^ (ULONG)constants[i];
        state3 = rotateLeft(state18 ^ u2, 21);
        state1 = w0 ^ (~state2 & state3);
        state4 = rotateLeft(state24 ^ u3, 14);
        state2 ^= (~state3 & state4);
        state3 ^= (~state4 & t0);
        state4 ^= (~t0 & w0);
        
        w0 = rotateLeft(state5 ^ u4, 36);
        state7 = rotateLeft(state10 ^ u4, 3);
        state5 = t3 ^ (~w3 & state7);
        state8 = rotateLeft(state16 ^ u0, 45);
        state6 = w3 ^ (~state7 & state8);
        state9 = rotateLeft(state22 ^ u1, 61);
        state7 ^= (~state8 & state9);
        state8 ^= (~state9 & t3);
        state9 ^= (~t3 & w3);
        
        w3 = state11 ^ u0;
        t3 = state14 ^ u3;
        state12 = rotateLeft(state13 ^ u2, 25);
        state10 = t1 ^ (~w1 & state12);
        state13 = rotateLeft(state19 ^ u3, 8);
        state11 = w1 ^ (~state12 & state13);
        state14 = rotateLeft(state20 ^ u4, 18);
        state12 ^= (~state13 & state14);
        state13 ^= (~state14 & t1);
        state14 ^= (~t1 & w1);
        
        t1 = state15 ^ u4;
        w1 = state17 ^ u1;
        state17 = rotateLeft(w3, 10);
        state15 = t4 ^ (~w0 & state17);
        state18 = rotateLeft(w1, 15);
        state16 = w0 ^ (~state17 & state18);
        state19 = rotateLeft(state23 ^ u2, 56);
        state17 ^= (~state18 & state19);
        state18 ^= (~state19 & t4);
        state19 ^= (~t4 & w0);
        
        w3 = state21 ^ u0;
        state22 = rotateLeft(t3, 39);
        state20 = t2 ^ (~w2 & state22);
        state23 = rotateLeft(t1, 41);
        state21 = w2 ^ (~state22 & state23);
        state24 = rotateLeft(w3, 2);
        state22 ^= (~state23 & state24);
        state23 ^= (~state24 & t2);
        state24 ^= (~t2 & w2);
    }    
    //
    // Check if we met the target
    //
    BOOLEAN isSolved = (state3<this->target[3] ? TRUE : state3>this->target[3] ? FALSE :
                        state2<this->target[2] ? TRUE : state2>this->target[2] ? FALSE :
                        state1<this->target[1] ? TRUE : state1>this->target[1] ? FALSE :
                        state0<this->target[0] ? TRUE : state0>this->target[0] ? FALSE : TRUE);
    //
    // Return the nonce if we have a solution
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
    this->input = (__global ulong *)kernelData;
    this->target = (__global ulong *)(kernelData+40);
    this->solution = (__global ulong *)(kernelData+72);
    this->passId = passId;
    //
    // Hash the input data
    //
    hash(this);
}
