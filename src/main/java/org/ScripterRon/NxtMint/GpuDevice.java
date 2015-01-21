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
    private final OpenCLDevice device;
    
    /** Number of processing cores */
    private int cores;
    
    /**
     * Create the GPU device with the default of 256 cores per compute unit
     * 
     * @param       device          Associated OpenCL device
     */
    public GpuDevice(OpenCLDevice device) {
        this.device = device;
        this.cores = 256 * device.getMaxComputeUnits();
    }
    
    /**
     * Return the OpenCL device
     * 
     * @return                      OpenCL device
     */
    public OpenCLDevice getDevice() {
        return device;
    }
    
    /**
     * Return the number or processing cores
     * 
     * @return                      Number of cores
     */
    public int getCores() {
        return cores;
    }
    
    /**
     * Set the number of processing cores
     * 
     * @param       cores           Number of cores
     */
    public void setCores(int cores) {
        this.cores = cores;
    }
}
