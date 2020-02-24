/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.utils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author edraff
 */
public class RabinKarpHashTest
{
    
    public RabinKarpHashTest()
    {
    }
    
    @BeforeClass
    public static void setUpClass()
    {
    }
    
    @AfterClass
    public static void tearDownClass()
    {
    }
    
    @Before
    public void setUp()
    {
    }
    
    @After
    public void tearDown()
    {
    }

    @Test
    public void testRollingHash()
    {
        
        RabinKarpHash rkh = new RabinKarpHash(4);
        
        int[] vals = new int[256];
        //priming
        for(int i = 0; i < 3; i++)
            rkh.pushByteHash((byte)i);
        //setting values
        for(int i = 3; i < 256; i++)
            vals[i] = rkh.pushByteHash((byte)i);
        for(int i = 0; i < 3; i++)
            vals[i] = rkh.pushByteHash((byte)i);
        
        int pos = 3;
        for(int i = 0; i < 100000; i++)
        {
            int h = rkh.pushByteHash((byte) pos);
            assertEquals(vals[pos], h);
            
            pos = (pos + 1) % 256;
        }
    }
    
}
