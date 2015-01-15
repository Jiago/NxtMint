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

import java.util.Date;

/**
 * Solution represents the solution of a minting target
 */
public class Solution {
    
    /** Date */
    private final Date date;
    
    /** Transaction identifier */
    private long txId;
    
    /** Units minted */
    private final double units;
    
    /** Minting counter */
    private final long counter;
    
    /** Hash nonce */
    private final long nonce;
    
    /** Hash count */
    private final long hashCount;
    
    /**
     * Create a new minting solution
     * 
     * @param       date            Solution date
     * @param       units           Number of units minted
     * @param       counter         Minting counter
     * @param       nonce           Hash nonce
     * @param       hashCount       Number of hashes required
     */
    public Solution(Date date, double units, long counter, long nonce, long hashCount) {
        this.date = date;
        this.units = units;
        this.counter = counter;
        this.nonce = nonce;
        this.hashCount = hashCount;
    }
    
    /**
     * Return the date
     * 
     * @return                      Date
     */
    public Date getDate() {
        return date;
    }
    
    /**
     * Return the transaction identifier
     * 
     * @return                      Transaction identifier
     */
    public long getTxId() {
        return txId;
    }
    
    /**
     * Set the transaction identifier
     * 
     * @param       txId            Transaction identifier
     */
    public void setTxId(long txId) {
        this.txId = txId;
    }
    
    /**
     * Return the number of units minted
     * 
     * @return                      Number of units
     */
    public double getUnits() {
        return units;
    }
    
    /**
     * Return the minting counter
     * 
     * @return                      Minting counter
     */
    public long getCounter() {
        return counter;
    }
    
    /**
     * Return the hash nonce
     * 
     * @return                      Hash nonce
     */
    public long getNonce() {
        return nonce;
    }
    
    /**
     * Return the hash count
     * 
     * @return                      Hash count
     */
    public long getHashCount() {
        return hashCount;
    }
}
