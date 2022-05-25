package dataCollect;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import jdk.nashorn.internal.ir.BlockStatement;
import jdk.nashorn.internal.ir.IfNode;

import java.io.FileInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class JavaParserUtils {
    static List<String> methodName;
    static List<String> methodName_extend;
    static List<String> fieldsName;
    static List<String> methodBody;
    static List<String> varibleName;
    static List<String> callSet;
    static List<String> methodCall;
    //存储对应的xxName和xxxDeclaration
    public static Map<String, FieldDeclaration> fieldMap;
    //局部变量的名字-》父类的映射
    public static Map<String, VariableDeclarationExpr> variableMap;
    public static Map<String, String> var_stmtMap;
    public static Map<String, String> field_stmtMap;
    static Map<String, MethodDeclaration> methodMap;
    static Map<String, MethodDeclaration> methodMap_extend;
    //callSet:方法参数，方法内的全局变量
    public static Map<String, String> nameExprMap;
    public static Map<String, String> entUsageMap;

    static Map<String, String> methodCallMap;
    public static Map<String, List<String>> class_ent;
    public static Map<String, String> classMap;
    static List<String> ifSet=new ArrayList<>();
    static List<String> whileSet=new ArrayList<>();
    static List<String> biSet=new ArrayList<>();


    public static Map<String, List> getData(String code, Boolean isCode) throws Exception {
//        FileInputStream in = new FileInputStream("D:\\kon_data\\JAVA_DATA\\SpotRenaming\\ast-javaParser\\src\\main\\resources\\Srccode.java");


        // parse the file
        methodName = new ArrayList<String>();
        methodName_extend = new ArrayList<String>();
        fieldsName = new ArrayList<String>();
        methodBody = new ArrayList<String>();
        varibleName = new ArrayList<String>();
        callSet = new ArrayList<String>();
        methodCall = new ArrayList<String>();
        //存储对应的xxName和xxxDeclaration
        fieldMap = new HashMap<>();
        //局部变量的名字-》父类的映射
        variableMap = new HashMap<>();
        var_stmtMap = new HashMap<>();
        field_stmtMap = new HashMap<>();
        methodMap = new HashMap<>();
        methodMap_extend = new HashMap<>();
        //callSet:方法参数，方法内的全局变量
        nameExprMap = new HashMap<>();
        methodCallMap = new HashMap<>();
        entUsageMap=new HashMap<>();
        class_ent=new HashMap<>();
        classMap = new HashMap<>();
        CompilationUnit cu = null;
        cu = JavaParser.parse(code);


        // prints the resulting compilation unit to default system output
//        System.out.println(cu.toString());

        cu.accept(new Visitor(), null);
//        //查看list
//        for(String mn:methodBody){
//            System.out.println(mn);
//        }
        Map<String, List> map = new HashMap<>();
        map.put("fields_name", fieldsName);
        map.put("method_name", methodName);
        map.put("method_name_extend", methodName_extend);
        map.put("method_body", methodBody);
        map.put("variable_name", varibleName);
        map.put("call_relation", callSet);
        map.put("method_call", methodCall);
        return map;


    }

    public static Map<String, List> getData(String file) throws Exception {
//        FileInputStream in = new FileInputStream("D:\\kon_data\\JAVA_DATA\\SpotRenaming\\ast-javaParser\\src\\main\\resources\\Srccode.java");

        methodName = new ArrayList<String>();
        methodName_extend = new ArrayList<String>();
        fieldsName = new ArrayList<String>();
        methodBody = new ArrayList<String>();
        varibleName = new ArrayList<String>();
        var_stmtMap = new HashMap<>();
        field_stmtMap = new HashMap<>();
        callSet = new ArrayList<String>();
        methodCall = new ArrayList<String>();
        //存储对应的xxName和xxxDeclaration
        fieldMap = new HashMap<>();
        //局部变量的名字-》父类的映射
        variableMap = new HashMap<>();
        methodMap = new HashMap<>();
        methodMap_extend = new HashMap<>();
        //callSet:方法参数，方法内的全局变量
        nameExprMap = new HashMap<>();
        entUsageMap=new HashMap<>();

        methodCallMap = new HashMap<>();
        class_ent=new HashMap<>();
        classMap = new HashMap<>();
        FileInputStream in = new FileInputStream(file);

        // parse the file
        CompilationUnit cu = JavaParser.parse(in);


        // prints the resulting compilation unit to default system output
//        System.out.println(cu.toString());

        cu.accept(new Visitor(), null);
//        //查看list
//        for(String mn:methodBody){
//            System.out.println(mn);
//        }
        Map<String, List> map = new HashMap<>();
        map.put("fields_name", fieldsName);
        map.put("method_name", methodName);
        map.put("method_name_extend", methodName_extend);
        map.put("method_body", methodBody);
        map.put("variable_name", varibleName);
        map.put("call_relation", callSet);

        map.put("method_call", methodCall);
        return map;


    }

    public static String getParents(final NameExpr nameExp) {
        final StringBuilder path = new StringBuilder();

        nameExp.walk(Node.TreeTraversal.PARENTS, node -> {
            if (node instanceof ClassOrInterfaceDeclaration) {
                path.insert(0, ((ClassOrInterfaceDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof ObjectCreationExpr) {
                path.insert(0, ((ObjectCreationExpr) node).getType().getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof MethodDeclaration) {
                path.insert(0, ((MethodDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof CompilationUnit) {
                final Optional<PackageDeclaration> pkg = ((CompilationUnit) node).getPackageDeclaration();
                if (pkg.isPresent()) {
                    path.replace(0, 1, ".");
                    path.insert(0, pkg.get().getNameAsString());
                }
            }
        });

        // convert StringBuilder into String and return the String
        //System.out.println("parents:"+path.toString());
        return path.toString();
    }

    public static String getParents(final MethodCallExpr methodCallExpr) {
        final StringBuilder path = new StringBuilder();

        methodCallExpr.walk(Node.TreeTraversal.PARENTS, node -> {
            if (node instanceof ClassOrInterfaceDeclaration) {
                path.insert(0, ((ClassOrInterfaceDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof ObjectCreationExpr) {
                path.insert(0, ((ObjectCreationExpr) node).getType().getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof MethodDeclaration) {
                path.insert(0, ((MethodDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof CompilationUnit) {
                final Optional<PackageDeclaration> pkg = ((CompilationUnit) node).getPackageDeclaration();
                if (pkg.isPresent()) {
                    path.replace(0, 1, ".");
                    path.insert(0, pkg.get().getNameAsString());
                }
            }
        });

        // convert StringBuilder into String and return the String
        //System.out.println("parents:"+path.toString());
        return path.toString();
    }

    public static String getParents(final MethodDeclaration methodDeclaration) {
        final StringBuilder path = new StringBuilder();

        methodDeclaration.walk(Node.TreeTraversal.PARENTS, node -> {
            if (node instanceof ClassOrInterfaceDeclaration) {
                path.insert(0, ((ClassOrInterfaceDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof ObjectCreationExpr) {
                path.insert(0, ((ObjectCreationExpr) node).getType().getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof MethodDeclaration) {
                path.insert(0, ((MethodDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof CompilationUnit) {
                final Optional<PackageDeclaration> pkg = ((CompilationUnit) node).getPackageDeclaration();
                if (pkg.isPresent()) {
                    path.replace(0, 1, ".");
                    path.insert(0, pkg.get().getNameAsString());
                }
            }
        });

        // convert StringBuilder into String and return the String
//        System.out.println("parents:"+path.toString());
        return path.toString();
    }

    public static String getParents(final VariableDeclarationExpr variableDeclaration) {
        final StringBuilder path = new StringBuilder();

        variableDeclaration.walk(Node.TreeTraversal.PARENTS, node -> {
            if (node instanceof ClassOrInterfaceDeclaration) {
                path.insert(0, ((ClassOrInterfaceDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof ObjectCreationExpr) {
                path.insert(0, ((ObjectCreationExpr) node).getType().getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof MethodDeclaration) {
                path.insert(0, ((MethodDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof CompilationUnit) {
                final Optional<PackageDeclaration> pkg = ((CompilationUnit) node).getPackageDeclaration();
                if (pkg.isPresent()) {
                    path.replace(0, 1, ".");
                    path.insert(0, pkg.get().getNameAsString());
                }
            }
        });

        // convert StringBuilder into String and return the String
//        System.out.println("parents:"+path.toString());
        return path.toString();
    }

    public static String getParents(final FieldDeclaration fieldDeclaration) {
        final StringBuilder path = new StringBuilder();

        fieldDeclaration.walk(Node.TreeTraversal.PARENTS, node -> {
            if (node instanceof ClassOrInterfaceDeclaration) {
                path.insert(0, ((ClassOrInterfaceDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof ObjectCreationExpr) {
                path.insert(0, ((ObjectCreationExpr) node).getType().getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof MethodDeclaration) {
                path.insert(0, ((MethodDeclaration) node).getNameAsString());
                path.insert(0, '.');
            }
            if (node instanceof CompilationUnit) {
                final Optional<PackageDeclaration> pkg = ((CompilationUnit) node).getPackageDeclaration();
                if (pkg.isPresent()) {
                    path.replace(0, 1, ".");
                    path.insert(0, pkg.get().getNameAsString());
                }
            }
        });

        // convert StringBuilder into String and return the String
//        System.out.println("parents:"+path.toString());
        return path.toString();
    }


    private static class Visitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(final FieldDeclaration n, Void arg) {
//            System.out.println("Fields:"+n.getVariables());
            String field = n.getVariables().toString();
            int eq = field.indexOf("=");
            String fid = "";
            if (eq != -1) {
                fid = field.substring(1, eq);
            } else {
                fid = field.substring(1, field.length() - 1);
            }


            fieldsName.add(fid.trim());
            fieldMap.put(fid.trim(), n);
            StringBuffer b = new StringBuffer();
            if (n.getVariables().getParentNode().isPresent()) {
                if (n.getVariables().getParentNode().get() != null) {
                    b.append(n.getVariables().getParentNode().get().toString());
//
                    field_stmtMap.put(fid.trim(), b.toString());

                }

            }
            super.visit(n, arg);

        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            /* here you can access the attributes of the method.
             this method will be called for all methods in this
             CompilationUnit, including inner class methods */

            StringBuffer param = new StringBuffer();
            for (String par : n.getParameters().toString().substring(1, n.getParameters().toString().length() - 1).split(",")) {
                param.append(par.trim() + "#");
            }

            String method_extend = n.getType().toString() + "@@@@" + n.getNameAsString() + "@@@@" + param.toString();
            methodName_extend.add(method_extend);
            methodMap_extend.put(method_extend, n);

            methodName.add(n.getNameAsString());
            methodMap.put(n.getNameAsString(), n);

            //首先分割
//            System.out.println("declare="+n.getDeclarationAsString());
            StringBuffer b = new StringBuffer();
            if (n.getBody().isPresent()) {
                if (n.getBody().get().getStatements().isNonEmpty()) {
                    for (Statement bs : n.getBody().get().getStatements()) {

                        b.append(bs.toString() + "\n");
                    }
                }
            }


            StringBuffer body = new StringBuffer();
            body.append(n.getDeclarationAsString());
            body.append("{");
            body.append(b.toString() + "}");
            methodBody.add(body.toString());


            //getParents(n);


            super.visit(n, arg);
        }





        @Override
        public void visit(VariableDeclarationExpr n, Void arg) {
            //System.out.println(n.getVariables());

            String data = n.getVariables().toString();

            String[] set = data.substring(1, data.length() - 1).split("=");

            varibleName.add(set[0].trim());
            variableMap.put(set[0].trim(), n);

            StringBuffer b = new StringBuffer();
            if (n.getVariables().getParentNode().isPresent()) {
                if (n.getVariables().getParentNode().get() != null) {

                    b.append(n.getVariables().getParentNode().get().toString());
//                    System.out.println("variables=" + set[0].trim()+","+b.toString());
                    var_stmtMap.put(set[0].trim(), b.toString());

                }

            }




            super.visit(n, arg);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
//            System.out.println("class:"+n.getName());
//            System.out.println("extends:"+n.getExtendedTypes());
//            System.out.println("implements:"+n.getImplementedTypes());
            List<String> ent=new ArrayList<>();
            for(ClassOrInterfaceType extend:n.getExtendedTypes()){
                ent.add(extend.getElementType().asString());
            }
            for(ClassOrInterfaceType imp:n.getImplementedTypes()){
                ent.add(imp.getElementType().toString());
            }
            class_ent.put(n.getNameAsString(),ent);


            StringBuffer classinfo=new StringBuffer();
            if(n.getParentNode().isPresent()){
                String[] data=n.getParentNode().get().toString().split("\n");
                for(String s:data){
                    if(s.contains(n.getNameAsString())) {

                        classinfo.append(s.trim());


                    }
                }
            }
            classMap.put(n.getNameAsString(), classinfo.toString());

//            List<String> classInfo = new ArrayList<>();
//            classInfo.add(n.getExtendedTypes().toString().replace("[", "").replace("]", ""));
//            classInfo.add(n.getImplementedTypes().toString().replace("[", "").replace("]", ""));
//
//
//            if (n.isInnerClass()) {
//                classInfo.add(n.getParentNode().toString());
//            }
//
//
//
////            classMap.put(n.getName().toString(), classInfo);


            super.visit(n, arg);
        }

        @Override
        public void visit(PackageDeclaration n, Void arg) {

            super.visit(n, arg);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
//            System.out.println("MethodCallExpr:"+n.getNameAsString());
//            System.out.println("parent:"+getParents(n));
            methodCall.add(n.getNameAsString());

            if (methodCallMap.get(n.getNameAsString()) == null) {
                methodCallMap.put(n.getNameAsString(), getParents(n));
//                System.out.println("map:"+methodCallMap.get(n.getNameAsString()));
            } else {
                //多个父类
                methodCallMap.put(n.getNameAsString(), methodCallMap.get(n.getNameAsString()) + "@@@@" + (getParents(n)));
//                System.out.println("map:"+methodCallMap.get(n.getNameAsString()));
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(IfStmt n, Void arg) {
            ifSet.add(n.getCondition().toString());
            super.visit(n, arg);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            whileSet.add(n.getCondition().toString());
            super.visit(n, arg);
        }

        @Override
        public void visit(BinaryExpr n, Void arg) {
            biSet.add(n.toString());
            super.visit(n, arg);
        }

        @Override
        public void visit(NameExpr n, Void arg) {
            //System.out.println("NameExpr:"+n.getName());
            getParents(n);
            callSet.add(n.getNameAsString());


            if(n.getParentNode().isPresent()){

                String usageStmt="";
//                包含了binary的usage丢弃（处理太麻烦了）
//                System.out.println(biSet.size());

                    //包含逻辑比较
                String stmt=n.getParentNode().get().toString();

                if(stmt.contains("==")||stmt.contains("!=")||stmt.contains(">")||stmt.contains(">=")||stmt.contains("<")||stmt.contains("<=")||stmt.contains("||")||stmt.contains("&&")||stmt.contains(" instanceof ")||stmt.startsWith("!")) {


                        //包含二元比对的：三元组，
                        if (n.getParentNode().get().toString().indexOf("?") != -1) {
                            usageStmt = "W w=" + n.getParentNode().get().toString();

                        }
                        else {

                            usageStmt = "if(" + n.getParentNode().get().toString() + "){}";
//                            for (String ifStmt : ifSet) {
//                                if (ifStmt.contains(n.getParentNode().get().toString())) {
//                                    usageStmt = "if(" + n.getParentNode().get().toString() + "){}";
//                                    System.out.println("usage stmt="+usageStmt);
//                                }
//                            }
//                            for (String ifStmt : whileSet) {
//                                if (ifStmt.contains(n.getParentNode().get().toString())) {
//                                    usageStmt = "if(" + n.getParentNode().get().toString() + "){}";
//                                }
//                            }

                            if (!usageStmt.contains("if")) {
                                usageStmt = n.getParentNode().get().toString();
                            }
//                            if (ifSet.contains(n.getParentNode().get().toString()) || whileSet.contains(n.getParentNode().get().toString())) {
//                                System.out.println(n.getParentNode().toString());
//                                usageStmt = "if(" + n.getParentNode().get().toString() + "){}";
//                            } else {
//                                usageStmt = n.getParentNode().get().toString();
//                            }
                        }

                    } else {
                        usageStmt = n.getParentNode().get().toString();
                    }
                //字符串
                if (usageStmt.contains("\"")&&usageStmt.contains("+")&&!usageStmt.contains("if")&&!usageStmt.contains("while")){
                    usageStmt="s="+usageStmt;
                }
                //三元组：
                if(usageStmt.contains("?")&&usageStmt.contains(":")){
                    if(usageStmt.indexOf("?")<usageStmt.indexOf((":"))) {
                        usageStmt = "W w=" + n.getParentNode().get().toString();
                    }
                }


//                System.out.println("ori usageStmt==="+n.getParentNode().get().toString());
                if(usageStmt.startsWith("\"")||usageStmt.startsWith("\\\\")||usageStmt.startsWith("\\*")||usageStmt.endsWith("*\\")||usageStmt.startsWith("this(")){
                    usageStmt="";

                }
                if((!usageStmt.contains("="))){
                    if(usageStmt.contains("+")||usageStmt.contains("-")||usageStmt.contains("*")||usageStmt.contains("/")||usageStmt.contains("%")){
                        usageStmt="";
                    }
                }
                //数组
                if (usageStmt.startsWith("[")){
                    usageStmt="";
                }
                if(usageStmt.endsWith("}")&& !usageStmt.contains("if(")){
//                    System.out.println("ent usageStmt==="+usageStmt);
                    if(usageStmt.endsWith(";}")) {
                        usageStmt = usageStmt;
                    }
                    else{
//                        StringBuffer sb=new StringBuffer(usageStmt);
//                        sb.insert(usageStmt.length()-2,';');
//                        usageStmt=sb.toString();
                        usageStmt="";
                    }
                }else{
                    usageStmt=usageStmt+";";
                }
                if(usageStmt.startsWith("(")||(usageStmt.contains(":")&&usageStmt.contains("for"))){
                    usageStmt="";
                }
                //仅包含字母则去掉
                String regex=".*[a-zA-Z]+.*";
                Matcher m= Pattern.compile(regex).matcher(usageStmt);
                if(!m.matches()) {
                    usageStmt="";
                }


                if(entUsageMap.get(n.getNameAsString())==null) {

//                    entUsageMap.put(n.getNameAsString(), n.getParentNode().get().toString()+";");

                    entUsageMap.put(n.getNameAsString(), usageStmt);
                }else {
//                    entUsageMap.put(n.getNameAsString(), entUsageMap.get(n.getNameAsString())+"\n"+n.getParentNode().get().toString()+";");
                    if( !entUsageMap.get(n.getNameAsString()).contains(usageStmt)) {
                        entUsageMap.put(n.getNameAsString(), entUsageMap.get(n.getNameAsString()) + "\n" + usageStmt );
                    }
                }
//                System.out.println("ent stmt=" + n.getParentNode());
            }


            //就是函数中调用的东西
            if (nameExprMap.get(n.getNameAsString()) == null) {
                nameExprMap.put(n.getNameAsString(), getParents(n));
            } else {
                //多个父类
                nameExprMap.put(n.getNameAsString(), nameExprMap.get(n.getNameAsString()) + "@@@@" + (getParents(n)));
            }
            super.visit(n, arg);
        }


    }
}
