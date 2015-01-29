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
 * This is Pass 1: Perform the initial HMAC calculations
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
static void initMac(State *state);
static void finishMac(BYTE *out, State *state);

/** SHA-256 functions */
static void updateDigestByte(BYTE in, __global Digest *digest);
static void updateDigest(BYTE *buffer, int inOff, int inLen, __global Digest *digest);
static void finishDigest(BYTE *out, __global Digest *digest);
static void resetDigest(__global Digest *digest);

/** SHA-256 helper functions */
static void processBlock(__global Digest *digest);
static void processWord(BYTE *buffer, int inOff, __global Digest *digest);
static void copyDigest(__global Digest *tgtDigest, __global Digest *srcDigest);
static UINT Ch(UINT x, UINT y, UINT z);
static UINT Maj(UINT x, UINT y, UINT z);
static UINT Sum0(UINT x);
static UINT Sum1(UINT x);

/** Rotate left for integer value */
#define rotateLeft(x, c) (((x)<<c) | ((x)>>(32-c)))

/**
 * Do the hash
 */
static void hash(This *this, State *state) {
    BYTE    H[32];
    int     i, j;
    uint    *pX0 = (uint *)state->X0;
    uint    *pX1 = (uint *)state->X1;
    //
    // Initialize B from the input data
    //
    // The nonce is stored in the first 8 bytes of the input data
    //
    ULONG nonce = (ULONG)this->input[0] |
                  ((ULONG)this->input[1] << 8) |
                  ((ULONG)this->input[2] << 16) |
                  ((ULONG)this->input[3] << 24) |
                  ((ULONG)this->input[4] << 32) |
                  ((ULONG)this->input[5] << 40) |
                  ((ULONG)this->input[6] << 48) |
                  ((ULONG)this->input[7] << 56);
    nonce += (ULONG)get_global_id(0) + ((ULONG)this->passId<<32);
    state->B[0] = (BYTE)nonce;
    state->B[1] = (BYTE)(nonce >> 8);
    state->B[2] = (BYTE)(nonce >> 16);
    state->B[3] = (BYTE)(nonce >> 24);
    state->B[4] = (BYTE)(nonce >> 32);
    state->B[5] = (BYTE)(nonce >> 40);
    state->B[6] = (BYTE)(nonce >> 48);
    state->B[7] = (BYTE)(nonce >> 56);
    for (i=8; i<40; i++)
        state->B[i] = this->input[i];
    state->B[40] = 0;
    state->B[41] = 0;
    state->B[42] = 0;
    state->B[43] = 0;
    //
    // Initialize state in X0 and X1
    //
    initMac(state);
    for (i=0; i<4; i++) {
        state->B[43] = (BYTE)(i + 1);
        updateDigest(state->B, 0, 44, state->digest);
        finishMac(H, state);
        for (j=0; j<8; j++) {
            if (i < 2)
                pX0[i*8+j] = ((UINT)H[j*4+0]) | 
                             ((UINT)H[j*4+1] << 8) | 
                             ((UINT)H[j*4+2] << 16) | 
                             ((UINT)H[j*4+3] << 24);
            else
                pX1[(i-2)*8+j] = ((UINT)H[j*4+0]) | 
                             ((UINT)H[j*4+1] << 8) | 
                             ((UINT)H[j*4+2] << 16) | 
                             ((UINT)H[j*4+3] << 24);
        }
    }
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
    resetDigest(state->digest);
    //
    // Initialize the mac buffers
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
    copyDigest(state->opadDigest, state->digest);
    updateDigest(outputBuf, 0, 64, state->opadDigest);
    //
    // Save the input digest to improve HMAC reset performance
    //
    updateDigest(inputPad, 0, 64, state->digest);
    copyDigest(state->ipadDigest, state->digest);
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
    digest->xBuffOff = 0;
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
    for (i=0; i<4; i++)
        tgtDigest->xBuff[i] = srcDigest->xBuff[i];
    tgtDigest->xOff = srcDigest->xOff;
    tgtDigest->xBuffOff = srcDigest->xBuffOff;
    tgtDigest->xByteCount = srcDigest->xByteCount;
}

/**
 * Update the digest
 * 
 * @param       in              Data
 * @param       digest          SHA-256 digest
 */
static void updateDigestByte(BYTE in, __global Digest *digest) {
    digest->xBuff[digest->xBuffOff++] = in;
    if (digest->xBuffOff == 4) {
        digest->DX[digest->xOff++] = ((UINT)digest->xBuff[0] << 24) |
                                     ((UINT)digest->xBuff[1] << 16) |
                                     ((UINT)digest->xBuff[2] << 8) |
                                      (UINT)digest->xBuff[3];
        if (digest->xOff == 16)
            processBlock(digest);
        digest->xBuffOff = 0;
    }
    digest->xByteCount++;
}

/**
 * Update the digest
 * 
 * @param       buffer          Data buffer
 * @param       inOff           Buffer offset to start of data
 * @param       inLen           Data length
 * @param       digest          SHA-256 digest
 */
static void updateDigest(BYTE *buffer, int inOff, int inLen, __global Digest *digest) {
    INT len = inLen;
    INT offset = inOff;
    //
    // Fill the current word
    //
    while (digest->xBuffOff!=0 && len>0) {
        updateDigestByte(buffer[offset++], digest);
        len--;
    }
    //
    // Process whole words.
    //
    while (len>4) {
        processWord(buffer, offset, digest);
        offset += 4;
        len -= 4;
        digest->xByteCount += 4;
    }
    //
    // Load in the remainder.
    //
    while (len > 0) {
        updateDigestByte(buffer[offset++], digest);
        len--;
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
    updateDigestByte((BYTE)128, digest);
    while (digest->xBuffOff != 0)
        updateDigestByte((BYTE)0, digest);
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
 * Process a word (4 bytes)
 * 
 * @param       buffer          Data buffer
 * @param       inOff           Buffer offset to start of word 
 * @param       digest          SHA-256 digest
 */
static void processWord(BYTE *buffer, int inOff, __global Digest *digest) {
    digest->DX[digest->xOff++] = ((UINT)buffer[inOff] << 24) |
                                 ((UINT)buffer[inOff+1] << 16) |
                                 ((UINT)buffer[inOff+2] << 8) |
                                 (UINT)buffer[inOff+3];
    if (digest->xOff == 16)
        processBlock(digest);
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
        r0 = ((x >> 7) | (x << 25)) ^ ((x >> 18) | (x << 14)) ^ (x >> 3);
        x = digest->DX[t-2];
        r1 = ((x >> 17) | (x << 15)) ^ ((x >> 19) | (x << 13)) ^ (x >> 10);
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

/**
 * Run the kernel
 */
__kernel void run(__global uchar  *kernelData, 
                  __global uchar  *stateBytes,
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
    //
    // Build the SCRYPT state
    //
    // B is allocated in private memory and is not preserved in global memory
    // X0 and X1 are allocated in private memory and are preserved in global memory
    //
    __global uchar *stateData = stateBytes+(get_global_id(0)*(3*304+2*64));
    State state;
    state.digest = (__global Digest *)(stateData+0);
    state.ipadDigest = (__global Digest *)(stateData+304);
    state.opadDigest = (__global Digest *)(stateData+2*304);
    BYTE B[44];
    state.B = B;
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
