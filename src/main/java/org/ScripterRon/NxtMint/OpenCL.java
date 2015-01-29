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
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_platform_id;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *  OpenCL helper routines
 */
public class OpenCL {
    
    /**
     * Return the value of the device information parameter with the given name
     *
     * @param       device              Device
     * @param       paramName           Parameter name
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static String getString(cl_device_id device, int paramName) throws CLException {
        //
        // Obtain the length of the string
        //
        long size[] = new long[1];
        CL.clGetDeviceInfo(device, paramName, 0, null, size);
        //
        // Create a buffer of the appropriate size and get the requested information
        //
        // Note that OpenCL returns strings with a trailing 0x00 delimiter byte.
        // We need to remove the delimiter when creating the Java string.
        //
        byte buffer[] = new byte[(int)size[0]];
        CL.clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);
        return new String(buffer, 0, buffer.length-1);
    }

    /**
     * Return the value of the platform information parameter with the given name
     *
     * @param       platform            Platform
     * @param       paramName           Parameter name
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static String getString(cl_platform_id platform, int paramName) throws CLException {
        //
        // Obtain the length of the string
        //
        long size[] = new long[1];
        CL.clGetPlatformInfo(platform, paramName, 0, null, size);
        //
        // Create a buffer of the appropriate size and get the requested information
        //
        // Note that OpenCL returns strings with a trailing 0x00 delimiter byte.
        // We need to remove the delimiter when creating the Java string.
        //
        byte buffer[] = new byte[(int)size[0]];
        CL.clGetPlatformInfo(platform, paramName, buffer.length, Pointer.to(buffer), null);
        return new String(buffer, 0, buffer.length-1);
    }
    
    /**
     * Return the value of the device information parameter with the given name
     * 
     * @param       device              Device
     * @param       paramName           Parameter name
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static boolean getBoolean(cl_device_id device, int paramName) throws CLException {
        int value[] = new int[1];
        CL.clGetDeviceInfo(device, paramName, Sizeof.cl_uint*1, Pointer.to(value), null);
        return value[0]!=0;
    }

    /**
     * Return the value of the device information parameter with the given name
     *
     * @param       device              Device
     * @param       paramName           Parameter name
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static int getInt(cl_device_id device, int paramName) throws CLException {
        return getInts(device, paramName, 1)[0];
    }

    /**
     * Return the values of the device information parameter with the given name
     *
     * @param       device              Device
     * @param       paramName           Parameter name
     * @param       numValues           Number of values to return
     * @return                          Requested values
     * @throws      CLException         Error detected
     */
    public static int[] getInts(cl_device_id device, int paramName, int numValues) throws CLException {
        int values[] = new int[numValues];
        CL.clGetDeviceInfo(device, paramName, Sizeof.cl_int*numValues, Pointer.to(values), null);
        return values;
    }

    /**
     * Return the value of the device information parameter with the given name
     *
     * @param       device              Device
     * @param       paramName           Parameter name
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static long getLong(cl_device_id device, int paramName) throws CLException {
        return getLongs(device, paramName, 1)[0];
    }

    /**
     * Return the values of the device information parameter with the given name
     *
     * @param       device              Device
     * @param       paramName           Parameter name
     * @param       numValues           Number of values to return
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static long[] getLongs(cl_device_id device, int paramName, int numValues) throws CLException {
        long values[] = new long[numValues];
        CL.clGetDeviceInfo(device, paramName, Sizeof.cl_long*numValues, Pointer.to(values), null);
        return values;
    }
 
    /**
     * Return the value of the device information parameter with the given name
     *
     * @param       device              Device
     * @param       paramName           Parameter name
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static long getSize(cl_device_id device, int paramName) throws CLException {
        return getSizes(device, paramName, 1)[0];
    }

    /**
     * Return the values of the device information parameter with the given name
     *
     * @param       device              Device
     * @param       paramName           Parameter name
     * @param       numValues           Number of values to return
     * @return                          The requested value
     * @throws      CLException         Error detected
     */
    public static long[] getSizes(cl_device_id device, int paramName, int numValues) throws CLException {
        //
        // The size of the returned data depends on the size of a size_t
        //
        ByteBuffer buffer = ByteBuffer.allocate(numValues*Sizeof.size_t).order(ByteOrder.nativeOrder());
        CL.clGetDeviceInfo(device, paramName, Sizeof.size_t*numValues, Pointer.to(buffer), null);
        long values[] = new long[numValues];
        if (Sizeof.size_t == 4) {
            for (int i=0; i<numValues; i++)
                values[i] = buffer.getInt(i*Sizeof.size_t);
        } else {
            for (int i=0; i<numValues; i++)
                values[i] = buffer.getLong(i*Sizeof.size_t);
        }
        return values;
    }    

    /**
     * Return the value of the kernel information parameter with the given name
     *
     * @param       kernel              Kernel
     * @param       device              Device executing the kernel
     * @param       paramName           Parameter name
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static long getSize(cl_kernel kernel, cl_device_id device, int paramName) throws CLException {
        return getSizes(kernel, device, paramName, 1)[0];
    }    
    /**
     * Return the value of the kernel information parameter with the given name
     * 
     * @param       kernel              Kernel
     * @param       device              Device executing the kernel
     * @param       numValues           Number of values to return
     * @return                          Requested value
     * @throws      CLException         Error detected
     */
    public static long[] getSizes(cl_kernel kernel, cl_device_id device, int paramName, int numValues) 
                                        throws CLException {
        //
        // The size of the returned data depends on the size of a size_t        
        //
        ByteBuffer buffer = ByteBuffer.allocate(numValues*Sizeof.size_t).order(ByteOrder.nativeOrder());
        CL.clGetKernelWorkGroupInfo(kernel, device, paramName, Sizeof.size_t*numValues, Pointer.to(buffer), null);
        long values[] = new long[numValues];
        if (Sizeof.size_t == 4) {
            for (int i=0; i<numValues; i++)
                values[i] = buffer.getInt(i*Sizeof.size_t);
        } else {
            for (int i=0; i<numValues; i++)
                values[i] = buffer.getLong(i*Sizeof.size_t);
        }
        return values;
    }
}