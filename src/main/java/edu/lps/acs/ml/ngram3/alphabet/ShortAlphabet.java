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

/**
 *
 * @author edraff
 */
public class ShortAlphabet implements Alphabet
{

    @Override
    public long size()
    {
        return 1<<Short.BYTES*8;
    }

    @Override
    public Iterator<Integer> getIter(InputStream input)
    {
        DataInputStream dis = new DataInputStream(input);
        
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
                    nextShort.set(Short.toUnsignedInt(dis.readShort()));
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
                int toRet = nextShort.get();
                if(toRet < 0)
                    throw new NoSuchElementException();
                nextShort.set(-1);
                return toRet;
            }
        };
    }
    
}
