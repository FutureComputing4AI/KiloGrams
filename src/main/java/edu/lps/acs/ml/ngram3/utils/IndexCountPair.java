/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.utils;

/**
 *
 * @author edraff
 */
public class IndexCountPair implements Comparable<IndexCountPair>
{
    public int index;
    public int count;

    public IndexCountPair(int index, int count)
    {
        this.index = index;
        this.count = count;
    }
    
    @Override
    public int compareTo(IndexCountPair o)
    {
        return Integer.compare(this.count, o.count);
    }
    
}
