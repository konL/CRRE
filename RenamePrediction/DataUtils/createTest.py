import pandas as pd

def mask(proj):
    data_df=pd.read_csv("..\\raw_data\\context\\"+proj+"_merge_refine.csv")
    def delName(x,index,isOld):
        if isOld:
            name=data_df.iloc[index]['oldname']
        else:
            name = data_df.iloc[index]['newname']
        x=x.replace(name,"_",1)
        print(x)




        return x

    data_df['oldStmt'] = data_df["oldStmt"].apply(lambda x: delName(x,data_df.loc[data_df['oldStmt']==x].index[0],True))
    data_df['newStmt'] = data_df["newStmt"].apply(lambda x: delName(x,data_df.loc[data_df['newStmt']==x].index[0],False))
    data_df.to_csv("..\\process_data\\test_data\\" + proj + "_test.csv")


proj="camel"
mask(proj)