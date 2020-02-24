/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.alphabet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author edraff
 */
public class ByteAlphabet implements Alphabet
{

    @Override
    public long size()
    {
        return 1<<Byte.BYTES*8;
    }

    @Override
    public Iterator<Integer> getIter(InputStream input)
    {
        AtomicInteger nextShort = new AtomicInteger(-1);
        
        return new Iterator<Integer>()
        {
            @Override
            public boolean hasNext()
            {
                if(nextShort.get() >= 0)
                    return true;
                try
                {
                    int val = input.read();
                    nextShort.set(val);
                    if(val < 0)
                        return false;
                    return true;
                }
                catch(IOException e)
                {
                    nextShort.set(-1);
                    return false;
                }
            }

            @Override
            public Integer next()
            {
                hasNext();
                int toRet = nextShort.get();
                if(toRet < 0)
                    throw new NoSuchElementException();
                nextShort.set(-1);
                return toRet;
            }
        };
    }
    
}
