/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.alphabet;

import edu.lps.acs.ml.ngram3.utils.ByteBufferBackedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author edraff
 */
public interface Alphabet
{
    /**
     * 
     * @return the size of this alphabet
     */
    public long size();
    

    
    /**
     * takes an input stream of bytes, and converts it to a sequence of integers
     * corresponding t the underlying alphabet.
     *
     * @param input the input stream to process into some set of 1-grams from
     * the base alphabet
     * @return an iterator of integer values, where the values correspond to the
     * base alphabet and are in the range [0, {@link #size() })
     */
    public Iterator<Integer> getIter(InputStream input);
    
    default public Iterator<Integer> getIter(ByteBuffer input)
    {
        try(InputStream i = new ByteBufferBackedInputStream(input))
        {
            return getIter(i);
        }
        catch (IOException ex)
        {
            Logger.getLogger(Alphabet.class.getName()).log(Level.SEVERE, null, ex);
            return Collections.EMPTY_LIST.iterator();
        }
    }
    
    default public Iterator<Integer> getIter(byte[] input)
    {
        try(ByteArrayInputStream bis = new ByteArrayInputStream(input))
        {
            return getIter(bis);
        }
        catch (IOException ex)
        {
            Logger.getLogger(Alphabet.class.getName()).log(Level.SEVERE, null, ex);
            //This should never happen... IO error from byte aray?
            return Collections.EMPTY_LIST.iterator();
        }
    }
}
