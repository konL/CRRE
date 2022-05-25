package dataCollect;

import com.csvreader.CsvReader;
import detectId.Trace.SyncPipe;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class createVerDB {
    static String group="3";
    public static void main(String[] args) throws Exception {
        //single-process:
        //ProjectCommit("abdera");

        //batch-process:生成前十个项目的versionDB
        String[] projs={"flink","camel"};
        for(String proj:projs) {                    //遍历File[]数组

           ProjectCommit(proj);

        }


    }

    private static void ProjectCommit(String project) throws Exception {
        String csvPath="..\\Renaming\\"+project+".csv";
        try {

            CsvReader csvReader = new CsvReader(csvPath);
            while (csvReader.readRecord()){
                String locHis=csvReader.get(2);
                String his=csvReader.get(3);
                String[] hisId=his.split("<-");
                String[] loc=locHis.split("<=");
                //获取每个id每次变化所在文件的历史版本和当前版。
                for(int i=0;i<hisId.length-1;i++){
                    String change=hisId[i]+"<-"+hisId[i+1];
                    String curCom=csvReader.get(4+i);
                    genFile(project,loc[i],curCom,change);
                }




            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }



    private static void genFile(String project, String loc, String curCom, String change) throws Exception {
        String[] srcAnddst=loc.split("<-");
        change=change.replace("<-","_");
        //获取FileName和前面的文件夹名字
        String[] data_n=srcAnddst[0].split("\\\\");
        String fileName_n=data_n[data_n.length-1];

        String[] data_o=srcAnddst[1].split("\\\\");
        String fileName_o=data_o[data_o.length-1];

        //没建立文件夹先建立文件夹
        File file1 =new File("..\\versionDB\\raw_project\\"+project+"\\"+project+"_new");
        if  (!file1 .exists()  && !file1 .isDirectory())
        {
            System.out.println("//不存在");
            file1 .mkdirs();
        } else
        {
            System.out.println("//目录存在");
        }
        File file2 =new File("C:\\project\\MethodPrediction_All_ID\\versionDB\\raw_project\\"+project+"\\"+project+"_old");
        if  (!file2 .exists()  && !file2 .isDirectory())
        {
            System.out.println("//不存在");
            file2 .mkdirs();
        } else
        {
            System.out.println("//目录存在");
        }

        //newCom读取当前的代码
        generateNew(srcAnddst[0],curCom,fileName_n,"..\\versionDB\\raw_project\\"+project+"\\"+project+"_new\\"+curCom+"_"+change+"_"+fileName_n);

        //oldcam读取记录代码的上一个版本

        generateOld(srcAnddst[1],fileName_o,"..\\versionDB\\raw_project\\"+project+"\\"+project+"_old\\"+curCom+"_"+change+"_"+fileName_o);

    }

    private static void generateOld(String location,String fileName, String output) throws Exception {

        String[] data=location.split("\\\\");
        //projectdir表示git proj所在的文件位置
        String projdir=data[0]+"\\\\"+data[2]+"\\\\"+data[4]+"\\\\"+data[6]+"\\\\"+data[8]+"\\\\"+data[10];
        String proj=data[10];
        //先回退到版本old,再回退到上一个版本
        ExecuteCommand(projdir,"git reset --hard \"HEAD^\"","..\\versionDB\\raw_project\\"+proj+"\\"+proj+"_log.txt");
        copyTo(location,output);

    }

    private static void generateNew(String location, String curCom,String fileName,String output) throws Exception {

        String[] data=location.split("\\\\");
        //projectdir表示git proj所在的文件位置
        String projdir=data[0]+"\\\\"+data[2]+"\\\\"+data[4]+"\\\\"+data[6]+"\\\\"+data[8]+"\\\\"+data[10];
        String proj=data[10];
        ExecuteCommand(projdir,"git reset --hard "+curCom,"C:\\project\\MethodPrediction_All_ID\\versionDB\\raw_project\\"+proj+"\\"+proj+"_log.txt");
        copyTo(location,output);
    }

    private static void copyTo(String location, String output) throws IOException {
        Boolean validFile=match(output);
        //读取
        File in=new File(location);
        //写入
        File out=new File(output);
        if(in.exists() && !validFile) {

            BufferedReader br = new BufferedReader(new FileReader(in));
            BufferedWriter bw = new BufferedWriter(new FileWriter(out));

            String line = "";
            while ((line = br.readLine()) != null) {
                bw.write(line);
                bw.newLine();
            }

            br.close();
            bw.close();
        }
    }

    private static Boolean match(String fileName) {
        fileName=fileName.substring(0,fileName.lastIndexOf("."));
        //假设任意一个 true
        boolean isBad=fileName.contains("<")||fileName.contains(">")||fileName.contains(".")||fileName.contains("\"")||fileName.contains("{")||fileName.contains("}")
                ||fileName.contains("(")||fileName.contains(")")||fileName.contains("<-<-");

        return isBad;
    }



    public static String ExecuteCommand(String projectdir,String cmd,String output) throws Exception
    {
        String final_com=null;
        System.out.println("projectdir:"+projectdir);

        String[] command =
                {
                        "cmd",
                };
        Process p = Runtime.getRuntime().exec(command);
        new Thread(new SyncPipe(p.getErrorStream(), System.err)).start();
        new Thread(new SyncPipe(p.getInputStream(), System.out)).start();
        PrintWriter stdin = new PrintWriter(p.getOutputStream());
        stdin.println("c:");
        stdin.println("cd "+projectdir);

        stdin.println(cmd + " > " + output);



        stdin.close();
        int returnCode = p.waitFor();
        System.out.println("Return code = " + returnCode);
        try (FileReader reader = new FileReader(output);
             BufferedReader br = new BufferedReader(reader) // 建立一个对象，它把文件内容转成计算机能读懂的语言
        ) {
            String line;

            while ((line = br.readLine()) != null) {
                // 一次读入一行数据
                //System.out.println(line);
                final_com=line;
                break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return final_com;
    }
}
