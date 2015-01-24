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
package org.ScripterRon.NxtMint;

import com.amd.aparapi.Kernel;
import com.amd.aparapi.Range;

/**
 * Currency minting hash functions using the GPU
 */
public abstract class GpuFunction extends Kernel {
    
    /** GPU device */
    protected GpuDevice gpuDevice;
    
    /** Execution range */
    protected Range range;
    
    /** Execution count */
    protected int count;
    
    /**
     * Private constructor for use by subclasses
     * 
     * @param       gpuDevice       The GPU device
     */
    protected GpuFunction(GpuDevice gpuDevice) {
        super();
        this.gpuDevice = gpuDevice;
    }
    
    /**
     * Create a hash function for the specified algorithm
     * 
     * @param       algorithm       Hash algorithm
     * @param       gpuDevice       GPU device
     * @return                      Hash function
     */
    public static GpuFunction factory(int algorithm, GpuDevice gpuDevice) {
        GpuFunction hashFunction;
        switch (algorithm) {
            case 2:                 // SHA256
                hashFunction = new GpuSha256(gpuDevice);
                break;
            case 5:                 // SCRYPT
                hashFunction = new GpuScrypt(gpuDevice);
                break;
            case 25:                // KECCAK25
                hashFunction = new GpuKnv25(gpuDevice);
                break;
            default:
                throw new IllegalArgumentException("GPU hash algorithm "+algorithm+" is not supported");
        }
        return hashFunction;
    }
    
    /**
     * Check for a supported algorithm
     * 
     * @param       algorithm       Hash algorithm
     * @return                      TRUE if the algorithm is supported
     */
    public static boolean isSupported(int algorithm) {
        return (algorithm==2 || algorithm==5 || algorithm==25);
    }

    /**
     * Set the input data and the hash target
     * 
     * The input data is in the following format:
     *     Bytes 0-7:   Initial nonce (modified for each kernel instance)
     *     Bytes 8-15:  Currency identifier
     *     Bytes 16-23: Currency units
     *     Bytes 24-31: Minting counter
     *     Bytes 32-39: Account identifier
     * 
     * The hash target and hash digest are treated as unsigned 32-byte numbers in little-endian format.
     * The digest must be less than the target in order to be a solution.
     * 
     * @param       inputBytes      Bytes to be hashed (40 bytes)
     * @param       targetBytes     Hash target (32 bytes)
     */
    public abstract void setInput(byte[] inputBytes, byte[] targetBytes);

    /**
     * Check if we found a solution
     * 
     * @return                      TRUE if we found a solution
     */
    public abstract boolean isSolved();
    
    /**
     * Return the nonce used to solve the hash
     * 
     * @return                      Nonce
     */
    public abstract long getNonce();
    
    /**
     * Execute the kernel
     */
    public abstract void execute();
    
    /**
     * Return the execution count
     * 
     * @return                      Kernel execution count
     */
    public int getCount() {
        return count;
    }
}
