/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * 
 * @author edraff
 */
public class GZIPHelper
{
    /*
     * Fromt he GZIP RFC 1952 file format specification
     * ID1 (IDentification 1)
     * ID2 (IDentification 2)
     *    These have the fixed values ID1 = 31 (0x1f, \037), ID2 = 139 (0x8b, \213), to identify the file as being in gzip format
     * CM (Compression Method)
     *    This identifies the compression method used in the file. CM = 0-7 are 
     *    reserved. CM = 8 denotes the "deflate" compression method, which is 
     *    the one customarily used by gzip and which is documented elsewhere. 
     */
    public static final byte GZIP_FIRST_BYTE = (byte) GZIPInputStream.GZIP_MAGIC;
    public static final byte GZIP_SECOND_BYTE = (byte) (GZIPInputStream.GZIP_MAGIC >> 8);
    
    public static InputStream getStream(InputStream in) throws IOException
    {
        //we only need to check the first 2 bytes. We will check the CM as well for extra safty
        PushbackInputStream pushBack = new PushbackInputStream(in, 3);
        byte[] headerBytes = new byte[3];
        int bytesRead = 0;
        while(bytesRead != headerBytes.length)
        {
            int read = pushBack.read(headerBytes, bytesRead, headerBytes.length-bytesRead);
            if(read >= 0)
                bytesRead+=read;
            else//read < 0, couldnt read 2 bytes, can't be a gzip stream
            {
                if(bytesRead > 0)
                    pushBack.unread(headerBytes, 0, bytesRead);
                return pushBack;//give back the input stream
            }
        }
        
        //undo whatever we have read anyway
        pushBack.unread(headerBytes, 0, bytesRead);
        //we now have the first 2 bytes, if they match our magic number, we know its a gzip stream (unless the stream JUST HAPPENED to start with that constant
        if(headerBytes[0] == GZIP_FIRST_BYTE && //ID1 is correct
                headerBytes[1] == GZIP_SECOND_BYTE && //ID2 is correct
                headerBytes[2] <= 8 && headerBytes[2] >= 0 //CM is a valid value
                )
            return new GZIPInputStream(pushBack);
        else
            return pushBack;
    }
    
    /**
     * 
     * @param file the file to check if it is a zip file
     * @return {@code true} if the file is a zip file, {@code false} if it is not a zip file
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static boolean isZip(Path file) throws FileNotFoundException, IOException
    {
        InputStream input = Files.newInputStream(file);
        byte[] header = new byte[4];
        input.read(header);
        
        return header[0] == 0x50 && header[1] == 0x4B;
    }
    
}
