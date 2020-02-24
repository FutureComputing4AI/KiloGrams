/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.alphabet;

/**
 *
 * @author edraff
 */
public interface AlphabetGram extends Comparable<AlphabetGram>
{
    
    public int size();
    
    public int push(int a);
    
    /**
     *
     * @param pos the position in the current gram to obtain the value of
     * @return the character at the current position of the gram
     */
    public int get(int pos);
    
    public int getUnsigned(int pos);
    
    public AlphabetGram clone();

    @Override
    default int compareTo(AlphabetGram other)
    {
        for(int i = 0; i < Math.min(this.size(), other.size()); i++)
        {
            int cmp = Integer.compare(this.get(i), other.get(i));
            if(cmp != 0)
                return cmp;
        }
        
        return Integer.compare(this.size(), other.size());
    }

    public Alphabet getAlphabet();
    
    
}
