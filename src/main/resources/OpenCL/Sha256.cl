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
 * SHA-256 constants
 */ 
__constant uint k[] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
        0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
        0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
        0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

/** 
 * Kernel arguments 
 */
typedef struct This_s {
    __global uchar  *input;             /* Input data */
    __global uchar  *target;            /* Hash target */
    __global uint   *done;              /* Solution found indicator */
    __global uchar  *solution;          /* Solution nonce */
             int    passId;             /* Pass identifier */
} This;

/**
 * Rotate an integer left the requested number of bits
 */
#define rotateLeft(v, c) ((v<<c) | (v>>(32-c)))

/**
 * Do the hash
 */
static void hash(This *this) {
    uint A = 1779033703;
    uint B = -1150833019;
    uint C = 1013904242;
    uint D = -1521486534;
    uint E = 1359893119;
    uint F = -1694144372;
    uint G = 528734635;
    uint H = 1541459225;
    uint w0=0, w1=0, w2=0, w3=0, w4=0, w5=0, w6=0, w7=0;
    uint w8=0, w9=0, w10=0, w11=0, w12=0, w13=0, w14=0, w15=0, w16=0;
    uint T, T2;
    ulong n;
    //
    // Transform the data
    //
    // We will modify the nonce (first 8 bytes of the input data) for each execution instance
    // based on the global ID
    //
    int offset = 0;
    int r;
    for (r=0; r<64; r++) {
        if (r < 16){
            w16 = ((uint)this->input[offset] << 24) | 
                  ((uint)this->input[offset+1] << 16) | 
                  ((uint)this->input[offset+2] << 8) | 
                  ((uint)this->input[offset+3]);
            offset += 4;
            if (r == 0) {
                n = (ulong)w16 << 32;
            } else if (r == 1) {
                w16 += (ulong)get_global_id(0) + ((ulong)this->passId<<32);
                n |= (ulong)w16;
            }
        } else {
            w16 = ((rotateLeft(w14, 15) ^ rotateLeft(w14, 13) ^ (w14 >> 10)) + w9 + 
                    (rotateLeft(w1, 25) ^ rotateLeft(w1, 14) ^ (w1 >> 3)) + w0);
        }
        T = (H + (rotateLeft(E, 26) ^ rotateLeft(E, 21) ^ rotateLeft(E, 7)) +
                        ((E & F) ^ (~E & G)) + k[r] + w16);
        T2 = ((rotateLeft(A, 30) ^ rotateLeft(A, 19) ^ rotateLeft(A, 10)) + 
                        ((A & B) ^ (A & C) ^ (B & C)));
        w0 = w1; w1 = w2; w2 = w3; w3 = w4; w4 = w5; w5 = w6; w6 = w7; w7 = w8; w8 = w9;
        w9 = w10; w10 = w11; w11 = w12; w12 = w13; w13 = w14; w14 = w15; w15 = w16;
        H = G; G = F; F = E; E = D + T;
        D = C; C = B; B = A; A = T + T2;
    }
    //
    // Finish the digest
    //
    A += 1779033703;
    B += -1150833019;
    C += 1013904242;
    D += -1521486534;
    E += 1359893119;
    F += -1694144372;
    G += 528734635;
    H += 1541459225;
    //
    // Save the digest if it satisfies the target.  Note that the digest and the target
    // are treated as 32-byte unsigned numbers in little-endian format.
    //
    char keepChecking = 1;
    char isSolved = 1;
    uint check;
    int i;
    for (i=7; i>=0 && keepChecking!=0; i--) {
      switch (i) {
         case 0:
            check = A;
            break;
         case 1:
            check = B;
            break;
         case 2:
            check = C;
            break;
         case 3:
            check = D;
            break;
         case 4:
            check = E;
            break;
         case 5:
            check = F;
            break;
         case 6:
            check = G;
            break;
         case 7:
            check = H;
            break;
      }
      for (int j=0; j<4 && keepChecking!=0; j++){
         int b0 = (check>>(j*8)) & 255;
         int b1 = this->target[((i*4) + (3-j))] & 255;
         if (b0 < b1){
            keepChecking = 0;
         } else if (b0 > b1) {
            isSolved = 0;
            keepChecking = 0;
         }
      }
    }
    if (isSolved!=0 && atomic_cmpxchg(this->done, 0, 1)==0) {
      //
      // Save the nonce in little-endian format
      //
      for (i=0; i<8; i++)
        this->solution[i] = (uchar)(n>>(56-(i*8)));
   }
   return;
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
    this->target = kernelData+64;
    this->solution = kernelData+96;
    this->done = (__global uint *)(kernelData+104);
    this->passId = passId;
    //
    // Hash the input data if we haven't found a solution yet
    //
    if (this->done[0]==0) {
      hash(this);
    }
    return;
}

