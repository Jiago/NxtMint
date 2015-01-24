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

import org.ScripterRon.NxtCore.ChainState;
import org.ScripterRon.NxtCore.MintingTarget;
import org.ScripterRon.NxtCore.Nxt;
import org.ScripterRon.NxtCore.NxtException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Mint mints coins for a Nxt Monetary System currency using multiple worker
 * threads to perform the hash functions.
 */
public class Mint {
    
    /** Mint thread */
    private static Thread mintThread;
    
    /** Thread list */
    private static final List<MintWorker> workers = new ArrayList<>();
    
    /** Thread group */
    private static final ThreadGroup threadGroup = new ThreadGroup("Workers");
    
    /** Solution queue */
    private static final ArrayBlockingQueue<Solution> solutions = new ArrayBlockingQueue<>(10);
    
    /** Pending queue */
    private static final List<Solution> pending = new LinkedList<>();
    
    /** Block height at last solution submission */
    private static int submitHeight;
    
    /** Counter at last solution submission */
    private static long submitCounter;
    
    /** Current solution counter */
    private static long counter;
    
    /**
     * Start minting
     */
    public static void mint() {
        List<Long> txList;
        mintThread = Thread.currentThread();
        //
        // Get the initial currency counter.  We will increment this counter for
        // each minting transaction.  If the account has unconfirmed transactions, we
        // need to skip the current counter since the server does not increment the counter until
        // the transaction is confirmed in a block.
        //
        counter = Main.mintingTarget.getCounter();
        try {
            txList = Nxt.getUnconfirmedAccountTransactions(Main.accountId);
            if (!txList.isEmpty())
                counter++;
        } catch (NxtException exc) {
            log.error("Unable to get unconfirmed transactions", exc);
        }
        //
        // Start the CPU worker threads
        //
        for (int i=0; i<Main.cpuThreads; i++) {
            MintWorker worker = new MintWorker(i, solutions, false, 0);
            Thread thread = new Thread(threadGroup, worker);
            thread.start();
            workers.add(worker);
        }
        //
        // Start the GPU worker thread
        //
        if (Main.gpuIntensity > 0) {
            for (Integer gpuId : Main.gpuDevices) {
                MintWorker worker = new MintWorker(workers.size(), solutions, true, gpuId);
                Thread thread = new Thread(threadGroup, worker);
                thread.start();
                workers.add(worker);
            }
        }
        //
        // Mint coins until shutdown
        //
        try {
            boolean workDispatched = false;
            while (true) {
                if (mintThread.isInterrupted())
                    throw new InterruptedException("Shutting down");
                //
                // Process completed solutions
                //
                if (workDispatched && (pending.isEmpty() || !solutions.isEmpty())) {
                    Solution solution = solutions.take();
                    if (solution.getCounter() > submitCounter) {
                        workDispatched = false;
                        submitCounter = solution.getCounter();
                        pending.add(solution);
                        log.debug(String.format("Solution for counter %d added to pending queue", solution.getCounter()));
                    }
                }
                //
                // Dispatch the new target if the workers are idle
                //
                if (!workDispatched) {
                    MintingTarget mintingTarget;
                    try {
                        mintingTarget = Nxt.getMintingTarget(Main.currency.getCurrencyId(),
                                                             Main.accountId, Main.mintingUnits);
                        mintingTarget.setCounter(counter);
                        counter++;
                    } catch (NxtException exc) {
                        log.error("Unable to get new minting target", exc);
                        throw new InterruptedException("Abormal shutdown");
                    }
                    workers.stream().forEach((worker) -> worker.newTarget(mintingTarget));
                    workDispatched = true;
                }
                //
                // Submit a pending solution
                //
                // We can have just one unconfirmed minting transaction at a time.  So we need to hold
                // additional transactions until a block has been confirmed.  This is usually not a
                // problem but a block occasionally takes 10 minutes or longer to be generated.  If
                // this happens, we will poll the server until a new block has been generated.
                //
                if (!pending.isEmpty()) {
                    boolean submitted = false;
                    try {
                        ChainState chainState = Nxt.getChainState();
                        if (chainState.getBlockCount() > submitHeight) {
                            submitHeight = chainState.getBlockCount();
                            txList = Nxt.getUnconfirmedAccountTransactions(Main.accountId);
                            if (txList.isEmpty()) {
                                Solution solution = pending.get(0);
                                long txId = Nxt.currencyMint(Main.currency.getCurrencyId(), Main.mintingUnits,
                                                    solution.getCounter(), solution.getNonce(), 
                                                    100000000L, 120, null, Main.secretPhrase);
                                solution.setTxId(txId);
                                if (Main.mainWindow != null)
                                    Main.mainWindow.solutionFound(solution);
                                log.info(String.format("Solution for counter %d submitted", solution.getCounter()));
                                submitted = true;
                                pending.remove(0);
                            }
                        }
                    } catch (NxtException exc) {
                        int errCode = exc.getReasonCode();
                        if (errCode != 0) {
                            log.error("Server rejected 'currencyMint' transaction - discarding");
                            submitted = true;
                            pending.remove(0);
                        } else {
                            log.error("Unable to submit 'currencyMint' transaction - retrying", exc);
                        }
                    }
                    if (!submitted)
                        Thread.sleep(30000);
                }
            }
        } catch (InterruptedException exc) {
            log.info("Minting controller stopping");
        } catch (Throwable exc) {
            log.error("Minting controller terminated by exception", exc);
        }
    }
    
    /**
     * Stop minting
     */
    public static void shutdown() {
        try {
            //
            // Stop the mint thread
            //
            if (Thread.currentThread() != mintThread) {
                mintThread.interrupt();
                mintThread.join(60000);
            }
            //
            // Stop the worker threads
            //
            for (MintWorker worker : workers)
                worker.shutdown();
        } catch (InterruptedException exc) {
            log.error("Unable to wait for workers to terminate", exc);
        }
    }
}
