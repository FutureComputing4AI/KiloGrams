/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import edu.lps.acs.ml.ngram3.utils.RabinKarpHash;

/**
 * This enum provides a method for indexing and selecting a number of rolling
 * hashing options to use for n-gramming.
 *
 * @author Edawrd Raff
 */
public enum NGramHashOption
{
    MURMUR3 
    {
        @Override
        public ByteHasher getNewHash(int n)
        {
            return new NgramHahser(n);
        }
    },
    RABIN_KARP 
    {
        @Override
        public ByteHasher getNewHash(int n)
        {
            return new RabinKarpHash(n);
        }
    },;
    
    /**
     * 
     * @param n the length of the rolling window in bytes
     * @return a new hashing object to use
     */
    public abstract ByteHasher getNewHash(int n);
}
