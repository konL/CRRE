package dataBuild;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.VoidVisitor;
import dataCollect.JavaParserUtils;
import detectId.DS.ClassDS;
import detectId.DS.IdentifierDS;
import detectId.DS.MethodDS;
import detectId.ParseInfo.ClassCollector;
import detectId.ParseInfo.VariableCollector;


import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class createMergeFile_all {
    static List<String> fieldsName;
    static List<String> methodName_extend;

    static List<String> callSet;
    //获取declaration
    static Map<String, FieldDeclaration> fieldMap;
    static Map<String, VariableDeclarationExpr> variableMap;
    static Map<String, String> callMap;
    static Map<String, String> classMap;

    static Map<String, List<String>> class_ent;
    static String[] typeSet = {"package", "type", "method", "field", "variable"};
    static List<String> changeIdlist;
    static Map<String, String> var_stmtMap;
    static Map<String, String> field_stmtMap;
    static Map<String, String> entUsageMap;

    public static void main(String[] args) throws Exception {
        //single-process
        create("cassandra");
        //batch-process
//        String[] projs={"flink","camel"};
//        for(String proj:projs){
//            create(proj);
//        }
    }

    public static void create(String proj) throws Exception {
        //historyanalysis生成的重命名文件
        String renamingPath = "..\\Renaming\\" + proj + ".csv";

        //记录所有重命名Id，fileSeet是记录已经获取过unchange的文件=============================================================
        Set<String> renamingId = new HashSet<String>();
        List<String> fileSet = new LinkedList<>();
        changeIdlist = new ArrayList<>();
        //① 读取记录了改名的csv文件
        CsvReader read1 = new CsvReader(renamingPath);
        System.out.println("===========① reading renaming name=============");
        while (read1.readRecord()) {
            String[] changeIdSet = read1.get(3).split("<-");
            if (changeIdSet.length == 0) continue;
            changeIdlist.add(read1.get(3));
            for (int i = 1; i < changeIdSet.length; i++) {
                if (changeIdSet[i].length() > 0)
                    renamingId.add(changeIdSet[i]);
            }
        }

        //写入的文件
        BufferedWriter bw = new BufferedWriter(new FileWriter("..\\raw_data\\context\\" + proj + "_merge.csv", true));
        CsvWriter writer = new CsvWriter(bw, ',');
        CsvReader read = new CsvReader(renamingPath);
        while (read.readRecord()) {
            int type = 1;
            try {
                type = Integer.parseInt(read.get(0));
            } catch (NumberFormatException e) {
                continue;
            }

            String[] changeIdSet = read.get(3).split("<-");


            if (changeIdSet.length == 0 || read.get(3).contains("<-<-")) continue;
            String[] t = read.get(2).split("<=")[0].split("<-")[0].split("\\\\");
            String filename = t[t.length - 1].trim();
            //每一对renaming pair，找到对应的版本对实现
            boolean isBad = false;
            for (int i = 1; i < changeIdSet.length; i++) {
                isBad = changeIdSet[i].contains("<") || changeIdSet[i].contains(">") || changeIdSet[i].contains(".") || changeIdSet[i].contains("\"") || changeIdSet[i].contains("{") || changeIdSet[i].contains("}")
                        || changeIdSet[i].contains("(") || changeIdSet[i].contains(")");
                if (isBad) break;
                renamingId.add(changeIdSet[i]);
                String changeId = changeIdSet[i - 1] + "_" + changeIdSet[i];//id
                String changeId_ori = changeIdSet[i - 1] + "." + changeIdSet[i];
                ;
                String id = read.get(4 + (i - 1)).trim();//commitId
                String oldStmt = read.get(4 + (changeIdSet.length - 1) + (i)).trim();
                String newStmt = read.get(4 + (changeIdSet.length - 1) + (i - 1)).trim();

                //获得对应的java文件，根据changeId生成oldCContext，newContext
                String file_old = "..\\versionDB\\raw_project\\" + proj + "\\" + proj + "_old\\" + id + "_" + changeId + "_" + filename;
                String file_new = "..\\versionDB\\raw_project\\" + proj + "\\" + proj + "_new\\" + id + "_" + changeId + "_" + filename;


                List<String> oldInfo = new ArrayList<>();
                List<String> newInfo = new ArrayList<>();

                //获得对应的java文件，生成oldCContext，newContext（重命名版本的）
                System.out.println("==========================================================================================");
                if (type == 3) {
                    oldInfo = create("old", oldStmt.trim(), changeId_ori, file_old);
                    newInfo = create("new", newStmt.trim(), changeId_ori, file_new);
                } else {
                    //倒数2，3
                    String classpar = read.get(4 + (changeIdSet.length - 1) + changeIdSet.length + 2);
                    String methodpar = read.get(4 + (changeIdSet.length - 1) + changeIdSet.length + 1);

                    oldInfo = create_other("old", oldStmt.trim(), changeId_ori, file_old, type, classpar, methodpar);
                    newInfo = create_other("new", newStmt.trim(), changeId_ori, file_new, type, classpar, methodpar);
                    System.out.println("type--->" + type + "," + oldInfo.size() + "," + newInfo.size());
                }
//
                if (oldInfo.size() == 0 || newInfo.size() == 0) continue;
                try {
                    String oldedge = oldInfo.get(4);
                    String newedge = newInfo.get(4);

                    writer.writeRecord(new String[]{"1", typeSet[Integer.parseInt(oldInfo.get(0)) - 1], oldInfo.get(2), newInfo.get(2), oldInfo.get(3), newInfo.get(3), oldedge, newedge});
                } catch (Exception e) {
                    continue;
                }
                String filedone = filename;

                if (fileSet.contains(filedone)) {
                    System.out.println("repeat!!!!");
                    continue;
                }
                fileSet.add(filedone);

                //获取该文件的代码，义查找方法以外的标识符
                File f = new File(file_new);
                BufferedReader br = new BufferedReader(new FileReader(f));
                Vector<String> allcode = new Vector<String>();
                String one = "";
                while ((one = br.readLine()) != null) {
                    allcode.add(one);
                }
                br.close();
//                //3. 获取文件中的所有标识符
                Vector<iden> alliden = ObtainIdentifier(allcode, file_new);
                for (iden identifier : alliden) {

                    List<String> unchange_old = new ArrayList<>();
                    List<String> unchange_new = new ArrayList<>();
                    String oneid = identifier.getIdentifier();
                    String stmt = identifier.getStatement();
                    //假设标识符不变的声明也不变，声明变化但是标识符不变的就不管了
                    if (!renamingId.contains(oneid.trim()) && oneid.trim().length() > 0) {
                        if (identifier.getType() == 3) {
                            unchange_old = create("old", stmt, oneid + "." + oneid, file_old);
                            unchange_new = create("new", stmt, oneid + "." + oneid, file_new);
                        } else {
                            String classpar = identifier.getClasspar() == null ? "null" : identifier.getClasspar();
                            String methodpar = identifier.getMethodpar() == null ? "null" : identifier.getMethodpar();

                            unchange_old = create_other("old", stmt, oneid + "." + oneid, file_old, identifier.getType(), classpar, methodpar);
                            unchange_new = create_other("new", stmt, oneid + "." + oneid, file_new, identifier.getType(), classpar, methodpar);
                        }
                        if (unchange_old.size() == 0 || unchange_new.size() == 0) continue;
                        try {
                            String oldedge = unchange_old.get(4);
                            String newedge = unchange_new.get(4);
                            writer.writeRecord(new String[]{"0", typeSet[Integer.parseInt(unchange_old.get(0)) - 1], unchange_old.get(2), unchange_new.get(2), unchange_old.get(3), unchange_new.get(3), oldedge, newedge});
                        } catch (Exception e) {
                            continue;
                        }

                    }
                }
            }
            if (isBad) {
                continue;
            }

        }
        System.out.println("项目" + proj + "的上下文文件（context+change entity）已生成");
        read.close();
        writer.close();
    }

    private static List<String> create(String ver, String statement, String changeId, String loc) throws Exception {
        //info记录传入method的信息
        List<String> info = new ArrayList<>();


        //1，获取识别方法的id（parsed_id,因为方法不能只抽取名字，参数和返回类型也需要抽取以进行唯一识别）
        statement.replace("####", ",");
        StringBuffer code = new StringBuffer(statement.trim());
        if (statement.trim().endsWith("{")) {
            code.deleteCharAt(code.length() - 1);
            code.append(";");
        }
        if (statement.trim().endsWith(")")) {
            code.append(";");
        }
        code.insert(0, "public class test{");
        code.append("}");
        Map<String, List> codeMap = new HashMap<>();
        String parsed_id = "";
        try {
            codeMap = JavaParserUtils.getData(code.toString(), true);
            parsed_id = codeMap.get("method_name_extend").get(0).toString();

        } catch (Exception e) {
            return info;
        }


        String id = "";
        if (ver.equals("old")) {
            id = changeId.split("\\.")[1];
        } else {
            id = changeId.split("\\.")[0];
        }
        List<String> methodBody = new ArrayList<String>();
        File f = new File(loc);
        if (!f.exists()) {
            System.out.println("【not exist!】" + loc);
            return info;
        }

        Map<String, List> map = new HashMap<>();
        try {
            map = JavaParserUtils.getData(loc);
            fieldMap = JavaParserUtils.fieldMap;
            callSet = map.get("call_relation");

            variableMap = JavaParserUtils.variableMap;
            callMap = JavaParserUtils.nameExprMap;
            fieldsName = map.get("fields_name");
            methodName_extend = map.get("method_name_extend");
            methodBody = map.get("method_body");


        } catch (Exception e) {
            System.out.println("JAVAPARSER ERR!!!!");
            System.out.println("its file" + ver + ":" + loc);
            System.out.println("【parsed id】=" + parsed_id);
            System.out.println("【curr id】=" + id.trim());
            System.out.println("===============================================");
            return info;
        }

        String context = "";
        StringBuffer edges = new StringBuffer();


        BufferedReader br = new BufferedReader(new FileReader(f));
        Vector<String> allcode = new Vector<String>();
        String one = "";
        while ((one = br.readLine()) != null) {

            allcode.add(one);

        }
        br.close();
        System.out.println(allcode.toString());


        Vector<iden> alliden = ObtainIdentifier(allcode, loc);
        int typeId = 0;
        //2.获取函数的code context
        if (methodName_extend.contains(parsed_id.trim())) {
            int index = methodName_extend.indexOf(parsed_id.trim());
            context = methodBody.get(index);
            iden method = getIdenInfo(id, alliden);
            if (method == null) return info;
            typeId = method.getType();
            System.out.println("methods contains " + id);
            System.out.println("context : " + context);

            //3.根据code context获取related entity


            try {
                //找到其包含的所有实体(set)
                StringBuffer code_context = new StringBuffer(context.trim());
                code_context.insert(0, "public class test{");
                code_context.append("}");
                Map<String, List> contextmap;
                try {
                    contextmap = JavaParserUtils.getData(code_context.toString(), true);
                } catch (Exception e) {
                    return info;
                }
                List<String> containent_method = contextmap.get("call_relation");
                List<String> containcall_method = contextmap.get("method_call");


                Set<String> set = new HashSet<>();

                for (String nameExpr : containent_method) {
                    //得nameexpr父类
                    set.add(nameExpr);

                }
                for (String callExpr : containcall_method) {

                    set.add(callExpr);


                }
                System.out.print(id + ":" + parsed_id + "【包含实体】");
                System.out.println(set.toString());
                List<String> node = new ArrayList<>();
                List<List<String>> edge = new ArrayList<>();
                for (String s : set) {

                    //假设node在parseId的方法参数中，不计算？
                    String[] data = parsed_id.split("@@@@");
                    for (String par : data) {
                        if (par.contains("#") && par.contains(s)) {
                            continue;
                        }
                    }
                    node.add(s);
                    List<String> edgePair = new ArrayList<>();
                    edgePair.add(id);
                    edgePair.add(s);
                    edge.add(edgePair);
                }

                System.out.println();
                System.out.print("edge=");
                for (List<String> e : edge) {
                    System.out.print("<" + e.get(0) + "," + e.get(1) + ">");
                    edges.append("<" + e.get(0) + "," + e.get(1) + ">" + "|");
                }
            } catch (NullPointerException e) {
                System.out.println("ERR ID==" + id);
            }
            System.out.println();


        }


        String[] csvContent = {String.valueOf(typeId), loc, id, normText(context), normText(edges.toString())};
        info = Arrays.asList(csvContent);
        System.out.println("==================================================================================");


        return info;
    }

    /*
    同理，获取除了metheod以外的标识符的信息并且返回
     */
    private static List<String> create_other(String ver, String statement, String changeId, String loc, int type, String classpar, String methodpar) throws Exception {
        //处理的identifier
        String id = "";
        //id的context
        StringBuffer context = new StringBuffer();
        //id的相关实体对
        StringBuffer edges = new StringBuffer();
        List<String> node = new ArrayList<>();
        List<List<String>> edge = new ArrayList<>();

        if (ver.equals("old")) {
            id = changeId.split("\\.")[1];
        } else {
            id = changeId.split("\\.")[0];
        }

        //记录返回信息
        List<String> info = new ArrayList<>();

        if (id.length() == 0) return info;
        Map<String, List> map = new HashMap<>();
        List<String> methodName = new ArrayList<String>();
        List<String> methodBody = new ArrayList<String>();
        try {
            map = JavaParserUtils.getData(loc);
        } catch (Exception e) {
            return info;
        }
        fieldMap = JavaParserUtils.fieldMap;
        callSet = map.get("call_relation");
        variableMap = JavaParserUtils.variableMap;
        callMap = JavaParserUtils.nameExprMap;
        fieldsName = map.get("fields_name");
        methodName_extend = map.get("method_name_extend");
        methodName = map.get("method_name");
        methodBody = map.get("method_body");
        classMap = JavaParserUtils.classMap;
        class_ent = JavaParserUtils.class_ent;

        //1. 针对var的处理：局部变量
        if (type == 5) {
            if (ver.equals("old")) {
                if (variableMap.get(id) == null) return info;
                String parents = JavaParserUtils.getParents(variableMap.get(id));
                String[] data = parents.split("\\.");
                methodpar = data[data.length - 1];

            }
            String body = "";
            //由于同名var可能在多个函数中出现，保证同一个函数的同一个var
            if (methodName.contains(methodpar.trim())) {

                int index = indexOf(methodName, 0, methodpar);
                body = methodBody.get(index);
                while (!body.contains(id)) {
                    if ((index + 1) == methodName.size()) return info;
                    index = indexOf(methodName, index + 1, methodpar);
                    if (index < 0) return info;
                    if (index < methodBody.size() && index >= 0) {
                        body = methodBody.get(index);

                    }

                }

            }
            String[] bodyStmt = body.split("\n");
            //解析获取其中的variable和其n.getVariableParentNode(初始语句)+callSetStmt里面包含variable的
            StringBuffer code = new StringBuffer(body);
            code.insert(0, "public class test{");
            code.append("}");

            Map<String, List> codeMap = new HashMap<>();
            try {
                codeMap = JavaParserUtils.getData(code.toString(), true);
                var_stmtMap = JavaParserUtils.var_stmtMap;
                entUsageMap = JavaParserUtils.entUsageMap;
                List<String> containVarBody = codeMap.get("method_body");
                String[] stmtlist = containVarBody.get(0).split("\n");

                if (var_stmtMap.containsKey(id)) {

                    context.append(var_stmtMap.get(id) + ";");
                }
                //获取使用语句
                if (entUsageMap.containsKey(id)) {
                    context.append(entUsageMap.get(id));
                }
            } catch (Exception e) {
                return info;
            }

            try {
                //找到其包含的所有实体(set)
                StringBuffer code_context = new StringBuffer(context.toString().trim());
                code_context.insert(0, "public class test{public void test(){");
                code_context.append("}}");

                System.out.println("code context=" + code_context);
                Map<String, List> contextmap;
                try {
                    contextmap = JavaParserUtils.getData(code_context.toString(), true);
                } catch (Exception e) {
                    return info;
                }
                List<String> containent_method = contextmap.get("call_relation");
                List<String> containcall_method = contextmap.get("method_call");


                Set<String> set = new HashSet<>();

                for (String nameExpr : containent_method) {
                    //得nameexpr父类
                    if (nameExpr.equals(id)) {
                        set.add(nameExpr);
                    }

                }
                for (String callExpr : containcall_method) {

                    set.add(callExpr);


                }


                for (String s : set) {
                    node.add(s);
                    List<String> edgePair = new ArrayList<>();
                    edgePair.add(id);
                    edgePair.add(s);
                    edge.add(edgePair);
                }

            } catch (NullPointerException e) {
                System.out.println("ERR ID==" + id);
            }

        }
        //2. 针对field处理
        if (type == 4) {

            //解析获取文件中的n.getVariableParentNode(初始语句)+callSetStmt里面的
            StringBuffer code_context = new StringBuffer();
            code_context.append("public class test{");

            field_stmtMap = JavaParserUtils.field_stmtMap;
            entUsageMap = JavaParserUtils.entUsageMap;

            if (field_stmtMap.containsKey(id)) {


                context.append(field_stmtMap.get(id));
                code_context.append(field_stmtMap.get(id));
            }


            //获取使用语句
            code_context.append("public void test(){");
            if (entUsageMap.containsKey(id)) {
                String usage = "";
                int i = entUsageMap.get(id).indexOf(";;");
                if (i != -1) {
                    usage = entUsageMap.get(id).replaceAll(";;", ";");
                } else {
                    usage = entUsageMap.get(id);
                }

                context.append(usage);
                code_context.append(usage);
            }
            code_context.append("}}");
            try {
                //找到其包含的所有实体(set)
                System.out.println("code context=\n" + code_context);
                Map<String, List> contextmap;
                try {
                    contextmap = JavaParserUtils.getData(code_context.toString(), true);
                } catch (Exception e) {
                    return info;
                }
                List<String> containent_method = contextmap.get("call_relation");
                List<String> containcall_method = contextmap.get("method_call");


                Set<String> set = new HashSet<>();

                for (String nameExpr : containent_method) {
                    //得nameexpr父类
                    if (nameExpr.equals(id)) {
                        set.add(nameExpr);
                    }

                }
                for (String callExpr : containcall_method) {

                    set.add(callExpr);


                }


                for (String s : set) {
                    node.add(s);
                    List<String> edgePair = new ArrayList<>();
                    edgePair.add(id);
                    edgePair.add(s);
                    edge.add(edgePair);
                }
//


            } catch (NullPointerException e) {
                System.out.println("ERR ID==" + id);
            }

        }
        //针对class处理
        if (type == 2) {
            if (classMap.containsKey(id.trim())) {
                context.append(classMap.get(id));
            }

            //相关实体 其extend或者implement的或者没有
            if (class_ent.containsKey(id.trim())) {
                node = class_ent.get(id);
                for (String res : node) {
                    List<String> edgePair = new ArrayList<>();
                    edgePair.add(id);
                    edgePair.add(res);
                    edge.add(edgePair);
                }
            }


        }

        for (List<String> e : edge) {
            edges.append("<" + e.get(0) + "," + e.get(1) + ">" + "|");
        }

        String[] csvContent = {String.valueOf(type), loc, id, normText(context.toString()), edges.toString()};
        info = Arrays.asList(csvContent);


        return info;
    }



    public static int indexOf(List<?> list, int start, Object value) {

        int idx = list.subList(start, list.size()).indexOf(value);
        return idx != -1 ? idx + start : -1;
    }

    private static iden getIdenInfo(String id, Vector<iden> alliden) {
        iden res = null;
        for (iden d : alliden) {
            if (d.getIdentifier().toString().equals(id)) {
                res = d;
                break;
            }

        }
        return res;
    }

    public static String normText(String str) {
        String dest = "";
        if (str != null) {
            Pattern p = Pattern.compile("\t|\r|\n");
            Matcher m = p.matcher(str);
            dest = m.replaceAll("");
        }
        //去除注释
        return dest;
    }

    public static Vector<iden> ObtainIdentifier(Vector<String> allstate, String javafilepath) throws Exception {

        Vector<IdentifierDS> packages = new Vector<IdentifierDS>();  //加入当前的package
        Vector<IdentifierDS> types = new Vector<IdentifierDS>();     //类，接口，枚举
        Vector<IdentifierDS> methods = new Vector<IdentifierDS>();   //method，包括了constructor,setter,getter
        Vector<IdentifierDS> fields = new Vector<IdentifierDS>();    //
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
            IdentifierDS newclass = new IdentifierDS(one.getClassname(), "", one.getClassname(), "class", "", one.getIndex());
            types.add(newclass);

            Vector<MethodDS> methodlist = one.getMethodlist();
            for (MethodDS onemethod : methodlist) {
                IdentifierDS newmethod = new IdentifierDS(one.getClassname(), onemethod.getMethodname(), onemethod.getMethodname(), onemethod.getReturntype(), "", onemethod.getBeginindex());
                methods.add(newmethod);

                Vector<IdentifierDS> parameters = onemethod.getParameters();
                //var 包括了参数
                for (IdentifierDS oneid : parameters) {
                    variables.add(oneid);
                }

            }

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

                IdentifierDS oneid = new IdentifierDS(classpar, methodpar, name, type, "", onevalue);

                variables.add(oneid);

            }
        }


        Vector<iden> allid = new Vector<>();
        for (IdentifierDS onepackage : packages) {
//        	System.out.println(onepackage.toString());
            String identifiername = onepackage.getName();
            int location = onepackage.getLocation();
            String singlestate = "";
            int purelocation = -1;
            if (location - 1 >= 0 && allstate.size() > 0) {
                singlestate = allstate.get(location - 1);
                purelocation = location - 1;
            }

            if (singlestate.contains(identifiername)) {
//                iden oness=new iden(1,identifiername,singlestate,purelocation);
                iden oness = new iden(1, identifiername, singlestate, purelocation, onepackage.getClasspar(), onepackage.getMethodpar(), onepackage.getType(), onepackage.getDefaultvalue());

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
//                iden oness=new iden(2,identifiername,singlestate,purelocation);
                iden oness = new iden(2, identifiername, singlestate, purelocation, onetype.getClasspar(), onetype.getMethodpar(), onetype.getType(), onetype.getDefaultvalue());

                allid.add(oness);
            } else {
                System.err.println("2: 标识符位置不对！" + identifiername + "  " + singlestate);
            }

        }
        for (IdentifierDS onemethod : methods) {
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
//                iden oness=new iden(3,identifiername,singlestate,purelocation);
                iden oness = new iden(3, identifiername, singlestate, purelocation, onemethod.getClasspar(), onemethod.getMethodpar(), onemethod.getType(), onemethod.getDefaultvalue());

                allid.add(oness);
            } else {
                System.err.println("3: 标识符位置不对！" + identifiername + "  " + singlestate);
            }
        }
        for (IdentifierDS onefield : fields) {
            String identifiername = onefield.getName();
            int location = onefield.getLocation();
            String singlestate = allstate.get(location - 1);
            int purelocation = location - 1;
            if (singlestate.contains(identifiername)) {

//                iden oness=new iden(4,identifiername,singlestate,purelocation);
                iden oness = new iden(4, identifiername, singlestate, purelocation, onefield.getClasspar(), onefield.getMethodpar(), onefield.getType(), onefield.getDefaultvalue());

                allid.add(oness);
            } else {
                System.err.println("4: 标识符位置不对！" + identifiername + "  " + singlestate);
            }
        }
        for (IdentifierDS onevariable : variables) {
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
//                iden oness=new iden(5,identifiername,singlestate,purelocation);
                iden oness = new iden(5, identifiername, singlestate, purelocation, onevariable.getClasspar(), onevariable.getMethodpar(), onevariable.getType(), onevariable.getDefaultvalue());

                allid.add(oness);
            } else {
                if (purelocation + 1 < allstate.size()) {
                    singlestate = singlestate + " " + allstate.get(purelocation + 1);
                    //        		purelocation=location+1;
                    if (singlestate.contains(identifiername)) {
                        iden oness = new iden(5, identifiername, singlestate, purelocation);
                        allid.add(oness);
                    } else {
                        System.err.println("5: 标识符位置不对！" + identifiername + "  " + singlestate);
                    }
                }
            }
        }


        return allid;

    }



}
