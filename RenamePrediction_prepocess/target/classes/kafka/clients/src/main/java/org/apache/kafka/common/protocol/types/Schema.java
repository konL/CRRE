package org.apache.kafka.common.protocol.types;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The schema for a compound record definition
 */
public class Schema extends Type {

    private final Field[] fields;
    private final Map<String, Field> fieldsByName;

    public Schema(Field... fs) {
        this.fields = new Field[fs.length];
        this.fieldsByName = new HashMap<String, Field>();
        for (int i = 0; i < this.fields.length; i++) {
            Field field = fs[i];
            if (fieldsByName.containsKey(field.name))
                throw new SchemaException("Schema contains a duplicate field: " + field.name);
            this.fields[i] = new Field(i, field.name, field.type, field.doc, field.defaultValue, this);
            this.fieldsByName.put(fs[i].name, this.fields[i]);
        }
    }

    /**
     * Write a struct to the buffer
     */
    public void write(ByteBuffer buffer, Object o) {
        Struct r = (Struct) o;
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            try {
                Object value = f.type().validate(r.get(f));
                f.type.write(buffer, value);
            } catch (Exception e) {
                throw new SchemaException("Error writing field '" + f.name + "': " + e.getMessage() == null ? e.getMessage() : e.getClass()
                                                                                                                                .getName());
            }
        }
    }

    /**
     * Read a struct from the buffer
     */
    public Object read(ByteBuffer buffer) {
        Object[] objects = new Object[fields.length];
        for (int i = 0; i < fields.length; i++)
            objects[i] = fields[i].type.read(buffer);
        return new Struct(this, objects);
    }

    /**
     * The size of the given record
     */
    public int sizeOf(Object o) {
        int size = 0;
        Struct r = (Struct) o;
        for (int i = 0; i < fields.length; i++)
            size += fields[i].type.sizeOf(r.get(fields[i]));
        return size;
    }

    /**
     * The number of fields in this schema
     */
    public int numFields() {
        return this.fields.length;
    }

    /**
     * Get a field by its slot in the record array
     * 
     * @param slot The slot at which this field sits
     * @return The field
     */
    public Field get(int slot) {
        return this.fields[slot];
    }

    /**
     * Get a field by its name
     * 
     * @param name The name of the field
     * @return The field
     */
    public Field get(String name) {
        return this.fieldsByName.get(name);
    }

    /**
     * Get all the fields in this schema
     */
    public Field[] fields() {
        return this.fields;
    }

    /**
     * Display a string representation of the schema
     */
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append('{');
        for (int i = 0; i < this.fields.length; i++) {
            b.append(this.fields[i].name);
            b.append(':');
            b.append(this.fields[i].type());
            if (i < this.fields.length - 1)
                b.append(',');
        }
        b.append("}");
        return b.toString();
    }

    @Override
    public Struct validate(Object item) {
        try {
            Struct struct = (Struct) item;
            for (int i = 0; i < this.fields.length; i++) {
                Field field = this.fields[i];
                try {
                    field.type.validate(struct.get(field));
                } catch (SchemaException e) {
                    throw new SchemaException("Invalid value for field '" + field.name + "': " + e.getMessage());
                }
            }
            return struct;
        } catch (ClassCastException e) {
            throw new SchemaException("Not a Struct.");
        }
    }

}