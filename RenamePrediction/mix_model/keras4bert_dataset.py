#! -*- coding: utf-8 -*-

import pandas as pd

def load_data(filename):
        """加载数据
        """
        df = pd.read_csv(filename,  encoding='unicode_escape',header=0)

        return df[['oldname','newname', 'oldStmt', 'newStmt', 'edge', 'newedge', 'changeNum', 'label_class']].values, \
               df[['oldname', 'newname','oldStmt', 'newStmt', 'edge', 'newedge', 'changeNum', 'label_class']]



