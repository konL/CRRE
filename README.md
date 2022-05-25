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
