# KiloGrams

This is the java code implementing the KiloGrams algorithm, from out paper [_KiloGrams: Very Large N-Grams for Malware Classification_](https://arxiv.org/abs/1908.00200). Using it, you can extract the top-_k_ largest _n_-grams from a corpus using a fixed amount of memory, for large values of _k_ and n. In our original paper, we tested with _k_ up to 8192, which took the same time or less than processing _k_=6 grams. 

This is research code, and comes with no warranty or support. 


## Quick Start

You can use this code to create a dataset based on the top-_k_ _n_-grams. To do so, after building the KiloGrams code, you can run a comand like this:

```
java -Xmx10G -jar Kilograms-1.0-jar-with-dependencies.jar NGram -n 8 -k 1000 -g <path to goodware> -b <path to malware> -o grams.dat
```
The top-_k_ ngrams are saved in grams.dat, a binary formated file. See NGram.java or Featurizer.java source code for the nature of the binary format and how to parse it if you want to know the n-grams. If you use a value of _n_ > 8, we recommend you add the hashing-stride option with `-hs`. For example, if you want _n_=1024 grams, we would use `-hs 256`.  

To create a dataset from the above code, you can use the following command:
```
java -Xmx10G -jar Kilograms-1.0-jar-with-dependencies.jar DATASET  -g <path to goodware> -b <path to malware> -h grams.dat -o data.libsvm
```

By default, this will produce a file using the libsvm format. Scikit-learn can [read this](https://scikit-learn.org/stable/modules/generated/sklearn.datasets.load_svmlight_file.html). 

If you have a machine with a very large number of cores or very large files, you may want to increase the max memory for Java, depending on your JVM used.

The folders given as input do not have to be executables, or even benign/malicious. They can be any kind of files, and the code will process byte n-grams. The `DATASET` creation step also supports multi-class problems by using the `-mc <path to class 0> <path to class 1> ... <path to class C>` flag instead of `-b` and `-g`. 

## Citations

If you use the Kilogram algorithm or code, please cite our work! 

```
@inproceedings{Kilograms_2019,
author = {Raff, Edward and Fleming, William and Zak, Richard and Anderson, Hyrum and Finlayson, Bill and Nicholas, Charles K. and Mclean, Mark},
booktitle = {Proceedings of KDD 2019 Workshop on Learning and Mining for Cybersecurity (LEMINCS'19)},
title = {{KiloGrams: Very Large N-Grams for Malware Classification}},
url = {https://arxiv.org/abs/1908.00200},
year = {2019}
}
```

## Contact 

If you have questions, please contact 

Mark Mclean <mrmclea@lps.umd.edu>
Edward Raff <edraff@lps.umd.edu>
Richard Zak <rzak@lps.umd.edu>
