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

import com.amd.aparapi.device.OpenCLDevice;

/**
 * Define a GPU device to use for minting
 */
public class GpuDevice {
    
    /** Associated OpenCL device */
    private final OpenCLDevice clDevice;
    
    /** Work group size */
    private int workGroupSize;
    
    /**
     * Create the GPU device with the default of 256 work items per work group
     * 
     * @param       clDevice        Associated OpenCL device
     */
    public GpuDevice(OpenCLDevice clDevice) {
        this.clDevice = clDevice;
        this.workGroupSize = Math.min(256, clDevice.getMaxWorkGroupSize());
    }
    
    /**
     * Return the OpenCL device
     * 
     * @return                      OpenCL device
     */
    public OpenCLDevice getDevice() {
        return clDevice;
    }
    
    /**
     * Return the work group size
     * 
     * @return                      Work group size
     */
    public int getWorkGroupSize() {
        return workGroupSize;
    }
    
    /**
     * Set the work group size
     * 
     * @param       size            Work group size
     */
    public void setWorkGroupSize(int size) {
        workGroupSize = Math.min(size, clDevice.getMaxWorkGroupSize());
    }
}
