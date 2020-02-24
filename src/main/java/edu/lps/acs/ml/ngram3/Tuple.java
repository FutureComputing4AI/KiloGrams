/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

/**
 *
 * @author rjzak
 */
public class Tuple<T,P> {
    public T a;
    public P b;
    
    public Tuple() {
        a = null;
        b = null;
    }
    
    public Tuple(T l, P r) {
        a = l;
        b = r;
    }
}
