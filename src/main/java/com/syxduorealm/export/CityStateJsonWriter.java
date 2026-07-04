package com.syxduorealm.export;

import java.util.Iterator;
import java.util.Map;

public final class CityStateJsonWriter {

    private final StringBuilder out = new StringBuilder(4096);
    private int indent;

    private CityStateJsonWriter() {
    }

    public static String write(CityState state) {
        return writeValueAsJson(state.toJsonMap());
    }

    public static String writeMap(Map<String, ?> map) {
        return writeValueAsJson(map);
    }

    private static String writeValueAsJson(Object value) {
        CityStateJsonWriter writer = new CityStateJsonWriter();
        writer.writeValue(value);
        writer.out.append(System.lineSeparator());
        return writer.out.toString();
    }

    private void writeValue(Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            writeString(string);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof CityState.RacePopulation racePopulation) {
            writeValue(racePopulation.toJsonMap());
        } else if (value instanceof CityState.ResourceAmount resourceAmount) {
            writeValue(resourceAmount.toJsonMap());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(map);
        } else if (value instanceof Iterable<?> iterable) {
            writeArray(iterable);
        } else {
            writeString(value.toString());
        }
    }

    private void writeObject(Map<?, ?> map) {
        out.append('{');
        indent++;

        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            newline();
            writeString(String.valueOf(entry.getKey()));
            out.append(": ");
            writeValue(entry.getValue());
            if (iterator.hasNext()) {
                out.append(',');
            }
        }

        indent--;
        if (!map.isEmpty()) {
            newline();
        }
        out.append('}');
    }

    private void writeArray(Iterable<?> values) {
        out.append('[');
        indent++;

        Iterator<?> iterator = values.iterator();
        while (iterator.hasNext()) {
            newline();
            writeValue(iterator.next());
            if (iterator.hasNext()) {
                out.append(',');
            }
        }

        indent--;
        newline();
        out.append(']');
    }

    private void writeString(String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20 || ch > 0x7E) {
                        writeUnicodeEscape(ch);
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    private void writeUnicodeEscape(char ch) {
        out.append("\\u");
        String hex = Integer.toHexString(ch);
        for (int pad = hex.length(); pad < 4; pad++) {
            out.append('0');
        }
        out.append(hex);
    }

    private void newline() {
        out.append(System.lineSeparator());
        for (int i = 0; i < indent; i++) {
            out.append("  ");
        }
    }
}
