/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.alphabet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import jsat.utils.random.XORWOW;

/**
 *
 * @author edraff
 */
public class ByteGrams implements AlphabetGram
{
    static final int B = 31;
    int n;
    int B2N = 1;
    int curHashValue = 0;
    int curLen = 0;
    byte[] circular_buffer;
    int buffer_pos;

    public ByteGrams(int n)
    {
        this.n = n;
        for(int i = 0; i < n; i++)
            B2N *= B;
        buffer_pos = 0;
        circular_buffer = new byte[n];
    }

    public ByteGrams(ByteGrams toCopy)
    {
        this.n = toCopy.n;
        this.B2N = toCopy.B2N;
        this.curHashValue = toCopy.curHashValue;
        this.curLen = toCopy.curLen;
        this.buffer_pos = toCopy.buffer_pos;
        this.circular_buffer = Arrays.copyOf(toCopy.circular_buffer, toCopy.circular_buffer.length);
    }
    
    
    
    

    @Override
    public int push(int b)
    {
        byte a = (byte) b;
        if (curLen < n)
        {
            curHashValue = B * curHashValue + byteHash[Byte.toUnsignedInt(a)];
            circular_buffer[curLen] = a;
            curLen++;
        }
        else//, normal case
        {
            ///swap out buffer values 
            int out = Byte.toUnsignedInt(circular_buffer[buffer_pos]);
            circular_buffer[buffer_pos] = a;
            buffer_pos = (buffer_pos + 1) % n;

            curHashValue = B * curHashValue + byteHash[Byte.toUnsignedInt(a)] - B2N * byteHash[out];
        }

        return curHashValue;
    }

    @Override
    public int get(int pos)
    {
        pos = (buffer_pos + pos) % n;
        return circular_buffer[pos];
    }

    @Override
    public int getUnsigned(int pos)
    {
        pos = (buffer_pos + pos) % n;
        return Byte.toUnsignedInt(circular_buffer[pos]);
    }

    @Override
    public int size()
    {
        return n;
    }

    @Override
    public AlphabetGram clone()
    {
        return new ByteGrams(this);
    }

    @Override
    public int hashCode()
    {
        return curHashValue;
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if(obj instanceof ByteGrams)
        {
            AlphabetGram other = (AlphabetGram) obj;
            
            if(this.size() != other.size())
                return false;
            for(int i = 0; i < this.size(); i++)
                if(this.get(i) != other.get(i))
                    return false;
            return true;
        }
        
        return false;
    }
    
    static int[] byteHash = new int[1<<8];
    static
    {
        HashSet<Integer> vals = new HashSet<>();
        Random rand = new XORWOW(468493);
        while(vals.size() < byteHash.length)
            vals.add(rand.nextInt());
        int pos = 0;
        for(int v : vals)
            byteHash[pos++] = v;
    }

    @Override
    public Alphabet getAlphabet()
    {
        return new ByteAlphabet();
    }
}
