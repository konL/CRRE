package dataBuild;


public class iden
{
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

    public void setClasspar(String classpar) {
        this.classpar = classpar;
    }

    public String getMethodpar() {
        return methodpar;
    }

    public void setMethodpar(String methodpar) {
        this.methodpar = methodpar;
    }
    public int getType() {
        return type;
    }
    public void setType(int type) {
        this.type = type;
    }
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    public String getStatement() {
        return statement;
    }
    public void setStatement(String statement) {
        this.statement = statement;
    }
    public int getLocation() {
        return location;
    }
    public void setLocation(int location) {
        this.location = location;
    }
    public iden(int type, String identifier, String statement, int location) {
        super();
        this.type = type;
        this.identifier = identifier;
        this.statement = statement;
        this.location = location;
    }
    public iden(int type, String identifier, String statement, int location,String classpar,String methodpar,String IdType,String defaultValue) {
        super();
        this.type = type;
        this.identifier = identifier;
        this.statement = statement;
        this.location = location;
        this.classpar=classpar;
        this.methodpar=methodpar;
        this.IdType=IdType;

    }
    @Override
    public String toString() {
        return "iden [type=" + type + ", identifier=" + identifier + ", statement=" + statement + ", location="
                + location + "]";
    }

}

