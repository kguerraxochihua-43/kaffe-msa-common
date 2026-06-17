package com.kaffe.common.sql;

import java.util.regex.Pattern;

public final class SqlSchemas {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private SqlSchemas() {
    }

    public static String table(String schema, String table) {
        return schema(schema) + "." + identifier(table, "table");
    }

    public static String schema(String schema) {
        return identifier(schema, "schema");
    }

    private static String identifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SQL " + label + " identifier: " + value);
        }
        return value;
    }
}
