
import pandas as pd
#两个任务
#1.去掉空context
#2.去掉oldStmt=newStmt（context）

def delStmt_file(proj):
    data_df=pd.read_csv("..\\raw_data\\context\\"+proj+"_merge.csv",header=0,encoding="unicode_escape")

    #根据正则式匹配的函数
    def isMatch( s: str, p: str) -> bool:
        m, n = len(s) + 1, len(p) + 1
        dp = [[False] * n for _ in range(m)]
        dp[0][0] = True
        for j in range(2, n, 2):
            dp[0][j] = dp[0][j - 2] and p[j - 1] == '*'
        for i in range(1, m):
            for j in range(1, n):
                dp[i][j] = dp[i][j - 2] or dp[i - 1][j] and (s[i - 1] == p[j - 2] or p[j - 2] == '.') \
                    if p[j - 1] == '*' else \
                    dp[i - 1][j - 1] and (p[j - 1] == '.' or s[i - 1] == p[j - 1])
        return dp[-1][-1]
    #获取除了名字以外的context：把标识符名字去掉--》bodyStmt
    def delName(x,k_index,isOld):

        classIndex=isMatch(x, '.* *class *.*{.*')
        interfaceIndex = isMatch(x, '.* *interface *.*{.*')
        enumIndex = isMatch(x, '.* *enum *.*{.*')
        assignIndex = isMatch(x, '.* *= *.*{.*')
        index = x.find("{")

        if index != -1 and ((not classIndex) and (not interfaceIndex) and (not enumIndex)and(not assignIndex) ):
            # method
            x = x[index:]
        else:
            if assignIndex:
                equidx = x.find("=")
                if x[0:equidx].find("{") != -1:
                    x = x[index:]
            else:
                if isOld:
                    name = data_df.iloc[k_index]['oldname']
                    # print(name,x)
                else:
                    name = data_df.iloc[k_index]['newname']
                print(name)
                x = x.replace(name, "_")


        return x

    #处理
    data_df['oldStmt_body'] = data_df["oldStmt"].apply(lambda x: delName(x,data_df.loc[data_df['oldStmt']==x].index[0],True))
    data_df['newStmt_body'] = data_df["newStmt"].apply(lambda x: delName(x,data_df.loc[data_df['newStmt']==x].index[0],False))
   # 删除空方法体
    data_df.drop(data_df[data_df['oldStmt_body']=="{}"].index, inplace=True)
    data_df.drop(data_df[data_df['newStmt_body']=="{}"].index, inplace=True)
    data_df.drop(data_df[data_df['oldStmt_body']=="{"].index, inplace=True)
    data_df.drop(data_df[data_df['newStmt_body']=="{"].index, inplace=True)


    #删除context不变化相同的，一般全是label=0
    data_df.drop(data_df[data_df['oldStmt_body'] == data_df['newStmt_body']].index, inplace=True)
    data_df.reset_index(drop=True, inplace=True)
    data_df.to_csv("..\\raw_data\\context\\"+proj+'_merge_refine.csv')


proj="camel"
delStmt_file(proj)