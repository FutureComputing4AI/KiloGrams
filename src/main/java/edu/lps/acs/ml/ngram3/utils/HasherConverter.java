/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.utils;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import edu.lps.acs.ml.ngram3.NGramHashOption;
import java.util.Arrays;

/**
 *
 * @author edraff
 */
public class HasherConverter implements IStringConverter<NGramHashOption> 
{
    @Override
    public NGramHashOption convert(String value) {
        NGramHashOption convertedValue = NGramHashOption.valueOf(value);

        if (convertedValue == null)
        {
            throw new ParameterException("Value " + value + "can not be converted to NGramHashOption. "
                    + "Available values are: " + Arrays.toString(NGramHashOption.values()));
        }
        return convertedValue;
    }
}
