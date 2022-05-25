package dataCollect;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitor;
import detectId.DS.ClassDS;
import detectId.DS.IdentifierDS;
import detectId.DS.MethodDS;
import detectId.ParseInfo.ClassCollector;
import detectId.ParseInfo.VariableCollector;
import detectId.Trace.SyncPipe;
import detectId.utility.SimilarityCal;

import java.io.*;
import java.util.Hashtable;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;

public class HistoryAnalysis {
    public static void main(String[] args) throws Exception {

        //single-process
//        ProjectCommit("camel");

        //batch-process
        String[] projs = {"camel", "flink"};
        for (String proj : projs) {
            ProjectCommit(proj);
        }
    }


    /*
        获取projectname
     */
    public static void ProjectCommit(String projectname) throws Exception {
        //项目所在位置
        String projectpath = "../data/GitProject/" + projectname + "/";
        //javaFileIndex下存储某个项目所有的java文件位置
        String FileIndex = "../data/JavaFileIndex/" + projectname + ".txt";
        //记录log的日志文件
        String LogOutput = "../Renaming/log/log_output_" + projectname + ".txt";
        //输出的重命名文件
        String TraceHistory = "../Renaming/" + projectname + ".csv";

        /*
        1. 遍历所有java文件，进行解析
        */
        BufferedReader br = new BufferedReader(new FileReader(FileIndex));
        String line = "";
        while ((line = br.readLine()) != null) {
            Vector<String> allcode = new Vector<String>();
            File in = new File(line);
            if (!in.exists()) {
                continue;
            }


            BufferedReader read = new BufferedReader(new FileReader(line));
            String one = "";
            while ((one = read.readLine()) != null) {
                allcode.add(one);
            }
            read.close();


            String file = line.substring(line.indexOf(projectname) + projectname.length() + 2, line.length());


            //（1）获取当前处理文件中的所有标识符，遍历进行解析
            Vector<idenDS> alliden = ObtainIdentifier(allcode, line);
            for (idenDS iden : alliden) {

                //获得相应的标识符
                String identifier = iden.getIdentifier();
                if (identifier.trim().length() == 0) continue;
                //获取对应代码
                String statement = iden.getStatement();
                //获取代码行数
                int lineno = iden.getLocation();

                lineno++;
                //查看标识符所在行的提交历史
                ExecuteCommand(projectpath, "git log -L " + lineno + "," + lineno + ":" + file, LogOutput);
                //解析log内容，保存为commitMessage对象
                Vector<commitMessage> allcom = ParseCommandContent(LogOutput);
                //进而获取具体的所需要的提交历史内容
                Vector<String>[] data = TraceAnalysis(allcom, statement, iden);

                Vector<String> traceResult = data[0];
                Vector<String> historyId = data[1];
                Vector<String> locHistory = data[2];
                //有修改历史的话
                if (traceResult.size() > 1) {
                    //获取beforeId
                    Vector<String> beforeId = ExtractIdFromStatement(traceResult, iden.getIdentifier(), iden.getType());

                    //根据history把修改历史写入csv
                    BufferedWriter bw = new BufferedWriter(new FileWriter(TraceHistory, true));
                    //tyepe,行数，文件位置，（修改历史，下面这个循环），每个statement
                    StringBuffer sb = new StringBuffer();
                    for (String s : locHistory) {
                        StringBuilder b = new StringBuilder(s);
                        b.replace(0, 1, projectpath.substring(0, projectpath.length() - 1).replace("\\", "\\\\"));
                        int index = b.indexOf("<-");
                        b.replace(index + 2, index + 3, projectpath.substring(0, projectpath.length() - 1).replace("\\", "\\\\"));


                        sb.append(b.toString().replace("/", "\\\\") + "<=");

                    }
                    //获取标识符所的父类，以后抽取相关实体的时候使用
                    String classpar = "null";
                    String methodpar = "null";
                    String defaultValue = "null";
                    //这里的父类是最新的标识符所在父类，如果其父类也变名称了。放弃获取这个标识符

                    if (iden.getClasspar() != null && iden.getClasspar().length() > 0) {
                        classpar = iden.getClasspar();
                    }
                    if (iden.getMethodpar() != null && iden.getMethodpar().length() > 0) {
                        methodpar = iden.getMethodpar();
                    }
                    if (iden.getDefaultValue() != null && iden.getDefaultValue().length() > 0) {
                        defaultValue = iden.getDefaultValue();
                    }

                    bw.write(iden.getType() + "," + iden.getLocation() + "," + sb.toString() + ",");
                    bw.write(iden.getIdentifier() + "<-");//最新版本<-
                    for (String onetr : beforeId) {
                        bw.write(onetr + "<-");
                    }
                    bw.write(",");

                    for (String id : historyId) {

                        bw.write(id + ",");
                    }
                    for (String onehis : traceResult) {
                        if (onehis.contains(",")) {
                            //针对csv的特殊处理，stmt中的逗号都变为####
                            onehis = onehis.replace(",", "####");
                        }

                        bw.write(onehis + ",");

                    }

                    bw.write(classpar + ",");

                    bw.write(methodpar + ",");
                    bw.write(defaultValue);


                    bw.newLine();
                    bw.close();

                }


            }


        }
    }

    public static Vector<idenDS> ObtainIdentifier(Vector<String> allstate, String javafilepath) throws Exception {
        Vector<IdentifierDS> packages = new Vector<IdentifierDS>();  //加入当前的package
        Vector<IdentifierDS> types = new Vector<IdentifierDS>();     //类，接口，枚举
        Vector<IdentifierDS> methods = new Vector<IdentifierDS>();   //method，包括了constructor,setter,getter
        Vector<IdentifierDS> fields = new Vector<IdentifierDS>();    //field
        Vector<IdentifierDS> variables = new Vector<IdentifierDS>(); //包括了函数的参数

        CompilationUnit cu = null;

        try {
            cu = JavaParser.parse(new File(javafilepath));

        } catch (Exception e) {
            System.err.println(e.toString());
        }

        String packagename = "";
        try {
            Optional<PackageDeclaration> packagename1 = cu.getPackageDeclaration();
            if (packagename1.isPresent()) {

                packagename = packagename1.get().toString().trim();
                if (packagename.startsWith("/*"))
                    packagename = packagename.substring(packagename.indexOf("*/") + 2, packagename.length()).trim();
                if (packagename.startsWith("//"))
                    packagename = packagename.substring(packagename.indexOf("package "), packagename.length());
                if (packagename.startsWith("package "))
                    packagename = packagename.substring(packagename.indexOf(" ") + 1, packagename.length());
                if (packagename.endsWith(";"))
                    packagename = packagename.substring(0, packagename.length() - 1);
            }

        } catch (Exception e) {
            System.err.println(e.toString());
        }
        int packloc = 1;
        for (int i = 0; i < allstate.size(); i++) {
            if (allstate.get(i).trim().startsWith("package ")) {
                packloc = i + 1;
                break;
            }
        }

        IdentifierDS newpackage = new IdentifierDS("", "", packagename, "", "", packloc);
        packages.add(newpackage);


        Hashtable<String, Integer> variableSet = new Hashtable<String, Integer>();            //所有的变量和对象
        VoidVisitor<Hashtable<String, Integer>> VariableCollector = new VariableCollector();
        try {
            VariableCollector.visit(cu, variableSet);
        } catch (Exception e) {
            System.err.println(e.toString());
        }


        Vector<ClassDS> classdetails = new Vector<ClassDS>();
        VoidVisitor<Vector<ClassDS>> classNameCollector = new ClassCollector();
        try {
            classNameCollector.visit(cu, classdetails);
        } catch (Exception e) {
            System.err.println(e.toString());
        }


        for (ClassDS one : classdetails) {
            //-----------------iden是类
            IdentifierDS newclass = new IdentifierDS(one.getClassname(), "", one.getClassname(), "class", "", one.getIndex());
            types.add(newclass);

            Vector<MethodDS> methodlist = one.getMethodlist();
            for (MethodDS onemethod : methodlist) {
                //-----------------iden是方法
                IdentifierDS newmethod = new IdentifierDS(one.getClassname(), onemethod.getMethodname(), onemethod.getMethodname(), onemethod.getReturntype(), "", onemethod.getBeginindex());
                methods.add(newmethod);

                Vector<IdentifierDS> parameters = onemethod.getParameters();
                for (IdentifierDS oneid : parameters) {
                    variables.add(oneid);
                }

            }
            //-----------------one是类，获取该类下的所有field
            Vector<IdentifierDS> fieldlist = one.getFieldlist();
            fields.addAll(fieldlist);

        }


        Set<String> keyset = variableSet.keySet();
        for (String onekey : keyset) {
            int onevalue = variableSet.get(onekey);
            String methodpar = "";
            String classpar = "";
            for (ClassDS one : classdetails) {

                Vector<MethodDS> methodlist = one.getMethodlist();
                for (MethodDS onemethod : methodlist) {
                    if (onevalue >= onemethod.getBeginindex() && onevalue <= onemethod.getEndindex()) {
                        methodpar = onemethod.getMethodname();
                        classpar = one.getClassname();
                        break;
                    }
                }
            }


            if (onekey.contains("=")) {
                String front = onekey.substring(0, onekey.indexOf("=")).trim();
                String end = onekey.substring(onekey.indexOf("=") + 1, onekey.length()).trim();
                String name = front.substring(front.lastIndexOf(" ") + 1, front.length()).trim();
                String type = "";

                if (front.contains(" ")) {
                    front = front.substring(0, front.lastIndexOf(" "));
                    if (front.contains(" ")) {
                        type = front.substring(front.lastIndexOf(" ") + 1, front.length()).trim();
                    } else type = front;
                } else
                    type = front;

                type = type.trim();
                IdentifierDS oneid = new IdentifierDS(classpar, methodpar, name, type, end, onevalue);

                variables.add(oneid);


            } else {
                String name = onekey.substring(onekey.lastIndexOf(" ") + 1, onekey.length()).trim();
                onekey = onekey.substring(0, onekey.lastIndexOf(" ")).trim();
                String type = "";
                if (onekey.contains(" "))
                    type = onekey.substring(onekey.lastIndexOf(" ") + 1, onekey.length()).trim();
                else
                    type = onekey;

                type = type.trim();
                // //-----------------iden是variable
                IdentifierDS oneid = new IdentifierDS(classpar, methodpar, name, type, "", onevalue);

                variables.add(oneid);

            }
        }


        Vector<idenDS> allid = new Vector<idenDS>();
        for (IdentifierDS onepackage : packages) {
            String identifiername = onepackage.getName();
            int location = onepackage.getLocation();
            String singlestate = "";
            int purelocation = -1;
            if (location - 1 >= 0 && allstate.size() > 0) {
                singlestate = allstate.get(location - 1);
                purelocation = location - 1;
            }

            if (singlestate.contains(identifiername)) {
                idenDS oness = new idenDS(1, identifiername, singlestate, purelocation, onepackage.getClasspar(), onepackage.getMethodpar(), onepackage.getType(), onepackage.getDefaultvalue());
                allid.add(oness);
            } else {
                System.err.println("1: 标识符位置不对！" + identifiername + "  " + singlestate);
            }

        }
        for (IdentifierDS onetype : types) {
//        	System.out.println(onetype.toString());

            String identifiername = onetype.getName();
            int location = onetype.getLocation();
            location = location / 100000;

            String singlestate = "";
            int purelocation = -1;
            if (location - 1 >= 0 && allstate.size() > 0) {
                singlestate = allstate.get(location - 1);
                purelocation = location - 1;
            }

            if (singlestate.trim().startsWith("@")) {
                if (location < allstate.size()) {
                    singlestate = allstate.get(location);
                    purelocation = location;
                }

                if (singlestate.trim().startsWith("@")) {
                    singlestate = allstate.get(location + 1);
                    purelocation = location + 1;
                }
            }

            if (singlestate.contains(identifiername)) {
                idenDS oness = new idenDS(2, identifiername, singlestate, purelocation, onetype.getClasspar(), onetype.getMethodpar(), onetype.getType(), onetype.getDefaultvalue());
                allid.add(oness);
            } else {
                System.err.println("2: 标识符位置不对！" + identifiername + "  " + singlestate);
            }

        }
        for (IdentifierDS onemethod : methods) {
//            System.out.println("【method】："+onemethod.getName());
//

            String identifiername = onemethod.getName();
            int location = onemethod.getLocation();

            String singlestate = allstate.get(location - 1);
            int purelocation = location - 1;

            if (singlestate.trim().startsWith("@")) {
                singlestate = allstate.get(location);
                purelocation = location;

                if (singlestate.trim().startsWith("@")) {
                    singlestate = allstate.get(location + 1);
                    purelocation = location + 1;
                }
            }

            if (singlestate.contains(identifiername)) {
                idenDS oness = new idenDS(3, identifiername, singlestate, purelocation, onemethod.getClasspar(), onemethod.getMethodpar(), onemethod.getType(), onemethod.getDefaultvalue());
                allid.add(oness);
            } else {
                System.err.println("3: 标识符位置不对！" + identifiername + "  " + singlestate);
            }
        }
        for (IdentifierDS onefield : fields) {
//            System.out.println("【fields】："+onefield.getName());
//
            String identifiername = onefield.getName();
            int location = onefield.getLocation();
            String singlestate = allstate.get(location - 1);
            int purelocation = location - 1;
            if (singlestate.contains(identifiername)) {
                idenDS oness = new idenDS(4, identifiername, singlestate, purelocation, onefield.getClasspar(), onefield.getMethodpar(), onefield.getType(), onefield.getDefaultvalue());
                allid.add(oness);
            } else {
                System.err.println("4: 标识符位置不对！" + identifiername + "  " + singlestate);
            }
        }
        for (IdentifierDS onevariable : variables) {
//            System.out.println("【variable】："+onevariable.getName());
//
            String identifiername = onevariable.getName();
            int location = onevariable.getLocation();
            String singlestate = allstate.get(location - 1);
            int purelocation = location - 1;
            if (singlestate.trim().startsWith("@")) {
                singlestate = allstate.get(location);
                purelocation = location;
                if (singlestate.trim().startsWith("@")) {
                    singlestate = allstate.get(location + 1);
                    purelocation = location + 1;
                }
            }

            if (singlestate.contains(identifiername)) {
                idenDS oness = new idenDS(5, identifiername, singlestate, purelocation, onevariable.getClasspar(), onevariable.getMethodpar(), onevariable.getType(), onevariable.getDefaultvalue());
                allid.add(oness);
            } else {
                if (purelocation + 1 < allstate.size()) {
                    singlestate = singlestate + " " + allstate.get(purelocation + 1);
                    //        		purelocation=location+1;
                    if (singlestate.contains(identifiername)) {
                        idenDS oness = new idenDS(5, identifiername, singlestate, purelocation);
                        allid.add(oness);
                    } else {
                        System.err.println("5: 标识符位置不对！" + identifiername + "  " + singlestate);
                    }
                }
            }
        }


        return allid;

    }

    //这部分是查找对应的代码 (不同版本对应的代码行)
    public static Vector<String>[] TraceAnalysis(Vector<commitMessage> allcom, String statement, idenDS iden) throws Exception {
        /*传入代码行,要获取 traceHistory,historyId,locHis
        traceHistory:标识符iden改变的所有版本中的statement，
        histroryID：标识符iden所有id（改变前后）
        locHis：标识符iden所在的所有文件位置（可能文件夹改变了）
        */
        statement = statement.trim();
        Vector<String> traceHistory = new Vector<String>();
        Vector<String> historyId = new Vector<String>();
        Vector<String> locHis = new Vector<String>();
        traceHistory.add(statement);

        //----------------找与这个statement对应的代码行-------------------

        for (commitMessage m : allcom) {

            //获取commit中diff的内容
            Vector<Diff> difflist = m.getDifflist();
            for (Diff onediff : difflist) {
                //主要获取内容，其他author啥的就不管了
                Vector<String> content = onediff.getContent();


                for (String line : content) {
                    //以-开始，就是原文件中被删除的/被修改的一行
                    if (line.startsWith("- ")) {
                        //获取纯文本内容
                        line = line.substring(1, line.length()).trim();
                        //计算原来代码 line,和现在的代码行statement
                        //具有高相似度则是同一行代码，并且被修改


                        float simi = SimilarityCal.calEditSimi(line, statement);

                        // System.out.println("========================sim="+simi);
                        //确定statemnet变化了但相似
                        if (simi > 0.85 && simi != 1) {
                            //取出历史版本stmt line和当前版本stmt statement中的标识符（就第一个）
                            Vector<String> stmtId = ExtractOneIdFromStatement(statement, iden.getIdentifier(), iden.getType());
                            Vector<String> lineId = ExtractOneIdFromStatement(line, iden.getIdentifier(), iden.getType());


                            float id_simi = 0;
                            if (stmtId.size() != 0 && lineId.size() != 0) {
                                id_simi = SimilarityCal.calEditSimi(stmtId.get(0), lineId.get(0));


                            }
                            //确定标识符的确变化了
                            if (id_simi != 1.0) {

                                statement = line;
                                //加入修改历史中。接着顺延
                                traceHistory.add(statement);
                                historyId.add(m.getCommitid());
                                locHis.add(onediff.getToFile() + "<-" + onediff.getFromFile());

                                //获取commitId

                            }
                            break;
                        }
                    }
                }
            }
        }


        return new Vector[]{traceHistory, historyId, locHis};
    }

    private static Vector<String> ExtractOneIdFromStatement(String statement, String identifier, int type) throws Exception {
        Vector<String> result = new Vector<String>();


        String onetrace = statement;

        onetrace = onetrace.trim();
        if (onetrace.endsWith(";"))
            onetrace = onetrace.substring(0, onetrace.length() - 1);
        onetrace = onetrace.trim();

        if (type == 1) {
            if (onetrace.startsWith("/*"))
                onetrace = onetrace.substring(onetrace.indexOf("*/") + 2, onetrace.length()).trim();
            if (onetrace.startsWith("//"))
                onetrace = onetrace.substring(onetrace.indexOf("package "), onetrace.length());
            if (onetrace.startsWith("package "))
                onetrace = onetrace.substring(onetrace.indexOf(" ") + 1, onetrace.length());

            result.add(onetrace);
        } else if (type == 2) {
            if (onetrace.endsWith("{"))
                onetrace = onetrace.substring(0, onetrace.length() - 1);
            if (onetrace.contains(" implements "))
                onetrace = onetrace.substring(0, onetrace.indexOf(" implements ")).trim();

            if (onetrace.contains(" extends "))
                onetrace = onetrace.substring(0, onetrace.indexOf(" extends ")).trim();

            if (onetrace.contains(" ")) {
                onetrace = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length()).trim();
                result.add(onetrace);
            } else {
                result.add(onetrace);
            }

        } else if (type == 3) {
            if (onetrace.contains("("))
                onetrace = onetrace.substring(0, onetrace.indexOf("(")).trim();

            if (onetrace.contains(" ")) {
                onetrace = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length()).trim();
                result.add(onetrace);
            } else {
                result.add(onetrace);
            }

        } else if (type == 4) {
            if (onetrace.contains("="))
                onetrace = onetrace.substring(0, onetrace.indexOf("=")).trim();
            if (onetrace.contains(" ")) {
                onetrace = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length()).trim();
                result.add(onetrace);
            } else {
                result.add(onetrace);
            }

        } else if (type == 5) {
            if (!onetrace.contains("=") && onetrace.contains("(")) {
                if (onetrace.contains(")")) {
                    onetrace = onetrace.substring(onetrace.indexOf("(") + 1, onetrace.indexOf(")"));
                } else {
                    onetrace = onetrace.substring(onetrace.indexOf("(") + 1, onetrace.length()).trim();
                }

                if (onetrace.contains(",")) {
                    Vector<String> needtest = new Vector<String>();
                    String spl[] = onetrace.split(",");
                    for (String onespl : spl) {
                        onespl = onespl.trim();
                        String test = onespl.trim();
                        if (onespl.contains(" ")) {
                            test = onespl.substring(onespl.lastIndexOf(" ") + 1, onespl.length());
                        }
                        needtest.add(test);

                    }

                    float max = 0;
                    String maxstring = "";
                    for (String ss : needtest) {
                        float simi = SimilarityCal.calEditSimi(ss, identifier);
                        if (simi > max) {
                            max = simi;
                            maxstring = ss;
                        }
                    }
                    result.add(maxstring);

                } else if (onetrace.contains(":")) {
                    onetrace = onetrace.substring(0, onetrace.indexOf(":")).trim();
                    String test = onetrace;
                    if (onetrace.contains(" ")) {
                        test = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length());
                    }
                    result.add(test);
                } else if (onetrace.contains(";")) {
                    onetrace = onetrace.substring(0, onetrace.indexOf(";")).trim();
                    String test = onetrace;
                    if (onetrace.contains(" ")) {
                        test = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length());
                    }
                    result.add(test);
                } else {
                    String test = onetrace.trim();
                    if (onetrace.contains(" ")) {
                        test = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length());
                    }
                    result.add(test);
                }


            } else {
                if (onetrace.contains("="))
                    onetrace = onetrace.substring(0, onetrace.indexOf("=")).trim();

                if (onetrace.contains(" ")) {
                    onetrace = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length()).trim();
                    result.add(onetrace);
                } else {
                    result.add(onetrace);
                }
            }
        }

//        }
        return result;
    }

    public static void ExecuteCommand(String projectdir, String cmd, String output) throws Exception {
        System.out.println("projectdir:" + projectdir);

        String[] command =
                {
                        "cmd",
                };
        Process p = Runtime.getRuntime().exec(command);
        new Thread(new SyncPipe(p.getErrorStream(), System.err)).start();
        new Thread(new SyncPipe(p.getInputStream(), System.out)).start();
        PrintWriter stdin = new PrintWriter(p.getOutputStream());
        //首先进入对应的磁盘再进行后续的命令执行
        stdin.println("C:");
        stdin.println("cd " + projectdir);
        stdin.println(cmd + " > " + output);
        stdin.close();
        int returnCode = p.waitFor();
        System.out.println("Return code = " + returnCode);
    }

    public static Vector<commitMessage> ParseCommandContent(String filename) throws Exception {
        //allmessage:
        //allc:输出信息的每一行
        Vector<commitMessage> allmessage = new Vector<commitMessage>();
        Vector<String> allc = new Vector<String>();
        //git log的输出所有信息在filename中，读取该文件
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String lines = "";
        while ((lines = br.readLine()) != null) {
            allc.add(lines);
        }
        br.close();

        //commitinfo:
        Vector<Vector<String>> commitinfo = new Vector<Vector<String>>();
        //一条commit msg
        Vector<String> onecom = new Vector<String>();
        //开始遍历输出信息进行解析
        for (int i = 0; i < allc.size(); i++) {
            /*结构:
            commit Id
            Author:
            Date:
            xxx
            diff --git srcFile
            dstFile
            */
            //新的一条commitMsg要存到oneCom中，如果上一次的还没处理，就存到commitInfo之后再存入
            if (allc.get(i).startsWith("commit ")) {
                if (onecom.size() != 0) {
                    Vector<String> temp = new Vector<String>();
                    temp.addAll(onecom);
                    commitinfo.add(temp);
                    onecom.clear();
                    onecom.add(allc.get(i));
                } else {
                    onecom.add(allc.get(i));
                }

            } else {
                onecom.add(allc.get(i));
            }
        }

        if (onecom.size() != 0) {
            Vector<String> temp = new Vector<String>();
            temp.addAll(onecom);
            commitinfo.add(temp);
            onecom.clear();
        }


        //把命令内容已经全部存好，对应的每一条commit msg如下

        for (Vector<String> onecommit : commitinfo) {

            String commitid = "";
            String author = "";
            String date = "";
            String message = "";
            StringBuilder sb = new StringBuilder();

            for (String line : onecommit) {
                if (line.startsWith("commit")) {
                    commitid = line.substring("commit".length(), line.length()).trim();
                } else if (line.startsWith("Author:")) {
                    author = line.substring("Author:".length(), line.length()).trim();
                } else if (line.startsWith("Date:")) {
                    date = line.substring("Date:".length(), line.length()).trim();
                } else if (line.startsWith("    ")) {
                    message += line.trim() + "\n";
                } else {
                    sb.append(line + "\n");
                }
            }


            Vector<Diff> difflist = new Vector<Diff>();
            String mess = sb.toString();
            //split数组中每个元素是一个commit的
            String split[] = mess.split("diff --git");


            for (String s : split) {
                if (!s.trim().isEmpty()) {
                    Diff onediff = AnalyzeDiff("diff --git " + s.trim());

                    difflist.add(onediff);
                }
            }
            commitMessage onecommitmessage = new commitMessage(commitid, author, date, message, difflist);
            allmessage.add(onecommitmessage);
        }
        return allmessage;
    }

    public static Diff AnalyzeDiff(String s) {
        String split[] = s.split("\n");
        String fromFile = "";
        String toFile = "";
        String index = "";
        Vector<String> content = new Vector<String>();
        for (String oneline : split) {
            oneline = oneline.trim();
            if (oneline.startsWith("--- ")) {
                fromFile = oneline.substring(oneline.indexOf("--- ") + "--- ".length(), oneline.length());
            } else if (oneline.startsWith("+++ ")) {
                toFile = oneline.substring(oneline.indexOf("+++ ") + "+++ ".length(), oneline.length());
            } else if (oneline.startsWith("+++ ")) {
                toFile = oneline.substring(oneline.indexOf("+++ ") + "+++ ".length(), oneline.length());
            } else if (oneline.startsWith("@@")) {
                index = oneline.replace("@", "").trim();
            } else {
                if (!oneline.startsWith("diff --git ") && !oneline.isEmpty())
                    content.add(oneline);
            }
        }

        Diff one = new Diff(fromFile, toFile, index, content);
        return one;
    }

    public static Vector<String> ExtractIdFromStatement(Vector<String> traceResult, String identifier, int type) throws Exception {
        Vector<String> result = new Vector<String>();
        for (int i = 1; i < traceResult.size(); i++) {
            String onetrace = traceResult.get(i);
            onetrace = onetrace.trim();
            if (onetrace.endsWith(";"))
                onetrace = onetrace.substring(0, onetrace.length() - 1);
            onetrace = onetrace.trim();

            if (type == 1) {
                if (onetrace.startsWith("/*"))
                    onetrace = onetrace.substring(onetrace.indexOf("*/") + 2, onetrace.length()).trim();
                if (onetrace.startsWith("//"))
                    onetrace = onetrace.substring(onetrace.indexOf("package "), onetrace.length());
                if (onetrace.startsWith("package "))
                    onetrace = onetrace.substring(onetrace.indexOf(" ") + 1, onetrace.length());

                result.add(onetrace);
            } else if (type == 2) {
                if (onetrace.endsWith("{"))
                    onetrace = onetrace.substring(0, onetrace.length() - 1);
                if (onetrace.contains(" implements "))
                    onetrace = onetrace.substring(0, onetrace.indexOf(" implements ")).trim();

                if (onetrace.contains(" extends "))
                    onetrace = onetrace.substring(0, onetrace.indexOf(" extends ")).trim();

                if (onetrace.contains(" ")) {
                    onetrace = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length()).trim();
                    result.add(onetrace);
                } else {
                    result.add(onetrace);
                }

            } else if (type == 3) {
                if (onetrace.contains("("))
                    onetrace = onetrace.substring(0, onetrace.indexOf("(")).trim();

                if (onetrace.contains(" ")) {
                    onetrace = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length()).trim();
                    result.add(onetrace);
                } else {
                    result.add(onetrace);
                }

            } else if (type == 4) {
                if (onetrace.contains("="))
                    onetrace = onetrace.substring(0, onetrace.indexOf("=")).trim();
                if (onetrace.contains(" ")) {
                    onetrace = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length()).trim();
                    result.add(onetrace);
                } else {
                    result.add(onetrace);
                }

            } else if (type == 5) {
                if (!onetrace.contains("=") && onetrace.contains("(")) {
                    if (onetrace.contains(")")) {
                        onetrace = onetrace.substring(onetrace.indexOf("(") + 1, onetrace.indexOf(")"));
                    } else {
                        onetrace = onetrace.substring(onetrace.indexOf("(") + 1, onetrace.length()).trim();
                    }

                    if (onetrace.contains(",")) {
                        Vector<String> needtest = new Vector<String>();
                        String spl[] = onetrace.split(",");
                        for (String onespl : spl) {
                            onespl = onespl.trim();
                            String test = onespl.trim();
                            if (onespl.contains(" ")) {
                                test = onespl.substring(onespl.lastIndexOf(" ") + 1, onespl.length());
                            }
                            needtest.add(test);

                        }

                        float max = 0;
                        String maxstring = "";
                        for (String ss : needtest) {
                            float simi = SimilarityCal.calEditSimi(ss, identifier);
                            if (simi > max) {
                                max = simi;
                                maxstring = ss;
                            }
                        }
                        result.add(maxstring);

                    } else if (onetrace.contains(":")) {
                        onetrace = onetrace.substring(0, onetrace.indexOf(":")).trim();
                        String test = onetrace;
                        if (onetrace.contains(" ")) {
                            test = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length());
                        }
                        result.add(test);
                    } else if (onetrace.contains(";")) {
                        onetrace = onetrace.substring(0, onetrace.indexOf(";")).trim();
                        String test = onetrace;
                        if (onetrace.contains(" ")) {
                            test = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length());
                        }
                        result.add(test);
                    } else {
                        String test = onetrace.trim();
                        if (onetrace.contains(" ")) {
                            test = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length());
                        }
                        result.add(test);
                    }


                } else {
                    if (onetrace.contains("="))
                        onetrace = onetrace.substring(0, onetrace.indexOf("=")).trim();

                    if (onetrace.contains(" ")) {
                        onetrace = onetrace.substring(onetrace.lastIndexOf(" ") + 1, onetrace.length()).trim();
                        result.add(onetrace);
                    } else {
                        result.add(onetrace);
                    }
                }
            }

        }
        return result;
    }

}

class idenDS {
    int type;
    String identifier;
    String statement;
    int location;
    String classpar;
    String methodpar;
    String IdType;

    public String getClasspar() {
        return classpar;
    }


    public String getMethodpar() {
        return methodpar;
    }


    public String getDefaultValue() {
        return defaultValue;
    }


    String defaultValue;

    public int getType() {
        return type;
    }


    public String getIdentifier() {
        return identifier;
    }


    public String getStatement() {
        return statement;
    }


    public int getLocation() {
        return location;
    }


    public idenDS(int type, String identifier, String statement, int location) {
        super();
        this.type = type;
        this.identifier = identifier;
        this.statement = statement;
        this.location = location;
    }

    public idenDS(int type, String identifier, String statement, int location, String classpar, String methodpar, String IdType, String defaultValue) {
        super();
        this.type = type;
        this.identifier = identifier;
        this.statement = statement;
        this.location = location;
        this.classpar = classpar;
        this.methodpar = methodpar;
        this.IdType = IdType;
        this.defaultValue = defaultValue;
    }

    @Override
    public String toString() {
        return "idenDS [type=" + type + ", identifier=" + identifier + ", statement=" + statement + ", location="
                + location + "]";
    }

}


class commitMessage {
    //获取得到的 id，author,date,message,diff显示部分（diff list）
    String commitid;
    String author;
    String date;
    String message;
    Vector<Diff> difflist = new Vector<Diff>();

    public String getCommitid() {
        return commitid;
    }

    public void setCommitid(String commitid) {
        this.commitid = commitid;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Vector<Diff> getDifflist() {
        return difflist;
    }

    public void setDifflist(Vector<Diff> difflist) {
        this.difflist = difflist;
    }

    public commitMessage(String commitid, String author, String date, String message, Vector<Diff> difflist) {
        super();
        this.commitid = commitid;
        this.author = author;
        this.date = date;
        this.message = message;
        this.difflist = difflist;
    }

    @Override
    public String toString() {
        return "commitMessage [commitid=" + commitid + ", author=" + author + ", date=" + date + ", message=" + message
                + ", difflist=" + difflist.size() + "]";
    }


}

class Diff {
    String fromFile;
    String toFile;
    String index;
    Vector<String> content = new Vector<String>();

    //文件
    public String getFromFile() {
        return fromFile;
    }

    public void setFromFile(String fromFile) {
        this.fromFile = fromFile;
    }

    public String getToFile() {
        return toFile;
    }

    public void setToFile(String toFile) {
        this.toFile = toFile;
    }

    //比较的行数
    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public Vector<String> getContent() {
        return content;
    }

    public void setContent(Vector<String> content) {
        this.content = content;
    }

    public Diff(String fromFile, String toFile, String index, Vector<String> content) {
        super();
        this.fromFile = fromFile;
        this.toFile = toFile;
        this.index = index;
        this.content = content;
    }

    @Override
    public String toString() {
        return "Diff [fromFile=" + fromFile + ", toFile=" + toFile + ", index=" + index + ", content=" + content.toString() + "]";
    }


}




