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
public class IntGrams implements AlphabetGram
{
    static final int B = 31;
    int n;
    int B2N = 1;
    int curHashValue = 0;
    int curLen = 0;
    int[] circular_buffer;
    int buffer_pos;

    public IntGrams(int n)
    {
        this.n = n;
        for(int i = 0; i < n; i++)
            B2N *= B;
        buffer_pos = 0;
        circular_buffer = new int[n];
    }

    public IntGrams(IntGrams toCopy)
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
        int a = b;
        if (curLen < n)
        {
            curHashValue = B * curHashValue + h(a);
            circular_buffer[curLen] = a;
            curLen++;
        }
        else//, normal case
        {
            ///swap out buffer values 
            int out = circular_buffer[buffer_pos];
            circular_buffer[buffer_pos] = a;
            buffer_pos = (buffer_pos + 1) % n;

            curHashValue = B * curHashValue + h(a) - B2N * h(out);
        }

        return curHashValue;
    }
    
    public static int h(int val)
    {
        //TODO replace with a decent hash function
        return val;
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
        return get(pos);
    }

    @Override
    public int size()
    {
        return n;
    }

    @Override
    public AlphabetGram clone()
    {
        return new IntGrams(this);
    }

    @Override
    public int hashCode()
    {
        return curHashValue;
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if(obj instanceof IntGrams)
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
    
    @Override
    public Alphabet getAlphabet()
    {
        return new IntAlphabet();
    }
}
