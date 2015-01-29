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
import static org.ScripterRon.NxtMint.Main.log;

import org.jocl.CL;
import org.jocl.CLException;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;

import java.io.IOException;
import java.util.Arrays;

/**
 * SCRYPT hash algorithm for Monetary System currencies
 */
public class GpuScrypt extends GpuFunction {
    
    /** Pass identifier */
    private final int[] passId = new int[1];
    
    /** Kernel global size */
    private final long[] kernelGlobalSize = new long[1];
    
    /** Kernel local size */
    private final long[] kernelLocalSize = new long[1];
    
    /** Kernel data offset */
    private final int inputOffset = 0;
    private final int targetOffset = 40;
    private final int solutionOffset = 72;
    private final int doneOffset = 80; 
    
    /** Kernel data buffer */
    private final byte[] kernelData = new byte[40+32+8+4];    

    /**
     * Create the GPU hash function
     * 
     * @param       gpuDevice       GPU device
     * @throws      CLException     OpenCL error occurred
     * @throws      IOException     Unable to read OpenCL program source
     */
    public GpuScrypt(GpuDevice gpuDevice) throws CLException, IOException {
        super(gpuDevice, "ScryptP1.cl", "ScryptP2.cl", "ScryptP3.cl");
        //
        // Calculate the local and global sizes
        //
        // We need to allocate global memory for the Scrypt V array.  Each work item
        // requires 128KB for this array.  We will need to adjust the global size
        // if the required memory exceeds the maximum allocation size for the device.
        //
        if (Main.gpuIntensity > 1024) {
            log.warn("GPU intensity may not exceed 1024 - setting to maximum");
            count = 1024*1024;
        } else {
            count = Main.gpuIntensity*1024;
        }
        localSize = gpuDevice.getWorkGroupSize();
        if (localSize%preferredLocalSize != 0)
            log.info(String.format("GPU %d: Preferred work group size multiple is %d",
                                   gpuDevice.getGpuId(), preferredLocalSize));
        if (gpuDevice.getWorkGroupCount() != 0)
            globalSize = gpuDevice.getWorkGroupCount()*localSize;
        else
            globalSize = (count/localSize)*localSize;
        if (count < globalSize)
            globalSize = count;
        long allocationSize = 4*32*1024;        // V is INT[4*1024] for each work item
        if (globalSize*allocationSize > maxAllocationSize) {
            log.warn(String.format("GPU %d: Maximum allocation size of %,dKB exceeded - reducing global size",
                                   gpuDevice.getGpuId(), maxAllocationSize/1024));
            globalSize = (int)(maxAllocationSize/allocationSize);
        }
        count = ((count+globalSize-1)/globalSize)*globalSize;
        passes = count/globalSize;
        kernelGlobalSize[0] = globalSize;
        kernelLocalSize[0] = localSize;
        log.debug(String.format("GPU %d: Local size %d, Global size %d, Passes %d", 
                                gpuDevice.getGpuId(), localSize, globalSize, passes));
        //
        // Allocate the memory objects for the kernel data
        //
        memObjects = new cl_mem[3];
        memObjects[0] = CL.clCreateBuffer(context, CL.CL_MEM_READ_WRITE,
                                          Sizeof.cl_uchar*kernelData.length, null, null);
        memObjects[1] = CL.clCreateBuffer(context, CL.CL_MEM_READ_WRITE,
                                          Sizeof.cl_uchar*(3*304+2*64), null, null);
        memObjects[2] = CL.clCreateBuffer(context, CL.CL_MEM_READ_WRITE,
                                          Sizeof.cl_uint*32*1024*globalSize, null, null);
        //
        // Set the Phase 1 kernel arguments
        //
        CL.clSetKernelArg(kernels[0], 0, Sizeof.cl_mem, Pointer.to(memObjects[0]));
        CL.clSetKernelArg(kernels[0], 1, Sizeof.cl_mem, Pointer.to(memObjects[1]));
        //
        // Set the Phase 2 kernel arguments
        //
        CL.clSetKernelArg(kernels[1], 0, Sizeof.cl_mem, Pointer.to(memObjects[0]));
        CL.clSetKernelArg(kernels[1], 1, Sizeof.cl_mem, Pointer.to(memObjects[1]));
        CL.clSetKernelArg(kernels[1], 2, Sizeof.cl_mem, Pointer.to(memObjects[2]));
        //
        // Set the Phase 3 kernel arguments
        //
        CL.clSetKernelArg(kernels[2], 0, Sizeof.cl_mem, Pointer.to(memObjects[0]));
        CL.clSetKernelArg(kernels[2], 1, Sizeof.cl_mem, Pointer.to(memObjects[1]));
        //
        // OpenCL resources have been allocated
        //
        resourcesAllocated = true;
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
    @Override
    public void setInput(byte[] inputBytes, byte[] targetBytes) {
        if (inputBytes.length != 40)
            throw new IllegalArgumentException("Input data length must be 40 bytes");
        if (targetBytes.length != 32)
            throw new IllegalArgumentException("Target data length must be 32 bytes");
        //
        // Set the input data
        //
        System.arraycopy(inputBytes, 0, kernelData, inputOffset, 40);
        //
        // Set the hash target
        //
        System.arraycopy(targetBytes, 0, kernelData, targetOffset, 32);
        //
        // Indicate no solution has been found
        //
        Arrays.fill(kernelData, doneOffset, doneOffset+4, (byte)0);        
    }
    
    /**
     * Execute the kernel
     * 
     * @return                      TRUE if the kernel was executed
     */
    @Override
    public boolean execute() {
        boolean executed = false;
        try {
            //
            // Write the kernel data to the GPU
            //
            CL.clEnqueueWriteBuffer(commandQueue, memObjects[0], CL.CL_TRUE, 0,
                                    Sizeof.cl_uchar*kernelData.length, Pointer.to(kernelData),
                                    0, null, null);
            //
            // Execute the kernel, updating the passId for each pass.  The kernels
            // will be executed sequentially, so the value chosen for global size 
            // should be large enough to keep the GPU compute units busy.  All work
            // items in the same work group will share local memory, which implies
            // that they will all be executed by the same compute unit.
            //
            for (int i=0; i<passes; i++) {
                passId[0] = i;
                CL.clSetKernelArg(kernels[0], 2, Sizeof.cl_int, Pointer.to(passId));
                CL.clSetKernelArg(kernels[1], 3, Sizeof.cl_int, Pointer.to(passId));
                CL.clSetKernelArg(kernels[2], 2, Sizeof.cl_int, Pointer.to(passId));
                CL.clEnqueueNDRangeKernel(commandQueue, kernels[0], 1, null, 
                                          kernelGlobalSize, kernelLocalSize, 
                                          0, null, null);
                CL.clEnqueueNDRangeKernel(commandQueue, kernels[1], 1, null, 
                                          kernelGlobalSize, kernelLocalSize, 
                                          0, null, null);
                CL.clEnqueueNDRangeKernel(commandQueue, kernels[2], 1, null, 
                                          kernelGlobalSize, kernelLocalSize, 
                                          0, null, null);
                CL.clEnqueueReadBuffer(commandQueue, memObjects[0], CL.CL_TRUE, 0,
                                       Sizeof.cl_uchar*kernelData.length, Pointer.to(kernelData),
                                       0, null, null);
                meetsTarget = (kernelData[doneOffset]!=0   || kernelData[doneOffset+1]!=0 ||
                               kernelData[doneOffset+2]!=0 || kernelData[doneOffset+3]!=0);
                if (meetsTarget)
                    break;
            }
            if (meetsTarget)
                nonce = ((long)kernelData[solutionOffset]&255) |
                        (((long)kernelData[solutionOffset+1]&255) << 8) |
                        (((long)kernelData[solutionOffset+2]&255) << 16) |
                        (((long)kernelData[solutionOffset+3]&255) << 24) |
                        (((long)kernelData[solutionOffset+4]&255) << 32) |
                        (((long)kernelData[solutionOffset+5]&255) << 40) |
                        (((long)kernelData[solutionOffset+6]&255) << 48) |
                        (((long)kernelData[solutionOffset+7]&255) << 56);
            executed = true;
        } catch (CLException exc) {
            log.error("Unable to execute OpenCL kernel", exc);
        }
        return executed;
    }
    
    /**
     * Release OpenCL resources when we are finished using the GPU
     */
    @Override
    public void dispose() {
        if (resourcesAllocated) {
            resourcesAllocated = false;
            for (cl_mem memObject : memObjects)
                CL.clReleaseMemObject(memObject);
            for (cl_kernel kernel : kernels)
                CL.clReleaseKernel(kernel);
            CL.clReleaseCommandQueue(commandQueue);
            CL.clReleaseContext(context);        
        }
    }    
}
