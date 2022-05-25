#提取文本
import pandas as pd
import re

from nltk import LancasterStemmer
from nltk.corpus import stopwords
import nltk
nltk.download('stopwords')
stop_words = set(stopwords.words('english'))


# df=pd.read_csv("D:\\kon_data\\PYTHON_DATA\\PROJECTS\\FR-Rename\\fr-cm-ref-"+proj+"-jira.csv",header=0,encoding='unicode_escape')
# df=pd.read_csv("D:\\2_secpaper\\data\\"+proj+"_test.csv",header=0,encoding='unicode_escape')
def clean_text(text):
    text = str(text).lower()
    text = re.sub(r"what's", "what is ", text)
    text = re.sub(r"\'s", " ", text)
    text = re.sub(r"\'ve", " have ", text)
    text = re.sub(r"can't", "can not ", text)
    text = re.sub(r"n't", " not ", text)
    text = re.sub(r"i'm", "i am ", text)
    text = re.sub(r"\'re", " are ", text)
    text = re.sub(r"\'d", " would ", text)
    text = re.sub(r"\'ll", " will ", text)
    text = re.sub(r"\'scuse", " excuse ", text)
    text = re.sub('\W', ' ', text)
    text = re.sub('\s+', ' ', text)
    text = text.strip(' ')
    #text=cleanString(text)
    return text
#ZA KWANGU_________________________________
def cleanHtml(text):
    cleanr = re.compile('<.*?>')
    cleantext = re.sub(cleanr, ' ', str(text))
    return cleantext

def cleanPunc(text): #function to clean the word of any punctuation or special characters
    cleaned = re.sub(r'[?|!|\'|"|#]',r'',text)
    cleaned = re.sub(r'[.|,|)|(|\|/]',r' ',cleaned)
    cleaned = cleaned.strip()
    cleaned = cleaned.replace("\n"," ")
    return cleaned

def keepAlpha(text):
    alpha_sent = ""
    for word in text.split():
        alpha_word = re.sub('[^a-z A-Z]+', ' ', word)
        alpha_sent += alpha_word
        alpha_sent += " "
    alpha_sent = alpha_sent.strip()
    return alpha_sent


def removeStopWords(sentence):
    stop_words = set(stopwords.words('english'))
    stop_words.update(['zero','one','two','three','four','five','six','seven','eight','nine','ten','may','also','across','among','beside','however','yet','within'])
    global re_stop_words
    re_stop_words = re.compile(r"\b(" + "|".join(stop_words) + ")\\W", re.I)
    return re_stop_words.sub(" ", sentence)


def stemming(sentence):
    stemmer = LancasterStemmer()
    stemSentence = ""
    for word in sentence.split():
        stem = stemmer.stem(word)
        stemSentence += stem
        stemSentence += " "
    stemSentence = stemSentence.strip()
    return stemSentence


def splitMethodName(text):
    splitted = re.sub('(?!^)([A-Z][a-z]+)', r' \1', text).split()

    cleanedMethod = re.sub(r'[?|!|\'|"|#]', r'', str(splitted))
    cleanedMethod = re.sub(r'[.|,|)|(|\|/]', r' ', cleanedMethod)
    cleanedMethod = cleanedMethod.strip()
    cleanedMethod = cleanedMethod.replace("\n", " ")
    return cleanedMethod
# for indexs in df.index:
#     #是重复的
#     olds=df.loc[indexs, 'oldStmt']
#     news=df.loc[indexs, 'newStmt']
#     print(olds)
#     olds=clean_text(olds)
#     olds=cleanHtml(olds)
#     olds=keepAlpha(olds)
#     olds=removeStopWords(olds)
#     olds=splitMethodName(olds)
#     print(olds)