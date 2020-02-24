/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.clearspring.analytics.stream.Counter;
import com.clearspring.analytics.stream.StreamSummary;
import com.google.common.collect.AbstractSequentialIterator;
import com.google.common.collect.Ordering;
import edu.lps.acs.ml.ngram3.utils.FileConverter;
import edu.lps.acs.ml.ngram3.utils.GZIPHelper;
import edu.lps.acs.ml.ngram3.utils.IndexCountPair;
import edu.lps.acs.ml.ngram3.utils.HasherConverter;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.BinaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jsat.utils.IntDoubleMap;
import jsat.utils.IntList;
import jsat.utils.IntSet;
import jsat.utils.SystemInfo;

/**
 *
 * @author edraff
 */
public class NGram
{
    /**
     * This is the default buffer size that will be used for reading in bytes.
     * Should be greater than the n-gram size.
     */
    private static int BUFFER_INIT_SIZE =  1024*10;
    public static final ThreadLocal<byte[]> localBuffers = ThreadLocal.withInitial(()->new byte[BUFFER_INIT_SIZE]);
    public static final ThreadLocal<IntDoubleMap> localSets = ThreadLocal.withInitial(()->new IntDoubleMap());

    @Parameter(names="--exact-pass", description="Option to perform a 2nd pass over the data to determin exact n-gram values")
    boolean exact_pass = false;
    
    @Parameter(names={"-em"}, description="Keeplist size multiplier used for exact pass")
    int exact_multiplier = 2;
    
    @Parameter(names="--lzjd", description="Option to use LZJD")
    boolean lzjdHash = false;
    
    @Parameter(names="--absolute-counts", description="If set to true, count n-grams based on the absolute number of times they occur. Default is to only count once per file.")
    boolean countEachOccurrence = false;
    
    @Parameter(names={"--min-count", "-mc"}, description="Minimum number of counts an n-gram must have to be considered for retainment. "
            + "This will not increase the number of n-grams \"too-keep\", but may reduce them. ")
    int minCount = 0;
    
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
    @Parameter(names={"--good-dir", "-g"}, converter = FileConverter.class, required=true, description="Directory of goodware")
    File benDir;
    @Parameter(names={"--bad-dir", "-b"}, converter = FileConverter.class, required=true, description="Directory of malware")
    File malDir;
    @Parameter(names={"--out", "-o"}, converter = FileConverter.class, required=true, description="Output file")
    File outDir;
    
    @Parameter(names={"--hasher", "-h"}, converter = HasherConverter.class, description="Rolling hash method to use")
    NGramHashOption hashMethod = NGramHashOption.RABIN_KARP;
    
    @Parameter(names = "--help", help = true)
    private boolean help;
    
    public static void main(String... args) throws IOException
    {
        NGram main = new NGram();
        
        JCommander optionParser = JCommander.newBuilder()
            .addObject(main)
            .build();
        
        try {
            optionParser.parse(args);
        } catch(ParameterException ex) {
            optionParser.usage();
            System.exit(1);
        }
        
        if (args.length < 3) {
            optionParser.usage();
        }
        
        BUFFER_INIT_SIZE = Math.max(BUFFER_INIT_SIZE, main.gramSize*10);
        
        if(main.gramSize > 0 && main.lzjdHash)
        {
            System.out.println("Can not use n-grams and LZJD options simultaniously. Please pick one");
            System.exit(1);
        }
        else if(main.gramSize == 0 && !main.lzjdHash)
        {
            System.out.println("n-gram size must be set using the -n option");
            System.exit(1);
        }
        else if(main.exact_pass && main.lzjdHash)
        {
            System.out.println("Exact pass is not currently compatible with lzjd-pass");
            System.exit(1);
        }
        else if(main.filterSize <= main.tooKeep)
        {
            System.out.println("Filter size must be larger than the desired number of n-grams");
            System.exit(1);
        }
        
        //Allow 0 to be rolled into 1 so that scripts can have an easier time
        if(main.hashStride == 0)
            main.hashStride = 1;
        else if(main.hashStride < 0)//but dont allow negative, thats crazy
            throw new RuntimeException("Hash-stride must be non-negative, not " + main.hashStride);
        
        main.run();
    }
    
    public void run() throws IOException
    {        
        long start, end;
        System.out.println("Collecting good files...");
//        Files.walkFileTree(benDir.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, fileVisitor);
        List<File> allGoodFiles = Files.walk(benDir.toPath(), FileVisitOption.FOLLOW_LINKS)
                .parallel()
                .filter(p -> !Files.isDirectory(p))
                .map(p -> p.toFile())
                .collect(Collectors.toList());
        System.out.println("Collecting bad files...");
//        Files.walkFileTree(malDir.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, fileVisitor);
        List<File> allBadFiles = Files.walk(malDir.toPath(), FileVisitOption.FOLLOW_LINKS)
                .parallel()
                .filter(p->!Files.isDirectory(p))
                .map(p->p.toFile())
                .collect(Collectors.toList());
        List<File> allFiles = new ArrayList<>(allBadFiles.size() + allGoodFiles.size());
        allFiles.addAll(allGoodFiles);
        allFiles.addAll(allBadFiles);
//        List<File> allFiles = new ArrayList<>(queuOfAllFiles);
        
        System.out.println("Starting Hashing of " + allFiles.size() + " files...");
        final AtomicIntegerArray countIndex = new AtomicIntegerArray(filterSize);
        
        
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
        List<int[][]> allLocalBuffers = new ArrayList<>();
        ThreadLocal<int[][]> threadLocalBuffer = ThreadLocal.withInitial(()->
        {
            int[][] b = new int[2][write_buffer_size];
            synchronized(allLocalBuffers)
            {
                allLocalBuffers.add(b);
            }
            return b;
        });
        
        allFiles.parallelStream().forEach(f->
        {
            try(InputStream is = GZIPHelper.getStream(new FileInputStream(f)))
            {
                //Originally, we re-used a set for all data. Some kind of logic 
                //flaw / bad file caused this to become MASSIVe (100MB+), which
                //then caused needless overhead for all subsequent files. 
                //Replaced with a new map on every instance. Need to debug later
                //why the map got so absurdley large, as error log showed sizes 
                //larger than ever should have been needed. 
//                IntDoubleMap set = localSets.get();
                IntDoubleMap set = new IntDoubleMap();
                int[][] localBuffs = threadLocalBuffer.get();
                int[] wb_count = localBuffs[0];
                int[] wb_id = localBuffs[1];
                
                hashNgramSet(set, is, filterSize, hashStride, gramSize, lzjdHash, hashMethod, !countEachOccurrence);
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
            catch (Exception ex)
            {
                localSets.set(new IntDoubleMap());
                ex.printStackTrace();
                Logger.getLogger(NGram.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        
        //now push out remaining counts in every buffer
        allLocalBuffers.parallelStream().forEach(b ->
        {
            for(int i = 0; i < write_buffer_size; i++ )
                countIndex.addAndGet(b[1][i], b[0][i]);
        });
        
        System.out.println("Creating frequen hash-gram set");
        //Now count the most frequent
        Iterator<IndexCountPair> countIterator = new AbstractSequentialIterator<IndexCountPair>(new IndexCountPair(0, countIndex.get(0)))
        {
            @Override
            protected IndexCountPair computeNext(IndexCountPair t)
            {
                if(t.index == countIndex.length()-1)
                    return null;
                //MUST return a new object for each index in order for Ordering.natural code to work
                return new IndexCountPair(t.index+1, countIndex.get(t.index+1));
            }
        };
        
        final int[] selectedFPIndexes = Ordering.natural().greatestOf(countIterator, tooKeep)
                .stream()
                //filter out things that could not possibly meet our min-count requirements
                .filter(t -> t.count >= minCount) 
                .mapToInt(t -> t.index)
                .sorted().toArray();
        final Map<Integer, Integer> oldIndx = new HashMap<>(selectedFPIndexes.length);
        for(int i = 0;i < selectedFPIndexes.length; i++)
            oldIndx.put(selectedFPIndexes[i], i);
        
        if(!exact_pass || lzjdHash)//write out our set of hashes
        {
            try(DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outDir))))
            {
                dos.writeUTF("HASH_GRAM_S");
                dos.writeInt(gramSize);
                dos.writeInt(filterSize);
                dos.writeInt(hashMethod.ordinal());
                dos.writeBoolean(countEachOccurrence);
                dos.writeInt(tooKeep);
                for(int i = 0; i < selectedFPIndexes.length; i++)
                    dos.writeInt(selectedFPIndexes[i]);
            }
            return;
        }
        //else we want to perform a 2nd exact pass over the n-grams
        IntSet whiteList = new IntSet(IntList.view(selectedFPIndexes, selectedFPIndexes.length));
        
        List<StreamSummary<ByteBuffer>> local_summary_all = new ArrayList<>();
        ThreadLocal<StreamSummary<ByteBuffer>> local_summary = ThreadLocal.withInitial(()->
        {
            synchronized(local_summary_all)
            {
                StreamSummary<ByteBuffer> local = new StreamSummary<>(tooKeep*exact_multiplier);
                local_summary_all.add(local);
                return local;
            }
        });
        
        System.out.println("Perfoming 2nd Pass to get exact n-grams");
        allFiles.parallelStream().forEach((File f)->
        {
            try(InputStream is = GZIPHelper.getStream(new FileInputStream(f)))
            {
                Set<ByteBuffer> exact_grams = new HashSet<>();
                byte[] buffer = localBuffers.get();
                
                
                int read = 0;
                ByteHasher hasher = hashMethod.getNewHash(gramSize);

                int fails = 0;
                int pos = 0;
                while((read = is.read(buffer, pos, buffer.length-pos)) >= 0 && fails < 5)
                {
                    if(read == 0)//You read 0 bytes? WTF
                    {
                        fails++;
                        Thread.sleep(fails);
                        continue;
                    }
                    read += pos;//now read defins the END position of the buffer that is valid
                    for(/*pos already defined*/; pos < read; /*pos incremented in loop body*/)
                    {
                        int h = hasher.pushByteHash(buffer[pos++]);
                        h = Integer.remainderUnsigned(h, filterSize);
                        if(whiteList.contains(h) && pos >= gramSize)//This might be one of the true top-k items
                        {
                            byte[] exact_gram = Arrays.copyOfRange(buffer, pos-gramSize, pos);
                            
                            //Commented out, useful code for debugging purposes 
//                            int h2 = hasher.hash(exact_gram);
//                            h2 = Integer.remainderUnsigned(h2, filterSize);
//                            
//                            if(h2 != h)
//                            {
//                                System.out.println("CONSISTENCY ERROR");
//                            }
                            
                            exact_grams.add(ByteBuffer.wrap(exact_gram));
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
                        //Comment out code is useful for debugging
    //                    Arrays.fill(buffer, gramSize-1, buffer.length, (byte)0);
                    }
                }
                
                StreamSummary<ByteBuffer> exact_gram_summary = local_summary.get();
                for(ByteBuffer b : exact_grams)
                    exact_gram_summary.offer(b);
                
            }
            catch (Exception ex)
            {
                localSets.set(new IntDoubleMap());
                ex.printStackTrace();
                Logger.getLogger(NGram.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        
        CounterSet exact_gram_summary = local_summary_all.stream().parallel()
                .map(z->new CounterSet(z.topK(tooKeep*exact_multiplier)))
                .reduce((CounterSet t, CounterSet u) ->
        {
            t.merge(u, tooKeep*exact_multiplier);
            return t;
        }).get();
        
        int k = 0;
        
//        for(SSEntry top_k : exact_gram_summary.s.keySet())
//        {
//            
//            System.out.print((top_k.freq-top_k.eps) + ": ");
//            ByteBuffer b = top_k.b;
//            System.out.println(new String(b.array()));
//            System.out.println();
//        }

        //sort by lower-bound counts and keep top tooKeep (might have slightly more in the case of ties during merging)
        List<SSEntry> final_grams = exact_gram_summary.s.keySet().stream().parallel()
                .sorted((SSEntry o1, SSEntry o2) -> -Long.compareUnsigned(o1.freq-o1.eps, o2.freq-o2.eps))
                //make sure that we know you occcured at least minCount times to be kept
                .filter(t -> (t.freq-t.eps) >= minCount)
                .limit(tooKeep).collect(Collectors.toList());

        int gramsWritten = 0;
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outDir))))
        {
            dos.writeUTF("EXCT_GRAM_S");
            dos.writeInt(gramSize);
            dos.writeInt(filterSize);
            dos.writeInt(hashMethod.ordinal());
            dos.writeBoolean(countEachOccurrence);
            dos.writeInt(final_grams.size());
            for (SSEntry entry : final_grams) 
            {
//                System.out.println(new String(entry.b.array()).replace("\n", "\\n").replace("\r", "\\r"));
                for (int i = 0; i < gramSize; i++) 
                {
                    //Below code useful for debugging the counts of the found n-grams
//                    if(i == 0)
//                    {
////                        System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(entry.b.array()));
//                        System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(entry.b.array()) + ": " + entry.freq + " - " + entry.eps);
//                    }
                    dos.writeByte(entry.b.get(i));
                }
                gramsWritten++;
            }
        }
        if (gramsWritten != tooKeep) {
            System.out.println("Collected " + gramsWritten + " out of the requested " + tooKeep + " " + gramSize + "grams.");
        }
    }

    /**
     * Compute a set of hashes for a specified n-gram size
     * @param set the set to fill with count occurrence information
     * @param is the input stream of bytes
     * @param filterSize the size of the table that the hashes will eventually be stored in
     * @param hashStride the hashing-stride to use. Set equal to 1 if you want all hashes. 
     * @param n the n-gram size n
     * @param doLZJD
     * @param hashMethod
     * @param countOnce {@code true} if a hash should only get counted ounce per
     * input stream, or {@code false} if it should get counted on each
     * occurrence.
     * @throws IOException
     */
    protected static void hashNgramSet(IntDoubleMap set, final InputStream is, final int filterSize, final int hashStride, int n, boolean doLZJD, NGramHashOption hashMethod, boolean countOnce) throws IOException, InterruptedException
    {
        set.clear();
        byte[] buffer = localBuffers.get();
        int read = 0;
        ByteHasher hasher;
        if(doLZJD)
            hasher = new LZJDHasher();
        else
            hasher = hashMethod.getNewHash(n);
        
        int fails = 0;
        while((read = is.read(buffer)) >= 0 && fails < 5)
        {
            if(read == 0)//You read 0 bytes? WTF
            {
                fails++;
                Thread.sleep(fails);
            }
            
            for(int pos = 0; pos < read; pos++)
            {
                int h = hasher.pushByteHash(buffer[pos]);
                h = Integer.remainderUnsigned(h, filterSize);
                if(h % hashStride != 0)
                    continue;
                if(pos >= n || !set.isEmpty())
                    if(countOnce)
                        set.put(h, 1);
                    else
                        set.increment(h, 1);
            }
        }
    }
}

