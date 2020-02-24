/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import edu.lps.acs.ml.ngram3.utils.FileConverter;
import edu.lps.acs.ml.ngram3.utils.GZIPHelper;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jsat.classifiers.CategoricalData;
import jsat.classifiers.DataPoint;
import jsat.io.DataWriter;
import jsat.io.JSATData;
import jsat.io.LIBSVMLoader;

/**
 *
 * @author edraff
 */
public class HashToData
{
    @Parameter(names={"--good-dir", "-g"}, converter = FileConverter.class)
    File benDir = null;
    @Parameter(names={"--bad-dir", "-b"}, converter = FileConverter.class)
    File malDir = null;
    
    @Parameter(names={"--multi-class", "-mc"}, variableArity = true, converter = FileConverter.class)
    private List<File> classDirs = new ArrayList<>();
    
    @Parameter(names="--lzjd")
    boolean lzjdHash = false;
    
    OutputFormats outputFormat = OutputFormats.LIBSVM;
        
    @Parameter(names={"--out-file", "-o"}, converter = FileConverter.class)
    File outFile;
    
    @Parameter(names={"--hash-file", "-h"}, converter = FileConverter.class)
    File hashFile;
    
    @Parameter(names = "--help", help = true)
    private boolean help;
    
    public enum OutputFormats
    {
        JSAT 
        {
            @Override
            public DataWriter getWriter(OutputStream out, int num_features, CategoricalData predicting) throws IOException
            {
                return JSATData.getWriter(out, new CategoricalData[0], num_features, predicting, JSATData.FloatStorageMethod.BYTE, DataWriter.DataSetType.CLASSIFICATION);
            }
        },
        LIBSVM
        {
            @Override
            public DataWriter getWriter(OutputStream out, int num_features, CategoricalData predicting) throws IOException
            {
                return LIBSVMLoader.getWriter(out, num_features, DataWriter.DataSetType.CLASSIFICATION);
            }
             
        };
        
        public abstract DataWriter getWriter(OutputStream out, int num_features, CategoricalData predicting) throws IOException;
    }
    
    public static void main(String... args) throws IOException
    {
        HashToData main = new HashToData();
        
        JCommander optionParser = JCommander.newBuilder()
            .addObject(main)
            .build();
        optionParser.parse(args);
        
        if (args.length < 3) {
            optionParser.usage();
        }
        main.run();
    }
    
    public void run() throws IOException
    {
        
        
        CategoricalData predicting;
        //Benign and Malicious dir have been specified? OK, assume just 2 classes -benign goes first. 
        if(benDir != null && malDir != null)
        {
            classDirs.clear();
            classDirs.add(benDir);
            classDirs.add(malDir);
            
            predicting = new CategoricalData(2);
            predicting.setOptionName("Benign", 0);
            predicting.setOptionName("Malicious", 1);
        }
        else//Doing MC
        {
            //Sort by folder name to make life consistent
            Collections.sort(classDirs, (File o1, File o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
            predicting = new CategoricalData(classDirs.size());
            for(int i = 0; i < classDirs.size(); i++)
                predicting.setOptionName(classDirs.get(i).getName(), i);
        }
        
        //collect files
        List<List<File>> filesPerClass = new ArrayList<>();
        for(int i = 0; i < classDirs.size(); i++)
        {
            List<File> classFiles = Files.walk(classDirs.get(i).toPath(), FileVisitOption.FOLLOW_LINKS)
                    .filter(p->!p.toFile().isDirectory())
                    .map(p->p.toFile())
                    .collect(Collectors.toList());    
            filesPerClass.add(classFiles);
        }
        
        Featurizer vectorizer;
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(hashFile))))
        {
            vectorizer = Featurizer.getFromStream(dis);
        }
        catch(FileNotFoundException ex)
        {
            ex.printStackTrace();
            System.exit(0);
            return;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            System.out.println("INVALID HASH FILE GIVEN");
            System.exit(0);
            return;
        }
        
        try(DataWriter w = outputFormat.getWriter(new BufferedOutputStream(new FileOutputStream(outFile)), vectorizer.kept, predicting))
        {
            for(int i = 0; i < classDirs.size(); i++)
            {
                final int ID = i;
                filesPerClass.get(i).parallelStream().forEach(f ->
                {
                    try (InputStream is = GZIPHelper.getStream(new FileInputStream(f)))
                    {
                        w.writePoint(new DataPoint(vectorizer.vectorize(is)), ID);
                    }
                    catch (InterruptedException | IOException ex)
                    {
                        Logger.getLogger(HashToData.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }
        }
    }
    
}
