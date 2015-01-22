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

import org.ScripterRon.NxtCore.MintingTarget;

import com.amd.aparapi.Kernel;
import com.amd.aparapi.Range;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mint worker
 */
public class MintWorker implements Runnable {
    
    /** Worker identifier */
    private final int workerId;
    
    /** GPU worker */
    private final boolean gpuWorker;
    
    /** GPU identifier */
    private int gpuId;
    
    /** GPU disabled */
    private boolean gpuDisabled;
    
    /** GPU disabled time */
    private long gpuDisabledTime;
    
    /** Worker thread */
    private Thread thread;
    
    /** Work queue */
    private final ArrayBlockingQueue<MintingTarget> workQueue = new ArrayBlockingQueue<>(5);
    
    /** Solution queue */
    private final ArrayBlockingQueue<Solution> solutionQueue;
    
    /** CPU hash function */
    private final HashFunction hashFunction;
    
    /** GPU hash function */
    private GpuFunction gpuFunction;
    
    /** Hash count */
    private long hashCount;
    
    /** Nonce */
    private long nonce;
    
    /**
     * Create a new worker
     * 
     * @param       workerId        Worker identifier
     * @param       solutionQueue   Hash solution queue
     * @param       gpuWorker       TRUE if this is the GPU worker
     * @param       gpuId           GPU identifier
     */
    public MintWorker(int workerId, ArrayBlockingQueue<Solution> solutionQueue, 
                                    boolean gpuWorker, int gpuId) {
        this.workerId = workerId;
        this.solutionQueue = solutionQueue;
        this.gpuWorker = gpuWorker;
        hashFunction = HashFunction.factory(Main.currency.getAlgorithm());
        if (gpuWorker) {
            gpuFunction = GpuFunction.factory(Main.currency.getAlgorithm(), Main.gpuDeviceList.get(gpuId));
            this.gpuId = gpuId;
        }
    }
    
    /**
     * Start hashing
     */
    @Override
    public void run() {
        byte[] hashBytes = new byte[40];
        thread = Thread.currentThread();
        if (gpuWorker)
            log.info(String.format("GPU worker %d starting on GPU %d", workerId, gpuId));
        else
            log.info(String.format("CPU worker %d starting", workerId));
        //
        // Process hashing targets until shutdown
        //
        try {
            while (true) {
                //
                // Get the next hash target
                //
                MintingTarget target = workQueue.take();
                long counter = target.getCounter()+1;
                log.debug(String.format("Worker %d starting on counter %d", workerId, counter));
                byte[] targetBytes = target.getTarget();
                hashCount = 0;
                long startTime = System.currentTimeMillis();
                long statusTime = startTime;
                //
                // Hash until the result meets the target, we get a new target or we are interrupted
                //
                while (true) {
                    if (thread.isInterrupted())
                        throw new InterruptedException("Shutting down");
                    if (!workQueue.isEmpty()) {
                        log.debug(String.format("Worker %d abandoning counter %d", workerId, counter));
                        break;
                    }
                    if (nonce == 0 || (gpuWorker&!gpuDisabled))
                        nonce = (ThreadLocalRandom.current().nextLong()&0x00ffffffffffffffL)|((long)workerId<<56);
                    else
                        nonce++;
                    ByteBuffer buffer = ByteBuffer.wrap(hashBytes);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.putLong(nonce);
                    buffer.putLong(Main.currency.getCurrencyId());
                    buffer.putLong(Main.mintingUnits);
                    buffer.putLong(counter);
                    buffer.putLong(Main.accountId);
                    boolean meetsTarget;
                    if (gpuWorker && !gpuDisabled)
                        meetsTarget = gpuHash(hashBytes, targetBytes);
                    else
                        meetsTarget = cpuHash(hashBytes, targetBytes);
                    //
                    // Return the solution if the hash meets the target
                    //
                    if (meetsTarget) {
                        log.info(String.format("Worker %d found solution for counter %d", workerId, counter));
                        Solution solution = new Solution(new Date(), Main.currencyUnits, counter, nonce, hashCount);
                        solutionQueue.put(solution);
                        break;
                    }
                    //
                    // Print a status message every 60 seconds
                    //
                    long currentTime = System.currentTimeMillis();
                    if (currentTime-statusTime > 1*60*1000) {
                        double count = (double)hashCount;
                        double rate = count/(double)((currentTime-startTime)/1000);
                        log.debug(String.format("Worker %d: %,.2f MHash, %,.4f MHash/s", 
                                                workerId, count/1000000.0, rate/1000000.0));
                        statusTime = currentTime;
                    }
                    //
                    // Re-enable the GPU if we have waited 5 minuts
                    //
                    if (gpuDisabled && currentTime-gpuDisabledTime > 5*60*1000) {
                        gpuDisabled = false;
                        gpuFunction = GpuFunction.factory(Main.currency.getAlgorithm(), Main.gpuDeviceList.get(gpuId));
                        log.info("Enabling GPU hashing");
                    }
                }
            }
        } catch (InterruptedException exc) {
            log.info(String.format("Worker %d stopping", workerId));
        } catch (Throwable exc) {
            log.error(String.format("Worker %d terminated by exception", workerId), exc);
        }
    }
    
    /**
     * Stop hashing
     */
    public void shutdown() {
        try {
            if (thread != null) {
                thread.interrupt();
                thread.join(60000);
            }
        } catch (InterruptedException exc) {
            log.error("Unable to wait for worker to terminate");
        }
    }
    
    /**
     * New hash target
     * 
     * @param       target          Minting target
     */
    public void newTarget(MintingTarget target) {
        try {
            workQueue.put(target);
        } catch (InterruptedException exc) {
            log.error("Unable to add new target to work queue", exc);
        }
    }
    
    /**
     * Hash using CPU threads
     * 
     * @param       hashBytes       Bytes to be hashed
     * @param       targetBytes     Target
     * @return                      TRUE if the hash satisfies the target
     */
    private boolean cpuHash(byte[] hashBytes, byte[] targetBytes) {
        hashCount++;
        byte[] hashDigest = hashFunction.hash(hashBytes);
        boolean meetsTarget = true;
        for (int i=hashDigest.length-1; i>=0; i--) {
            int byte1 = (hashDigest[i]&0xff);
            int byte2 = (targetBytes[i]&0xff);
            if (byte1 < byte2)
                break;
            if (byte1 > byte2) {
                meetsTarget = false;
                break;
            }
        }
        return meetsTarget;
    }
    
    /**
     * Hash using the GPU
     * 
     * @param       hashBytes       Bytes to be hashed
     * @param       targetBytes     Target
     * @return                      TRUE if the hash satisfies the target
     */
    private boolean gpuHash(byte[] hashBytes, byte[] targetBytes) {
        boolean meetsTarget = false;
        gpuFunction.setInput(hashBytes, targetBytes);
        gpuFunction.execute();
        if (!gpuFunction.getExecutionMode().equals(Kernel.EXECUTION_MODE.GPU)) {
            log.warn("GPU execution did not complete, probably due to GPU resource shortage");
            log.info("Disabling GPU hashing and reverting to CPU hashing");
            gpuDisabled = true;
            gpuDisabledTime = System.currentTimeMillis();
            gpuFunction.dispose();
            gpuFunction = null;
        } else {
            meetsTarget = gpuFunction.isSolved();
            hashCount += gpuFunction.getCount();
            if (meetsTarget)
                nonce = gpuFunction.getNonce();
        }
        return meetsTarget;
    }
}
