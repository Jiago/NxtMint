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

import org.jocl.CL;
import org.jocl.CLException;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_program;

import java.io.InputStream;
import java.io.IOException;

/**
 * Currency minting hash functions using the GPU
 */
public abstract class GpuFunction {
    
    /** GPU device */
    protected GpuDevice gpuDevice;
    
    /** Execution count */
    protected int count;
    
    /** Execution passes */
    protected int passes;
    
    /** Global size */
    protected int globalSize;
    
    /** Local size */
    protected int localSize;
    
    /** Maximum memory allocation size */
    protected long maxAllocationSize;
    
    /** Preferred local size */
    protected int preferredLocalSize;
    
    /** Nonce */
    protected long nonce;
    
    /** Target is met */
    protected boolean meetsTarget;
    
    /** OpenCL context */
    protected cl_context context;
    
    /** OpenCL command queue */
    protected cl_command_queue commandQueue;
    
    /** OpenCL memory objects */
    protected cl_mem[] memObjects;
    
    /** OpenCL kernel */
    protected cl_kernel[] kernels;
    
    /** OpenCL resources allocated */
    protected boolean resourcesAllocated;
    
    /**
     * Private constructor for use by subclasses
     * 
     * @param       gpuDevice       The GPU device
     * @param       pgmNames        OpenCL program names
     * @throws      CLException     OpenCL error occurred
     * @throws      IOException     Unable to read OpenCL program source
     */
    protected GpuFunction(GpuDevice gpuDevice, String... pgmNames) throws CLException, IOException {
        this.gpuDevice = gpuDevice;
        //
        // Create the OpenCL context and associated command queue
        //
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL.CL_CONTEXT_PLATFORM, gpuDevice.getPlatform());
        context = CL.clCreateContext(contextProperties, 1, new cl_device_id[]{gpuDevice.getDevice()},
                                     null, null, null);
        commandQueue = CL.clCreateCommandQueue(context, gpuDevice.getDevice(), 0, null);
        kernels = new cl_kernel[pgmNames.length];
        //
        // Build each kernel
        //
        for (int k=0; k<pgmNames.length; k++) {
            String pgmName = pgmNames[k];
            //
            // Read the OpenCL program source from the application jar
            //
            String pgmSource;
            try (InputStream classStream = getClass().getClassLoader()
                                                     .getResourceAsStream("OpenCL/"+pgmName)) {
                if (classStream == null)
                    throw new IOException(String.format("OpenCL program '%s' not found", pgmName));
                int pgmLength = classStream.available();
                byte[] pgmBuffer = new byte[pgmLength];
                int byteCount = classStream.read(pgmBuffer);
                if (byteCount != pgmLength)
                    throw new IOException(String.format("OpenCL program '%s' truncated", pgmName));
                pgmSource = new String(pgmBuffer, "UTF-8");
            }
            //
            // Compile and build the CL program
            //
            cl_program program = CL.clCreateProgramWithSource(context, 1, new String[]{pgmSource}, null, null);
            CL.clBuildProgram(program, 0, null, null, null, null);
            //
            // Create the kernel
            //
            kernels[k] = CL.clCreateKernel(program, "run", null);
            CL.clReleaseProgram(program);
        }
        //
        // Get device information for use by subclasses
        //
        maxAllocationSize = OpenCL.getLong(gpuDevice.getDevice(), CL.CL_DEVICE_MAX_MEM_ALLOC_SIZE);
        preferredLocalSize = (int)OpenCL.getSize(kernels[0], gpuDevice.getDevice(),
                                                 CL.CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE);
    }
    
    /**
     * Create a hash function for the specified algorithm
     * 
     * @param       algorithm       Hash algorithm
     * @param       gpuDevice       GPU device
     * @return                      Hash function
     * @throws      CLException     OpenCL error occurred
     * @throws      IOException     Unable to read OpenCL program
     */
    public static GpuFunction factory(int algorithm, GpuDevice gpuDevice) throws CLException, IOException {
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
     * Check if we found a solution
     * 
     * @return                      TRUE if we found a solution
     */
    public boolean isSolved() {
        return meetsTarget;
    }
    
    /**
     * Return the nonce used to solve the hash
     * 
     * @return                      Nonce
     */
    public long getNonce() {
        return nonce;
    }
    
    /**
     * Return the execution count
     * 
     * @return                      Kernel execution count
     */
    public int getCount() {
        return count;
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
     * Execute the kernel
     * 
     * @return                      TRUE if the kernel was executed
     */
    public abstract boolean execute();

    /**
     * Release OpenCL resources
     */
    public void dispose() {
    }
}
