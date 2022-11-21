# Data Description
We provide test data (test_data) and training data can be constructed by test data. The strategy adopted in our paper is to use test data from nine of the projects combined into training data to predict one of the remaining projects.
The training set should be used to build your machine learning model. For the test set we provided, it was constructed several times. `oldName` and `newName` were used to construct `label_class`, and `oldStmt_body` and `newStmt_body` were used to filter the data for the input model.
After the filtering is completed, `oldStmt` and `newStmt` are context input and 'edge' and 'newedge' are entity input, both are used as model inputs and `label_class` is the expected output.


|Column|Variable|Definition|Type|Content|
 -:|:-:|:-: |:-:| :-
 |0|label_class|Whether the method name needs to be renamed|Integer|0=No,1=Yes|
 |2|oldname|the historical method name|Text|outDegrees|
 |3|newname|the current method name|Text|getDegrees|
 |4|oldStmt|the historical method(masked name)|Text|`public DataSet<Tuple2<K, Long>> _(){return vertices.join(edges).where(0).equalTo(0).map(new VertexKeyWithOne<K, EV, VV>()).groupBy(0).sum(1);}`|
 |5|newStmt|the current method(masked name)|Text|`public DataSet<Tuple2<K, Long>> _(){return outDegrees().union(inDegrees()).groupBy(0).sum(1);}` |
 |6|edge|the set of the historical entity and its related entity pairs|Text|`<outDegrees,Graph>,<outDegrees,flink.graphs>`|
 |7|newedge|the set of the current entity and its related entity pairs|Text|`<outDegrees,Graph>,<outDegrees,flink.graphs>`|
 |9|oldStmt_body|the historical method body|Text|`{return vertices.join(edges).where(0).equalTo(0).map(new VertexKeyWithOne<K, EV, VV>()).groupBy(0).sum(1);}`|
 |10|newStmt_body|the current method body|Text|`{return outDegrees().union(inDegrees()).groupBy(0).sum(1);}`|
 
 


# Quick Start
(1) Prepare data
**Note that:Preparing data will take a long time, the output data is the test data we provided(test_data).**
1. Collect the renaming data of the project
https://github.com/konL/CRRE/blob/7a9c9eca8c51eaed063ed0852bba26fc3c7cbd87/RenamePrediction_prepocess/src/main/java/dataCollect/HistoryAnalysis.java

2. Generate the old and new versions of the database based on the renaming data in step 1
https://github.com/konL/CRRE/blob/7a9c9eca8c51eaed063ed0852bba26fc3c7cbd87/RenamePrediction_prepocess/src/main/java/dataCollect/createVerDB.java
3. generate test data
- Collect old info and new info from the old and new versions of the database
https://github.com/konL/CRRE/blob/7a9c9eca8c51eaed063ed0852bba26fc3c7cbd87/RenamePrediction_prepocess/src/main/java/dataBuild/createMergeFile_all.java

- Filter data
https://github.com/konL/CRRE/blob/7a9c9eca8c51eaed063ed0852bba26fc3c7cbd87/RenamePrediction/DataUtils/delStmt.py
- generate test data(mask name)
https://github.com/konL/CRRE/blob/7a9c9eca8c51eaed063ed0852bba26fc3c7cbd87/RenamePrediction/DataUtils/createTest.py
https://github.com/konL/CRRE/blob/7a9c9eca8c51eaed063ed0852bba26fc3c7cbd87/RenamePrediction/DataUtils/dropSame.py

3. Generate training data
https://github.com/konL/CRRE/blob/7a9c9eca8c51eaed063ed0852bba26fc3c7cbd87/RenamePrediction/DataUtils/createTraindata.py

(2) Training and prediction

4. Training and prediction
The test data of a project and its corresponding training data are selected for prediction, and the Precision, Recall and F-measure of the project are finally output.
https://github.com/konL/CRRE/blob/7a9c9eca8c51eaed063ed0852bba26fc3c7cbd87/RenamePrediction/mix_model/keras4bert_loaddata.py


# Plugin Tutorial

Recommended Version：IDEA version 2020.3/ JDK11
 Predictable language： Java
 
【Note】This plugin is a demo version based on the algorithm in this article, we will continue to improve this version and release the official version in the future.
 
## Installation

Download the provided zip File, open IDEA, and select File -> Settings -> Plugins -> Install Plugin from Disk...

Select the downloaded zip file to complete the installation

![GIF 2021-10-21 10-20-05](https://user-images.githubusercontent.com/24618393/138200640-bba6f9a0-21a3-4d41-8b17-0676d2e2115b.gif)

## Easy to Use tutorial

#### (1) Git Project preparation

First make sure that the file to be predicted is in a git project that contains the commit history.

git needs to be configured in order for the git command used in the plug-in to take effect.

Add the following in the.gitattributes file

`*.java diff=java`

You can also copy the following complete.gitattributes file contents:

```
# The default behavior, which overrides 'core.autocrlf', is to use Git's
# built-in heuristics to determine whether a particular file is text or binary.
# Text files are automatically normalized to the user's platforms.
* text=auto

# Explicitly declare text files that should always be normalized and converted
# to native line endings.
.gitattributes text
.gitignore text
LICENSE text
Dockerfile text
*.avsc text
*.go text
*.html text
*.java text
*.md text
*.properties text
*.proto text
*.py text
*.sh text
*.xml text
*.yml text
*.java diff=java

# Declare files that will always have CRLF line endings on checkout.
# *.sln text eol=crlf

# Explicitly denote all files that are truly binary and should not be modified.
# *.jpg binary

# Declare files that should be ignored when creating an archive of the
# git repository
.gitignore export-ignore
.gitattributes export-ignore
/gradlew* export-ignore
/gradle export-ignore

```

Then make the configuration take effect in the shell './.gitattributes' or 'bash.gitattributes'

#### (2) Plugin use

Click anywhere in the file to be predicted, click the plug-in "Rename Detection" under EditMenu, 
and the predicted correctable identifier is highlighted. The user can double-click the highlighted identifier, get the identifier name 
recommended by the plug-in, and decide whether to refactor.
![GIF 2021-10-21 10-48-50](https://user-images.githubusercontent.com/24618393/138202862-05a15c9e-5da1-4208-a6ff-10416a9a7123.gif)

