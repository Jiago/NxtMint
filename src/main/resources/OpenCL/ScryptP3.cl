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
 * This is Pass 3: Perform the final HMAC calculations
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
    INT     xOff;
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

/*
 * SHA-256 constants 
 */
__constant UINT K[] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

/** Hash functions */
static void hash(This *this, State *state);

/** HMAC functions */
static void finishMac(BYTE *out, State *state);

/** SHA-256 functions */
static void updateDigest(BYTE *buffer, int inOff, int inLen, __global Digest *digest);
static void finishDigest(BYTE *out, __global Digest *digest);
static void resetDigest(__global Digest *digest);

/** SHA-256 helper functions */
static void processBlock(__global Digest *digest);
static void copyDigest(__global Digest *tgtDigest, __global Digest *srcDigest);

/** SHA-256 manipulation functions */
#ifdef USE_ROTATE
#define rotateLeft(x, c) rotate(x, (UINT)(c))
#else
#define rotateLeft(x, c) (((x)<<c) | ((x)>>(32-c)))
#endif
#define Ch(x, y, z) (((x) & (y)) ^ ((~(x)) & (z)))
#define Maj(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define Sum0(x) (rotateLeft(x, 30) ^ rotateLeft(x, 19) ^ rotateLeft(x, 10))
#define Sum1(x) (rotateLeft(x, 26) ^ rotateLeft(x, 21) ^ rotateLeft(x, 7))

/**
 * Do the hash
 */
static void hash(This *this, State *state) {
    BYTE    H[32];
    uint    *pX0 = (uint *)state->X0;
    uint    *pX1 = (uint *)state->X1;
    int     i;
    //
    // Finish up
    //
    uint xv;
    for (i=0; i<16; i++) {
        xv = pX0[i];
        state->B[i*4+0] = (BYTE)(xv);
        state->B[i*4+1] = (BYTE)(xv >> 8);
        state->B[i*4+2] = (BYTE)(xv >> 16);
        state->B[i*4+3] = (BYTE)(xv >> 24);
    }
    for (i=16; i<32; i++) {
        xv = pX1[i-16];
        state->B[i*4+0] = (BYTE)(xv);
        state->B[i*4+1] = (BYTE)(xv >> 8);
        state->B[i*4+2] = (BYTE)(xv >> 16);
        state->B[i*4+3] = (BYTE)(xv >> 24);
    }    
    state->B[128] = 0;
    state->B[129] = 0;
    state->B[130] = 0;
    state->B[131] = 1;
    updateDigest(state->B, 0, 132, state->digest);
    finishMac(H, state);
    //
    // See if we have a solution.  Note that the digest and the target
    // are treated as 32-byte unsigned numbers in little-endian format.
    //
    ULONG check[4];
    check[0] =  (ULONG)H[0]       | ((ULONG)H[1]<<8)   | ((ULONG)H[2]<<16)  | ((ULONG)H[3]<<24) |
               ((ULONG)H[4]<<32)  | ((ULONG)H[5]<<40)  | ((ULONG)H[6]<<48)  | ((ULONG)H[7]<<56);
    check[1] =  (ULONG)H[8]       | ((ULONG)H[9]<<8)   | ((ULONG)H[10]<<16) | ((ULONG)H[11]<<24) |
               ((ULONG)H[12]<<32) | ((ULONG)H[13]<<40) | ((ULONG)H[14]<<48) | ((ULONG)H[15]<<56);
    check[2] =  (ULONG)H[16]      | ((ULONG)H[17]<<8)  | ((ULONG)H[18]<<16) | ((ULONG)H[19]<<24) |
               ((ULONG)H[20]<<32) | ((ULONG)H[21]<<40) | ((ULONG)H[22]<<48) | ((ULONG)H[23]<<56);
    check[3] =  (ULONG)H[24]      | ((ULONG)H[25]<<8)  | ((ULONG)H[26]<<16) | ((ULONG)H[27]<<24) |
               ((ULONG)H[28]<<32) | ((ULONG)H[29]<<40) | ((ULONG)H[30]<<48) | ((ULONG)H[31]<<56);
    BOOLEAN isSolved = (check[3]<this->target[3] ? TRUE : check[3]>this->target[3] ? FALSE :
                        check[2]<this->target[2] ? TRUE : check[2]>this->target[2] ? FALSE :
                        check[1]<this->target[1] ? TRUE : check[1]>this->target[1] ? FALSE :
                        check[0]<this->target[0] ? TRUE : check[0]>this->target[0] ? FALSE : TRUE);
    //
    // Return the nonce if we have a solution
    //
    if (isSolved==TRUE)
        this->solution[0] = ((__global ulong *)this->input)[0] + 
                                (ULONG)get_global_id(0) + ((ULONG)this->passId<<32);
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
    finishDigest(out, state->digest);
    //
    // Hash the digest output using the saved opad digest
    //
    copyDigest(state->digest, state->opadDigest);
    updateDigest(out, 0, 32, state->digest);
    //
    // Finish the MAC digest
    //
    finishDigest(out, state->digest);
    //
    // Reset the MAC digest from the saved ipad digest
    //
    copyDigest(state->digest, state->ipadDigest);
}

/**
 * Reset the SHA-256 digest
 * 
 * @param       digest          SHA-256 digest
 */
static void resetDigest(__global Digest *digest) {
    digest->DH[0] = 0x6a09e667;
    digest->DH[1] = 0xbb67ae85;
    digest->DH[2] = 0x3c6ef372;
    digest->DH[3] = 0xa54ff53a;
    digest->DH[4] = 0x510e527f;
    digest->DH[5] = 0x9b05688c;
    digest->DH[6] = 0x1f83d9ab;
    digest->DH[7] = 0x5be0cd19;
    for (int i=0; i<64; i++)
        digest->DX[i] = 0;
    digest->xOff = 0;
    digest->xByteCount = 0;
}

/**
 * Copy a digest
 *
 * @param       tgtDigest       Target digest
 * @param       srcDigest       Source digest
 */
static void copyDigest(__global Digest *tgtDigest, __global Digest *srcDigest) {
    int i;
    for (i=0; i<8; i++)
        tgtDigest->DH[i] = srcDigest->DH[i];
    for (i=0; i<64; i++)
        tgtDigest->DX[i] = srcDigest->DX[i];
    tgtDigest->xOff = srcDigest->xOff;
    tgtDigest->xByteCount = srcDigest->xByteCount;
}

/**
 * Update the digest
 * 
 * @param       buffer          Data buffer
 * @param       inOff           Buffer offset to start of data
 * @param       inLen           Data length (must be a multiple of 4)
 * @param       digest          SHA-256 digest
 */
static void updateDigest(BYTE *buffer, int inOff, int inLen, __global Digest *digest) {
    INT len = inLen;
    INT offset = inOff;
    //
    // Process whole words.
    //
    while (len>0) {
        digest->DX[digest->xOff++] = ((UINT)buffer[offset] << 24) |
                                     ((UINT)buffer[offset+1] << 16) |
                                     ((UINT)buffer[offset+2] << 8) |
                                      (UINT)buffer[offset+3];
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
 * @param       out         32-byte buffer to receive the output
 * @param       digest      SHA-256 digest
 */
static void finishDigest(BYTE *out, __global Digest *digest) {
    LONG bitLength = (digest->xByteCount << 3);
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
    for (int i=0; i<8; i++) {
        out[i*4]   = (BYTE)(digest->DH[i] >> 24);
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
static void processBlock(__global Digest *digest) {
    INT i, t;
    UINT x, r0, r1;
    for (t=16; t<64; t++) {
        x = digest->DX[t-15];
        r0 = rotateLeft(x, 25) ^ rotateLeft(x, 14) ^ (x >> 3);
        x = digest->DX[t-2];
        r1 = rotateLeft(x, 15) ^ rotateLeft(x, 13) ^ (x >> 10);
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
    for (i=0; i<16; i++)
        digest->DX[i] = 0;
}

/**
 * Run the kernel
 */
__kernel void run(__global uchar * kernelData, 
                  __global uchar * stateBytes,
                           int     passId) {
    //
    // Pass kernel arguments to internal routines
    //
    This thisStruct;
    This* this = &thisStruct;
    this->input = kernelData+0;
    this->target = (__global ulong *)(kernelData+40);
    this->solution = (__global ulong *)(kernelData+72);
    this->passId = passId;
    //
    // Build the SCRYPT state
    //
    // B is allocated in private memory and is not preserved in global memory
    // X0 and X1 are allocated in private memory and are preserved in global memory
    //
    __global uchar *stateData = stateBytes+(get_global_id(0)*(3*296+8+2*64));
    State state;
    state.digest = (__global Digest *)(stateData+0);
    state.ipadDigest = (__global Digest *)(stateData+296);
    state.opadDigest = (__global Digest *)(stateData+2*296);
    BYTE B[132];
    state.B = B;
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
}
