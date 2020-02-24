/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lps.acs.ml.ngram3.utils;

import com.beust.jcommander.IStringConverter;
import java.io.File;

/**
 *
 * @author edraff
 */
public class FileConverter implements IStringConverter<File> {
  @Override
  public File convert(String value) {
    return new File(value);
  }
}