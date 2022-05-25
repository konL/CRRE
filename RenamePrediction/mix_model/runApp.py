from mix_model.keras4bert_loaddata import run

import tensorflow.compat.v1 as tf
tf.enable_eager_execution()


from keras import backend as K
projs={"beam","cassandra","dubbo","flink","hbase","jmeter","storm","tomcat","zeppelin","camel"}
for iter_n in range(1,11):
    print(iter_n)
    for proj in projs:
        print(proj)
        run(proj,iter_n)

        K.clear_session()
        tf.reset_default_graph()

