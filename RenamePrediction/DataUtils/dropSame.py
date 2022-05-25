import pandas as pd
projs={"beam","cassandra","dubbo","flink","hbase","jmeter","storm","tomcat","zeppelin","camel"}
for proj in projs:
    df=pd.read_csv("..\\process_data\\test_data\\"+proj+"_test.csv",header=0,encoding="unicode_escape")
    df.drop_duplicates(["label_class"	,"type","oldname","newname","oldStmt","newStmt"
],keep='first', inplace=True)

    df.to_csv("C:\\project\\MethodPrediction_All_ID\\process_data\\test_data\\nosame\\"+proj+"_test.csv",index=False)

