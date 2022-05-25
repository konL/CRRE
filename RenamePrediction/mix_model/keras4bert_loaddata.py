#! -*- coding: utf-8 -*-

import tensorflow as tf

from mix_model.prepocess import clean_text, cleanHtml, keepAlpha, removeStopWords, splitMethodName

config = tf.compat.v1.ConfigProto()
config.gpu_options.allow_growth = True
session = tf.compat.v1.InteractiveSession(config=config)

import pandas as pd
import keras
import os

# 指定第一块GPU可用
os.environ["CUDA_DEVICE_ORDER"] = "PCI_BUS_ID"
os.environ["CUDA_VISIBLE_DEVICES"] = "1"



from bert4keras.tokenizers import Tokenizer
from bert4keras.snippets import sequence_padding, DataGenerator
from sklearn.metrics import classification_report, f1_score, roc_curve, auc
from bert4keras.optimizers import Adam

from mix_model.network import build_bert_model
from mix_model.keras4bert_dataset import load_data

#定义超参数和配置文件
class_nums = 2
maxlen =512
batch_size = 2

config_path = '..\\model\\bert_config.json'
checkpoint_path = '..\\model\\uncased_L-12_H-768_A-12\\bert_model.ckpt'

dict_path = '..\\model\\uncased_L-12_H-768_A-12\\vocab.txt'

tokenizer = Tokenizer(dict_path)






class data_generator(DataGenerator):

    def __iter__(self, random=False):
        batch_token_ids, batch_segment_ids,batch_token_ids2, batch_segment_ids2, batch_labels = [], [], [], [],[]
        for is_end, (oldname, newname,text, text1, oldedge,newedge,changeNum, label) in self.sample(
            random):

            text = clean_text(text)
            text = cleanHtml(text)
            text = keepAlpha(text)
            text = removeStopWords(text)
            text = splitMethodName(text)

            text1 = clean_text(text1)
            text1 = cleanHtml(text1)
            text1 = keepAlpha(text1)
            text1 = removeStopWords(text1)
            text1 = splitMethodName(text1)
            token_ids, segment_ids = tokenizer.encode(text, text1, maxlen=maxlen)
            oldRelation=oldedge.replace(oldname+','," ");
            newRelation = newedge.replace(newname + ',', " ");
            oldRelation=splitMethodName(oldRelation)
            oldRelation=clean_text(oldRelation)
            newRelation=splitMethodName(newRelation)
            newRelation = clean_text(newRelation)
            token_ids_ent, segment_ids_ent = tokenizer.encode(oldRelation,newRelation ,maxlen=maxlen)
            batch_token_ids.append(token_ids)
            batch_segment_ids.append(segment_ids)
            batch_token_ids2.append(token_ids_ent)
            batch_segment_ids2.append(segment_ids_ent)

            batch_labels.append([label])

            if len(batch_token_ids) == self.batch_size or is_end:
                batch_token_ids = sequence_padding(batch_token_ids)
                batch_segment_ids = sequence_padding(batch_segment_ids)
                batch_token_ids2= sequence_padding(batch_token_ids2)
                batch_segment_ids2 = sequence_padding(batch_segment_ids2)

                batch_labels = sequence_padding(batch_labels)

                yield [batch_token_ids, batch_segment_ids,batch_token_ids2,batch_segment_ids2], batch_labels
                batch_token_ids, batch_segment_ids,batch_token_ids2, batch_segment_ids2, batch_labels = [], [], [], [],[]


from tensorflow.keras import backend as K
def recall_(y_true,y_pred):

    true_positive = K.sum(K.round(K.clip(y_true*y_pred, 0, 1)))
    possible_positive = K.sum(K.round(K.clip(y_true, 0, 1)))
    recall=true_positive/(possible_positive+K.epsilon())
    return recall
def precision_(y_true,y_pred):
    true_positive = K.sum(K.round(K.clip(y_true * y_pred, 0, 1)))
    predicted_positive = K.sum(K.round(K.clip(y_pred, 0, 1)))
    precision=true_positive/(predicted_positive+K.epsilon())
    return precision
def f_1(y_true,y_pred):
    def recall(y_true,y_pred):

        true_positive = K.sum(K.round(K.clip(y_true*y_pred, 0, 1)))
        possible_positive = K.sum(K.round(K.clip(y_true, 0, 1)))
        recall=true_positive/(possible_positive+K.epsilon())
        return recall
    def precision(y_true,y_pred):
        true_positive = K.sum(K.round(K.clip(y_true * y_pred, 0, 1)))
        predicted_positive = K.sum(K.round(K.clip(y_pred, 0, 1)))
        precision=true_positive/(predicted_positive+K.epsilon())
        return precision
    precision=precision(y_true,y_pred)
    recall=recall(y_true,y_pred)
    return 2*((precision*recall)/(precision+recall+K.epsilon()))

def run(proj,iter_num):
    train_data, train = load_data(
            '..\\process_data\\train_data\\'+proj+'_train.csv')
    val_data, val = load_data(
        '..\\process_data\\test_data\\nosame\\beam_test.csv')
    test_data, test = load_data(
        '..\\process_data\\test_data\\nosame\\' + proj + '_test.csv')
    print(train['label_class'].value_counts())

    train_data=train.values




    # 转换数据集
    train_generator = data_generator(train_data, batch_size)
    val_generator = data_generator(val_data, batch_size)
    test_generator = data_generator(test_data, batch_size)
    model = build_bert_model(config_path,checkpoint_path,class_nums)
    model.compile(
        loss='sparse_categorical_crossentropy',
        optimizer=Adam(5e-6),
        metrics=['accuracy',f_1,precision_,recall_],
    )

    earlystop = keras.callbacks.EarlyStopping(
        monitor='val_loss',
        patience=2,
        verbose=1,
        mode='min'
        )
    bast_model_filepath = 'v3_best_model.weights'
    checkpoint = keras.callbacks.ModelCheckpoint(
        bast_model_filepath,
        monitor='val_loss',
        verbose=2,
        save_best_only=True,
        mode='min'
        )



    model.fit_generator(

        train_generator.forfit(),
        steps_per_epoch=len(train_generator)/batch_size,
        epochs=20,

        validation_data=test_generator.forfit(),
        validation_steps=len(test_generator)/batch_size,
        shuffle=True,
        verbose=1,

        callbacks=[earlystop,checkpoint]
    )
    model.load_weights('v3_best_model.weights')
    test_pred = []


    for x, y in test_generator:
        print("x=" + str(x))

        p = model.predict(x).argmax(axis=1)
        test_pred.extend(p)

        print("predict="+str(p))

    test_true = test["label_class"].tolist()

    print("项目=",proj)
    fp = 0
    tp = 0
    fn = 0
    tn = 0

    for i in range(len(test_true)):
        pred = int(test_pred[i])
        if ((test_true[i] == pred) & (test_true[i] == 0)):
            tn = tn + 1
        if ((test_true[i] == pred) & (test_true[i] == 1)):
            tp = tp + 1
        if ((test_true[i] != pred) & (test_true[i] == 0)):
            fp = fp + 1
        if ((test_true[i] != pred) & (test_true[i] == 1)):
            fn = fn + 1

    print(tp, fp, tn, fn)
    precision=0
    recall=0
    f=0
    try:

        precision = tp / (tp + fp)
        recall = tp / (tp + fn)
        f=2 * ((precision * recall) / (precision + recall))

    except:
        precision=0
        recall=0
        f=0
    p1 = precision
    r1 = recall
    f1 = f


    print("f1:", f1)
    print("precision:", p1)
    print("recall:", r1)
    print(classification_report(test_true, test_pred))







    changeNum=test['changeNum'].tolist()

    fp = 0
    tp = 0
    fn = 0
    tn = 0


    for i in range(len(test_true)):
        pred = int(test_pred[i])
        if ((test_pred[i] == 0) & (changeNum[i] > 0)):
            test_pred[i] = 1
            pred = 1

        if ((test_true[i] == pred) & (test_true[i] == 0)):
            tn = tn + 1
        if ((test_true[i] == pred) & (test_true[i] == 1)):
            tp = tp + 1
        if ((test_true[i] != pred) & (test_true[i] == 0)):
            fp = fp + 1
        if ((test_true[i] != pred) & (test_true[i] == 1)):
            fn = fn + 1


    print(tp, fp, tn, fn)

    try:
        precision = tp / (tp + fp)
        recall = tp / (tp + fn)
        f=2 * ((precision * recall) / (precision + recall))
    except:
        precision=0
        recall=0
        f=0

    print("f1:", f)
    print("precision:", precision)
    print("recall:", recall)

    dict = {"P-model": [p1, r1, f1], "post": [precision, recall, f],"model": [proj,proj,proj]}
    df = pd.DataFrame(dict)

    df.to_csv("..\\Result\\G"+str(iter_num)+"_all.csv",header=False, mode='a' )


