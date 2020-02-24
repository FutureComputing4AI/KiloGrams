/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.alphabet;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author edraff
 */
public class IntAlphabet implements Alphabet
{

    @Override
    public long size()
    {
        return 1L<<Integer.BYTES*8;
    }

    @Override
    public Iterator<Integer> getIter(InputStream input)
    {
        DataInputStream dis = new DataInputStream(input);
        
        AtomicLong nextInt = new AtomicLong(-1);
        
        return new Iterator<Integer>()
        {
            @Override
            public boolean hasNext()
            {
                if(nextInt.get() >= 0)
                    return true;
                try
                {
                    nextInt.set(Integer.toUnsignedLong(dis.readInt()));
                    return true;
                }
                catch(IOException e)
                {
                    nextInt.set(-1);
                    return false;
                }
            }

            @Override
            public Integer next()
            {
                long toRet = nextInt.get();
                if(toRet < 0)
                    throw new NoSuchElementException();
                nextInt.set(-1);
                return (int) toRet;
            }
        };
    }
    
}
