



projs=["dubbo","flink","cassandra","storm","tomcat","jmeter","zeppelin","beam","hbase","camel"]

print(projs)
import pandas as pd
for i in range(len(projs)):
    cur=projs[i]
    train_list = []
    data = pd.DataFrame()
    train_list.extend(projs[:i])
    train_list.extend(projs[i+1:])

    print(train_list)
    for proj in train_list:
        data_df=pd.read_csv(  '..\\process_data\\test_data\\nosame\\' + proj + '_test.csv',header=0)

        data = pd.concat([data, data_df],sort=False)
    print(len(data))
    print(data.head())
    data.to_csv("..\\process_data\\train_data\\"+cur+"_train.csv")




