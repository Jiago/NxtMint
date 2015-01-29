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

import org.jocl.cl_device_id;
import org.jocl.cl_platform_id;

/**
 * Define a GPU device to use for minting
 */
public class GpuDevice {
    
    /** GPU identifier */
    private final int gpuId;
    
    /** Associated OpenCL platform */
    private final cl_platform_id clPlatform;
    
    /** Associated OpenCL device */
    private final cl_device_id clDevice;
    
    /** Number of compute units */
    private final int computeUnits;
    
    /** Global memory size */
    private final long globalMemorySize;
    
    /** Local memory size */
    private final long localMemorySize;
    
    /** Maximum work group size */
    private final int maxWorkGroupSize;
    
    /** Work group size */
    private int workGroupSize;
    
    /** Work group count */
    private int workGroupCount;
    
    /**
     * Create the GPU device
     * 
     * @param       gpuId               GPU identifier
     * @param       clPlatform          Associate OpenCL platform
     * @param       clDevice            Associated OpenCL device
     * @param       computeUnits        Number of compute units
     * @param       globalMemorySize    Global memory size
     * @param       localMemorySize     Local memory size
     * @param       maxWorkGroupSize    Maximum work group size
     */
    public GpuDevice(int gpuId, cl_platform_id clPlatform, cl_device_id clDevice, int computeUnits,
                                        long globalMemorySize, long localMemorySize, int maxWorkGroupSize) {
        this.gpuId = gpuId;
        this.clPlatform = clPlatform;
        this.clDevice = clDevice;
        this.computeUnits = computeUnits;
        this.globalMemorySize = globalMemorySize;
        this.localMemorySize = localMemorySize;
        this.maxWorkGroupSize = maxWorkGroupSize;
        this.workGroupSize = Math.min(256, maxWorkGroupSize);
        this.workGroupCount = computeUnits;
    }
    
    /**
     * Return the GPU identifier
     * 
     * @return                          GPU identifier
     */
    public int getGpuId() {
        return gpuId;
    }
    
    /**
     * Return the OpenCL platform
     * 
     * @return                          OpenCL platform
     */
    public cl_platform_id getPlatform() {
        return clPlatform;
    }
    
    /**
     * Return the OpenCL device
     * 
     * @return                          OpenCL device
     */
    public cl_device_id getDevice() {
        return clDevice;
    }
    
    /**
     * Return the number of compute units
     * 
     * @return                          Number of compute units
     */
    public int getComputeUnits() {
        return computeUnits;
    }
    
    /**
     * Return the global memory size
     * 
     * @return                          Global memory size
     */
    public long getGlobalMemorySize() {
        return globalMemorySize;
    }
    
    /**
     * Return the local memory size
     * 
     * @return                          Local memory size
     */
    public long getLocalMemorySize() {
        return localMemorySize;
    }
    
    /**
     * Return the maximum work group size
     * 
     * @return                          Maximum work group size
     */
    public int getMaxWorkGroupSize() {
        return maxWorkGroupSize;
    }
    
    /**
     * Return the work group size
     * 
     * @return                          Work group size
     */
    public int getWorkGroupSize() {
        return workGroupSize;
    }
    
    /**
     * Set the work group size
     * 
     * @param       size                Work group size
     */
    public void setWorkGroupSize(int size) {
        workGroupSize = Math.min(size, maxWorkGroupSize);
    }
    
    /**
     * Return the work group count
     * 
     * @return                          Work group count
     */
    public int getWorkGroupCount() {
        return workGroupCount;
    }
    
    /**
     * Set the work group count
     * 
     * @param       count               Work group count
     */
    public void setWorkGroupCount(int count) {
        workGroupCount = count;
    }
}
