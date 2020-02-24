/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import com.clearspring.analytics.stream.Counter;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;
import jsat.utils.LongList;

/**
 *
 * @author edraff
 */
public class CounterSet
{
    protected Map<SSEntry,SSEntry> s;

    public CounterSet()
    {
        s = new HashMap<>();
    }
    
    public CounterSet(List<Counter<ByteBuffer>> ss)
    {
        this();
        for(Counter<ByteBuffer> entry : ss)
            this.add(entry.getItem(), entry.getCount(), entry.getError());
    }
    
    public void add(ByteBuffer b, long f, long eps)
    {
        SSEntry entry = new SSEntry(b, f, eps);
        s.put(entry, entry);
    }
    
    public void merge(CounterSet other, int maxSize)
    {
        long m1 = this.s.keySet().stream().mapToLong(e->e.freq).min().orElse(0);
        long m2 = other.s.keySet().stream().mapToLong(e->e.freq).min().orElse(0);
        
        
//        int split = Math.min(this.s.size(), other.s.size());
        int split = maxSize;
        
        LongList freq_count = new LongList();
        
        for(SSEntry entry : this.s.keySet())
        {
            SSEntry found = other.s.remove(entry);
            if(found == null)//was not found
            {
                entry.freq += m2;
                entry.eps += m2;
            }
            else//was found!
            {
                entry.freq += found.freq;
                entry.eps += found.eps;
            }
            
            freq_count.add(entry.freq);
        }
        //now for the leftovers from other
        for(SSEntry entry : other.s.keySet())
        {
            entry.eps += m1;
            entry.freq += m1;
            this.s.put(entry, entry);
            
            freq_count.add(entry.freq );
        }
        
        //wat is the min freq? 
        
        
        //sort high to low
        freq_count.sort((Long o1, Long o2) -> - o1.compareTo(o2));
        //make split find an unambigious cut
        while(split < freq_count.size()-1 && freq_count.get(split-1) == freq_count.get(split))
            split++;
        
        if(freq_count.size() > maxSize)
        {
            long min_freq = freq_count.get(split);
            this.s = this.s.entrySet().stream()
                    .filter(p -> p.getKey().freq >= min_freq)
                    .collect(Collectors.toMap(z -> z.getKey(), z -> z.getKey()));
        }
        else
        {
            this.s = this.s.entrySet().stream()
                    .collect(Collectors.toMap(z -> z.getKey(), z -> z.getKey()));
        }
    }
    
    
    public static CounterSet merge(List<CounterSet> counters, int maxSize)
    {
        Queue<CounterSet> remaining = new ArrayDeque<>(counters);
        
        while(remaining.size() > 1)
        {
            CounterSet a = remaining.poll();
            CounterSet b = remaining.poll();
            a.merge(b, maxSize);
            remaining.add(b);
        }
        
        return remaining.poll();
    }
}
