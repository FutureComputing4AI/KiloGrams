/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import edu.lps.acs.ml.ngram3.lzjd.MurmurHash3;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import jsat.utils.IntSet;

/**
 *
 * @author edraff
 */
public class LZJDHasher implements ByteHasher
{
    /**
     * Source of re-usable qs
     */
    static final Queue<IntSet> freeQs = new ConcurrentLinkedQueue<>();
    MurmurHash3 running_hash = new MurmurHash3();
    int len;
    IntSet seenBefore;

    public LZJDHasher()
    {
        seenBefore = freeQs.poll();
        if(seenBefore == null)//q was empt
            seenBefore = new IntSet();
        seenBefore.clear();
        len = 0;
    }
        
    @Override
    public int pushByteHash(byte b)
    {

        int hash = running_hash.pushByte(b);
        len++;
        if (len > 2 && seenBefore.add(hash))
        {//never seen it before, re-set the hash counter
            running_hash.reset();
            len = 0;
        }
        return hash;
    }

    @Override
    public void finished()
    {
        seenBefore.clear();
        freeQs.add(seenBefore);
    }

    @Override
    public int hash(byte[] bytes)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
