/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import edu.lps.acs.ml.ngram3.alphabet.ShortGrams;
import edu.lps.acs.ml.ngram3.alphabet.AlphabetGram;
import com.beust.jcommander.Parameter;
import com.clearspring.analytics.stream.StreamSummary;
import com.google.common.collect.AbstractSequentialIterator;
import com.google.common.collect.Ordering;
import edu.lps.acs.ml.ngram3.alphabet.ByteGrams;
import edu.lps.acs.ml.ngram3.alphabet.IntGrams;
import edu.lps.acs.ml.ngram3.utils.IndexCountPair;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import jsat.utils.IntDoubleMap;
import jsat.utils.IntList;
import jsat.utils.IntSet;
import jsat.utils.SystemInfo;

/**
 * This class provides general-purpose n-gram computation over alphabets of any
 * size less than a java unsigned int. This is for use in broader uses cases.
 *
 * @author edraff
 */
public class NGramGeneric
{
    /**
     * This is the default buffer size that will be used for reading in bytes.
     * Should be greater than the n-gram size.
     */
    private static int BUFFER_INIT_SIZE =  1024*10;
    public static final ThreadLocal<byte[]> localBuffers = ThreadLocal.withInitial(()->new byte[BUFFER_INIT_SIZE]);
    public static final ThreadLocal<IntDoubleMap> localSets = ThreadLocal.withInitial(()->new IntDoubleMap());

    @Parameter(names="--exact-pass", description="Option to perform a 2nd pass over the data to determin exact n-gram values")
    boolean exact_pass = true;
    
    @Parameter(names={"-em"}, description="Keeplist size multiplier used for exact pass")
    int exact_multiplier = 3;
    
    @Parameter(names="--absolute-counts", description="If set to true, count n-grams based on the absolute number of times they occur. Default is to only count once per file.")
    boolean countEachOccurrence = false;
    
    @Parameter(names={"--min-count", "-mc"}, description="Minimum number of counts an n-gram must have to be considered for retainment. "
            + "This will not increase the number of n-grams \"too-keep\", but may reduce them. ")
    int minCount = 0;
    
    int alphabetSize = 255;
    
    /**
     * The size of the filter. Must be some small value smaller than
     * Integer.MAX_VALUE due to VM array size limits. The default value is the
     * largest prime that is below these limits and below the max integer size.
     */
    @Parameter(names="--filter-size")
    int filterSize = Integer.MAX_VALUE - 18;
    
    @Parameter(names={"--too-keep", "-k"}, required=true, description="NGrams to keep")
    int tooKeep;
    @Parameter(names={"--ngram-size", "-n"}, description="Size of ngrams")
    int gramSize = 0;
    @Parameter(names={"--hash-stride", "-hs"}, description="The \"stride\" of the hash selection ")
    int hashStride = 1;

    public void setAlphabetSize(int alphabetSize)
    {
        this.alphabetSize = alphabetSize;
    }

    public void setFilterSize(int filterSize)
    {
        this.filterSize = filterSize;
    }

    public void setGramSize(int gramSize)
    {
        this.gramSize = gramSize;
        this.hashStride = Math.max(gramSize/4, 1);
    }

    public void setTooKeep(int tooKeep)
    {
        this.tooKeep = tooKeep;
    }
    
    
    
    
    public AlphabetGram getNewGram(int n)
    {
        if(alphabetSize <= 256)
            return new  ByteGrams(n);
        else if(alphabetSize <= 256*256)
            return new ShortGrams(n);
        else
            return new IntGrams(n);
    }
    
    /**
     * Store the count for every hashed n-gram seen 
     */
    private AtomicIntegerArray countIndex;
    
    /**
     * Stores all of the local write caches used by {@link #allLocalBuffers} so
     * that they can be cleared out when done.
     *
     */
    List<int[][]> allLocalBuffers = new ArrayList<>();
    /**
     * Local buffer of integer arrays used as a write cache for the
     * {@link #countIndex}.
     */
    ThreadLocal<int[][]> threadLocalBuffer;
    
    /**
     * White list of the hash values of the top-k most frequent hashes
     */
    IntSet whiteList;
    
    List<StreamSummary<AlphabetGram>> local_summary_all;
    ThreadLocal<StreamSummary<AlphabetGram>> local_summary;
    
    public void init()
    {
        countIndex = new AtomicIntegerArray(filterSize);
        
        /**
         * We keep a thread local write buffer to avoid contention on hot items
         * in the global countIndex table. 
         * First dimension has 2 options. Index 0 is the local counts, Index 1 
         * is the hash value. 
         * When buffer is full, evictions are pushed out into the global table. 
         * A final pass at the end will take the remaining local counts and move
         * them into the global table. 
         */
        final int write_buffer_size = SystemInfo.LogicalCores*5;
        allLocalBuffers = new ArrayList<>();
        threadLocalBuffer = ThreadLocal.withInitial(()->
        {
            int[][] b = new int[2][write_buffer_size];
            synchronized(allLocalBuffers)
            {
                allLocalBuffers.add(b);
            }
            return b;
        });
    }
    
    public void hashCount(InputStream is) throws IOException, InterruptedException
    {
        //Originally, we re-used a set for all data. Some kind of logic 
        //flaw / bad file caused this to become MASSIVE (100MB+), which
        //then caused needless overhead for all subsequent files. 
        //Replaced with a new map on every instance. Need to debug later
        //why the map got so absurdley large, as error log showed sizes 
        //larger than ever should have been needed. 
//                IntDoubleMap set = localSets.get();
        IntDoubleMap set = new IntDoubleMap();
        int[][] localBuffs = threadLocalBuffer.get();
        int[] wb_count = localBuffs[0];
        int[] wb_id = localBuffs[1];
        final int write_buffer_size = wb_id.length;

        hashNgramSet(set, is, filterSize, hashStride, gramSize, !countEachOccurrence);
        for(Map.Entry<Integer, Double> entry : set.entrySet())
        {
            int h = entry.getKey();
            h = Integer.remainderUnsigned(h, filterSize);
            if(h % hashStride == 0)
            {
                //Insert into buffer
                int h_2 = h % write_buffer_size;
                if(wb_id[h_2] != h)//evict the tenant!
                {
                    countIndex.addAndGet(wb_id[h_2], wb_count[h_2]);
                    //now replace with ourselves
                    wb_count[h_2] = 0;
                    wb_id[h_2] = h;
                }
                //else, we are in the buffer - just increment
                wb_count[h_2] += entry.getValue().intValue();
            }
        }
        set.clear();
    }
    
    public void finishHashCount()
    {
        //now push out remaining counts in every buffer
        allLocalBuffers.parallelStream().forEach(b ->
        {
            for (int i = 0; i < b[0].length; i++)
                countIndex.addAndGet(b[1][i], b[0][i]);
        });

        //now lets find the top-k hashes
        Iterator<IndexCountPair> countIterator = new AbstractSequentialIterator<IndexCountPair>(new IndexCountPair(0, countIndex.get(0)))
        {
            @Override
            protected IndexCountPair computeNext(IndexCountPair t)
            {
                if (t.index == countIndex.length() - 1)
                    return null;
                //MUST return a new object for each index in order for Ordering.natural code to work
                return new IndexCountPair(t.index + 1, countIndex.get(t.index + 1));
            }
        };

        final int[] selectedFPIndexes = Ordering.natural().greatestOf(countIterator, tooKeep)
                .stream()
                //filter out things that could not possibly meet our min-count requirements
                .filter(t -> t.count >= minCount)
                .mapToInt(t -> t.index)
                .sorted().toArray();
        final Map<Integer, Integer> oldIndx = new HashMap<>(selectedFPIndexes.length);
        for (int i = 0; i < selectedFPIndexes.length; i++)
            oldIndx.put(selectedFPIndexes[i], i);
        
        whiteList = new IntSet(IntList.view(selectedFPIndexes, selectedFPIndexes.length));
        
        local_summary_all = new ArrayList<>();
        local_summary = ThreadLocal.withInitial(()->
        {
            synchronized(local_summary_all)
            {
                StreamSummary<AlphabetGram> local = new StreamSummary<>(tooKeep*exact_multiplier);
                local_summary_all.add(local);
                return local;
            }
        });
    }
    
    /**
     * Should only be called after {@link #finishHashCount() } has returned. 
     * @param is 
     */
    public void exactCount(InputStream is)
    {
        Set<AlphabetGram> exact_grams = new HashSet<>();

        AlphabetGram cur_gram = getNewGram(gramSize);
        Iterator<Integer> grams = cur_gram.getAlphabet().getIter(is);
        
        long pos = 0;
        while(grams.hasNext())
        {
            int h = cur_gram.push(grams.next());
            h = Integer.remainderUnsigned(h, filterSize);
            if(pos++ >= gramSize && whiteList.contains(h))
                exact_grams.add(cur_gram.clone());
        }


        StreamSummary<AlphabetGram> exact_gram_summary = local_summary.get();
        for(AlphabetGram b : exact_grams)
            exact_gram_summary.offer(b);
    }
    
    
    /**
     * This method is used as a mechanism to count which files a given set of
     * n-grams have occurred in. The map that is passed in will have the given
     * <tt>id</tt> added to each n-gram indicating the file <tt>is</tt>
     * contained the given n-gram. It is only thread-safe if the Sets contained
     * in the map are thread-safe. The map itself will not have any additions or
     * removals performed. The keys of the map are the only n-grams considered.
     *
     * @param is the file under consideration
     * @param id the id associated with this file
     * @param occuredIn the map of sets to store the id if the key (n-gram) was
     * found in this file (is).
     */
    public void incrementConuts(InputStream is, int id, Map<AlphabetGram, Set<Integer>> occuredIn)
    {
        Set<AlphabetGram> exact_grams = new HashSet<>();

        AlphabetGram cur_gram = getNewGram(gramSize);
        Iterator<Integer> grams = cur_gram.getAlphabet().getIter(is);
        
        int pos = 0;
        while(grams.hasNext())
        {
            cur_gram.push(grams.next());
            if(occuredIn.containsKey(cur_gram))
                exact_grams.add(cur_gram.clone());
        }

        for(AlphabetGram b : exact_grams)
            occuredIn.get(b).add(id);
    }
    
    public Map<AlphabetGram, AtomicInteger> finishExactCount()
    {
        Map<AlphabetGram, AtomicInteger> gram_counts = new ConcurrentHashMap<>();
        local_summary_all.stream().forEach(s->
        {
            s.topK(tooKeep*exact_multiplier).forEach(counter->
            {
                AlphabetGram key = counter.getItem();
                int count = (int) (counter.getCount()-counter.getError());
                AtomicInteger toInc = gram_counts.get(key);
                if(toInc == null)//was not present
                {
                    toInc = gram_counts.putIfAbsent(key, new AtomicInteger());
                    toInc = gram_counts.get(key);
                    //now toInc is def not null and an object in the map
                }
                toInc.addAndGet(count);
            });
        });
        
        //anythign <= threshold is not in top-k
        int threshold = gram_counts.values().stream()
                //neg so largest values go first
                .mapToInt(s->-s.get())
                .sorted()
                .map(x->-x)//back to positive values
                .limit(tooKeep).min().getAsInt()-1;//get top k
        gram_counts.keySet()
                .removeIf(gram->gram_counts.get(gram).get() <= threshold);
        
        return gram_counts;
    }
    

    /**
     * Compute a set of hashes for a specified n-gram size
     * @param set the set to fill with count occurrence information
     * @param is the input stream of bytes
     * @param filterSize the size of the table that the hashes will eventually be stored in
     * @param hashStride the hashing-stride to use. Set equal to 1 if you want all hashes. 
     * @param n the n-gram size n
     * @param countOnce {@code true} if a hash should only get counted ounce per
     * input stream, or {@code false} if it should get counted on each
     * occurrence.
     * @throws IOException
     */
    protected void hashNgramSet(IntDoubleMap set, final InputStream is, final int filterSize, final int hashStride, int n, boolean countOnce) throws IOException, InterruptedException
    {
        set.clear();
        AlphabetGram hasher = getNewGram(n);
        
        Iterator<Integer> grams = hasher.getAlphabet().getIter(is);
        
        int pos = 0;
        while(grams.hasNext())
        {
            int h = hasher.push(grams.next());
            pos++;
            h = Integer.remainderUnsigned(h, filterSize);
            if (h % hashStride != 0)
                continue;
            if (pos >= n || !set.isEmpty())
                if (countOnce)
                    set.put(h, 1);
                else
                    set.increment(h, 1);
        }
    }
}

