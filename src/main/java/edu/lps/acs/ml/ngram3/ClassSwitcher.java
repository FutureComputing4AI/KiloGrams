package edu.lps.acs.ml.ngram3;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author rjzak
 */
public class ClassSwitcher {
    static final Map<String, Tuple<String,Class>> mainClasses = new LinkedHashMap<String, Tuple<String,Class>>()
    {{
        put("NGram", new Tuple<>("NGRAM goodware/malware", NGram.class));
        put("DATASET", new Tuple<>("Create dataset", HashToData.class));
    }};
    
    public static String timeDifference(long timeDifference1) {
        double seconds = timeDifference1/1000000000;
        int minutes = (int) (seconds / 60.0);
        seconds = seconds % 60;
        
        int hours = minutes / 60;
        minutes = minutes % 60;
        
        int days = hours / 24;
        hours = hours % 24;
        
        if (days > 0) {
            return String.format("%d days, %02d:%02d:%02d", days, hours, minutes, (int) seconds);
        }
        return String.format("%02d:%02d:%02d", hours, minutes, (int) seconds);
    }
    
    public static void main(String[] args) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        if (args.length < 1) {
            System.out.println("Possible actions:");
            mainClasses.keySet().forEach((name) -> {
                System.out.println("\t" + name + " - " + mainClasses.get(name).a);
            });
        } else {
            if(!mainClasses.containsKey(args[0])) {
                System.out.println(args[0] + " is an unknown option");
                return;
            }
            long start_time = System.nanoTime();
            String[] subOptions = Arrays.copyOfRange(args, 1, args.length);
            Method mainMethod = mainClasses.get(args[0]).b.getMethod("main", String[].class);
            mainMethod.invoke(null/*static method, no reference needed*/, (Object)subOptions);
            double millis = (double)(System.nanoTime() - start_time);
            double elasped_seconds = millis / 1000000000.0;
            String readableTime = timeDifference((long)millis);
            if (elasped_seconds < 60)
                System.out.format("Total execution time %1.4f seconds.%n", elasped_seconds);
            else
                System.out.println("Total execution time " + readableTime + ".");
        }
    }
}
