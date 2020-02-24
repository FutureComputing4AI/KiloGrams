/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

/**
 *
 * @author edraff
 */
public interface ByteHasher
{
    public int pushByteHash(byte b);
    
    /**
     * Computes the hash for the given byte sequence 
     * @param bytes
     * @return 
     */
    public int hash(byte[] bytes);
    
    default public void finished()
    {
        
    }
}
