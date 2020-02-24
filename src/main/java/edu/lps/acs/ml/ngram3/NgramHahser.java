/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import edu.lps.acs.ml.ngram3.utils.MurmurHash3;
import java.nio.ByteBuffer;

/**
 *
 * @author edraff
 */
public class NgramHahser implements ByteHasher
{
    private final MurmurHash3[] hashers;
    private int pos = 0;
    
    public NgramHahser(int n)
    {

        hashers = new MurmurHash3[n];
        for(int i = 0; i < n; i++)
            hashers[i] = new MurmurHash3(1073741827);
    }
    
    public static int hash(ByteBuffer b)
    {
        MurmurHash3 hasher = new MurmurHash3(1073741827);
        int h = 0;
        for(int i = 0; i < b.limit(); i++)
            h = hasher.pushByte(b.get(i));
        return h;
    }
    
    @Override
    public int hash(byte[] b)
    {
        MurmurHash3 hasher = new MurmurHash3(1073741827);
        int h = 0;
        for(int i = 0; i < b.length; i++)
            h = hasher.pushByte(b[i]);
        return h;
    }
    
    @Override
    public int pushByteHash(byte b)
    {
        int h = 0;
        for(int i = 0; i < hashers.length; i++)
        {
            int x = hashers[i].pushByte(b);
            if(i == pos)
            {
                h = x;
                hashers[i].reset();
            }
        }
        pos = (pos+1) % hashers.length;
        
        return h;
    }
    
    
}
