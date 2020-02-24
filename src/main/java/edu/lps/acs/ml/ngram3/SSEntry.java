/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import java.nio.ByteBuffer;

/**
 *
 * @author edraff
 */
public class SSEntry implements Comparable<SSEntry>
{
    public ByteBuffer b;
    public long freq;
    public long eps;

    public SSEntry()
    {
    }

    public SSEntry(ByteBuffer b, long freq, long eps)
    {
        this.b = b;
        this.freq = freq;
        this.eps = eps;
    }
    
    

    @Override
    public boolean equals(Object obj)
    {
        if(obj instanceof SSEntry)
            return this.b.equals(((SSEntry)obj).b);
        else
            return false;
    }

    @Override
    public int compareTo(SSEntry o)
    {
        return this.b.compareTo(o.b);
    }

    @Override
    public int hashCode()
    {
        return b.hashCode();
    }
    
}
