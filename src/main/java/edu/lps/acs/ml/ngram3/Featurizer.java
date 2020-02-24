/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import static edu.lps.acs.ml.ngram3.NGram.hashNgramSet;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import jsat.linear.SparseVector;
import jsat.linear.Vec;
import jsat.utils.IndexTable;
import jsat.utils.IntDoubleMap;
import jsat.utils.IntList;
import jsat.utils.IntSet;

/**
 * This class provides an object that is used to convert byte sequences into
 * feature vectors.
 *
 * @author Edward Raff
 */
public class Featurizer
{
    /**
     * This is the default buffer size that will be used for reading in bytes.
     * Should be greater than the n-gram size.
     */
    private static int BUFFER_INIT_SIZE =  1024;
    public static final ThreadLocal<byte[]> localBuffers = ThreadLocal.withInitial(()->new byte[BUFFER_INIT_SIZE]);
    public static final ThreadLocal<IntDoubleMap> localSets = ThreadLocal.withInitial(()->new IntDoubleMap());

    protected int kept = 0;
    protected int gramSize = 0;
    protected int fs = 0;
    protected final Map<Integer, Integer> hashToIndx = new HashMap<>();
    protected final Map<ByteBuffer, Integer> bytes2Index = new HashMap<>();
    protected  boolean lzjdHash;
    protected NGramHashOption hashMethod;
    protected boolean countEachOccurrence;

    public Featurizer()
    {
        this(true);
    }

    public Featurizer(boolean lzjdHash)
    {
        this.lzjdHash = lzjdHash;
    }
    
    
    public static Featurizer getFromStream(DataInputStream dis) throws IOException
    {
        Featurizer toRet = new Featurizer();
        
        String header = dis.readUTF();
        int hashMethod_ordinal;
        switch (header)
        {
            case "HASH_GRAM_S":
                toRet.gramSize = dis.readInt();
                toRet.fs = dis.readInt();
                hashMethod_ordinal = dis.readInt();
                toRet.countEachOccurrence = dis.readBoolean();
                toRet.kept = dis.readInt();
                for (int i = 0; i < toRet.kept; i++)
                    toRet.hashToIndx.put(dis.readInt(), i);
                break;
            case "EXCT_GRAM_S":
                toRet.gramSize = dis.readInt();
                toRet.fs = dis.readInt();
                hashMethod_ordinal = dis.readInt();
                toRet.countEachOccurrence = dis.readBoolean();
                toRet.kept = dis.readInt();
                for (int i = 0; i < toRet.kept; i++)
                {
                    byte[] b = new byte[toRet.gramSize];
                    for(int j = 0; j < toRet.gramSize; j++)
                        b[j] = dis.readByte();
//                        System.out.println(i + ": " + new String(b));
                    
                    ByteHasher hasher = NGramHashOption.values()[hashMethod_ordinal].getNewHash(toRet.gramSize);
                    
                    toRet.hashToIndx.put(Integer.remainderUnsigned(hasher.hash(b), toRet.fs), i);
                    toRet.bytes2Index.put(ByteBuffer.wrap(b), i);
                }
                break;
            default:
                throw new RuntimeException("Invalid input stream");
        }
        //If the gram size is zero, we are doing LZJD
        toRet.lzjdHash = toRet.gramSize == 0;
        toRet.hashMethod = NGramHashOption.values()[hashMethod_ordinal];
        BUFFER_INIT_SIZE = Math.max(BUFFER_INIT_SIZE, toRet.gramSize*3);
        return toRet;
    }

    public int getNGramSize()
    {
        return gramSize;
    }
    
    public int getKeepListSize()
    {
        return kept;
    }
    
    public int getFilterSize()
    {
        return fs;
    }
    
    /**
     * Converts the given byte array into a feature vector
     * @param bytes the array of bytes to convert
     * @return a new vector object representing the features obtained
     */
    public Vec vectorize(final byte[] bytes)   
    {
        return vectorize(bytes, 0, bytes.length);
    }
    
    /**
     * Converts the given byte array into a feature vector
     * @param bytes the array of bytes to convert
     * @param offset the offset to start for within the byte array
     * @param length the number of bytes to read from the array
     * @return a new vector object representing the features obtained
     */
    public Vec vectorize(final byte[] bytes, int offset, int length)
    {
        ByteArrayInputStream baos = new ByteArrayInputStream(bytes, offset, length);
        
        try
        {
            return vectorize(baos);
        }
        catch(IOException | InterruptedException e)
        {
            //no IO exception should ever occur because we are really reading from just the byte array...
            throw new RuntimeException("Impossible exception occured... please report bug");
        }
    }
    
    
    /**
     * Converts the given input stream into a feature vector
     * @param is the input stream of bytes to convert
     * @return a new vector object representing the features obtained
     * @throws InterruptedException
     * @throws IOException 
     */
    public Vec vectorize(final InputStream is) throws InterruptedException, IOException
    {
        IntDoubleMap set = localSets.get();
        set.clear();
        if(bytes2Index.isEmpty())//we dont know what the raw bytes are, so do the hashing approach
        {
            hashNgramSet(set, is, fs, 1, gramSize, lzjdHash, hashMethod, countEachOccurrence);

            //remove all non-matches from set
            set.keySet().removeIf(hash->!hashToIndx.containsKey(Integer.remainderUnsigned(hash, fs)));
        }
        else//we know the raw bytes!
        {
            ByteHasher hasher = hashMethod.getNewHash(gramSize);
            
            byte[] buffer = localBuffers.get();
            int fails = 0;
            int pos = 0;
            int read;
            while((read = is.read(buffer, pos, buffer.length-pos)) >= 0 && fails < 5)
            {
                if(read == 0)//You read 0 bytes? WTF
                {
                    fails++;
                    Thread.sleep(fails);
                }
                read += pos;//now read defines the END position of the buffer that is valid
                for(/*pos already defined*/; pos < read; /*pos incremented in loop body*/)
                {
                    int h = hasher.pushByteHash(buffer[pos++]);
                    h = Integer.remainderUnsigned(h, fs);
                    if(hashToIndx.containsKey(h) && pos >= gramSize)//This might be one of the true top-k items
                    {
                        int indx = bytes2Index.getOrDefault(ByteBuffer.wrap(buffer, pos-gramSize, gramSize), -1);
                        if(indx >= 0)
                            if(countEachOccurrence)
                                set.increment(indx, 1);
                            else
                                set.put(indx, 1);
                    }
                }

                 //Lets move the last n-gram into the buffer and adjust pos. That way we can reach back to grab any needed n-grams
                if(pos < gramSize)
                {
                    /*
                     * We were not able to read in enough data to even fill 
                     * the buffer enough for an n-gram. So lets leave the 
                     * pos marker where it is. we will either try and read 
                     * more data, or we hit EOF and file was just too small 
                     * for our big n-grams
                     */
                }
                else
                {
                    /*
                     * We read in enough data, shift buffer to retain only 
                     * enough bytes to be able to save off the next n-gram
                     */
                    for(int i = 0; i < gramSize-1; i++)
                        buffer[i] = buffer[read-gramSize+i+1];
                    pos = gramSize-1;
                }
            }
        }
        //create one-hot vector
        int[] nonZeros;
        double[] values;
        if(countEachOccurrence)
        {
            nonZeros = new int[set.size()];
            values = new double[set.size()];
            
            int pos = 0;
            for(Map.Entry<Integer, Double> entry : set.entrySet())
            {
                int h = entry.getKey();
                h = Integer.remainderUnsigned(h, fs);
                h = bytes2Index.isEmpty() ? hashToIndx.get(h) : h;
                nonZeros[pos] = h;
                values[pos] = entry.getValue();
            }
            
            //put both lists in a sorted order
            IntList intListView = IntList.view(nonZeros, nonZeros.length);
            IndexTable it = new IndexTable(intListView);
            it.apply(intListView);
            it.apply(values);
            
            //remove potential collision duplicates
            int colisions = 0;
            for(int i = 1; i < nonZeros.length; i++)
            {
                if(nonZeros[i-1-colisions] == nonZeros[i])//collision, move counts into current position and increment counter
                {
                    values[i] += values[i-1-colisions];
                    colisions++;
                }
                //now store our current value into the correct location
                nonZeros[i-colisions] = nonZeros[i];
                values[i-colisions] = values[i];
            }
            //all colisions have been removed, now shrink arrays 
            if(colisions > 0)
            {
                nonZeros = Arrays.copyOf(nonZeros, nonZeros.length-colisions);
                values = Arrays.copyOf(values, values.length-colisions);
            }
        }
        else
        {
            nonZeros = set.keySet().stream()
                .mapToInt(h -> Integer.remainderUnsigned(h, fs))
                .map(h -> bytes2Index.isEmpty() ? hashToIndx.get(h) : h)
                .distinct()
                .sorted().toArray();
            values = new double[nonZeros.length];
            Arrays.fill(values, 1.0);
        }
        
        Vec vec = new SparseVector(nonZeros, values, kept, nonZeros.length);
        return vec;
    }
    
}
