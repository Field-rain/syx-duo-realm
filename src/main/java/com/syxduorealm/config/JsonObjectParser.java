package com.syxduorealm.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonObjectParser {

    private final String source;
    private int index;

    private JsonObjectParser(String source) {
        this.source = source;
    }

    public static Map<String, Object> parse(String source) {
        JsonObjectParser parser = new JsonObjectParser(source);
        Map<String, Object> value = parser.readObject();
        parser.skipWhitespace();
        if (!parser.isDone()) {
            throw parser.error("Unexpected trailing content.");
        }
        return value;
    }

    public static Object parseValue(String source) {
        JsonObjectParser parser = new JsonObjectParser(source);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.isDone()) {
            throw parser.error("Unexpected trailing content.");
        }
        return value;
    }

    private Map<String, Object> readObject() {
        skipWhitespace();
        expect('{');

        Map<String, Object> out = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            index++;
            return out;
        }

        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            Object value = readValue();
            out.put(key, value);
            skipWhitespace();

            if (peek(',')) {
                index++;
            } else if (peek('}')) {
                index++;
                return out;
            } else {
                throw error("Expected ',' or '}'.");
            }
        }
    }

    private Object readValue() {
        skipWhitespace();
        if (isDone()) {
            throw error("Unexpected end of input.");
        }

        char ch = source.charAt(index);
        if (ch == '"') {
            return readString();
        }
        if (ch == '{') {
            return readObject();
        }
        if (ch == '[') {
            return readArray();
        }
        if (ch == 't') {
            readLiteral("true");
            return Boolean.TRUE;
        }
        if (ch == 'f') {
            readLiteral("false");
            return Boolean.FALSE;
        }
        if (ch == 'n') {
            readLiteral("null");
            return null;
        }
        if (ch == '-' || Character.isDigit(ch)) {
            return readNumber();
        }
        throw error("Unexpected value.");
    }

    private List<Object> readArray() {
        skipWhitespace();
        expect('[');

        List<Object> out = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return out;
        }

        while (true) {
            out.add(readValue());
            skipWhitespace();

            if (peek(',')) {
                index++;
            } else if (peek(']')) {
                index++;
                return out;
            } else {
                throw error("Expected ',' or ']'.");
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();

        while (!isDone()) {
            char ch = source.charAt(index++);
            if (ch == '"') {
                return out.toString();
            }
            if (ch != '\\') {
                out.append(ch);
                continue;
            }

            if (isDone()) {
                throw error("Unexpected end of escape sequence.");
            }

            char escaped = source.charAt(index++);
            switch (escaped) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> out.append(readUnicodeEscape());
                default -> throw error("Unsupported escape sequence.");
            }
        }

        throw error("Unterminated string.");
    }

    private char readUnicodeEscape() {
        if (index + 4 > source.length()) {
            throw error("Incomplete unicode escape.");
        }

        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(source.charAt(index++), 16);
            if (digit < 0) {
                throw error("Invalid unicode escape.");
            }
            value = (value << 4) + digit;
        }
        return (char) value;
    }

    private Number readNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        readDigits();

        boolean decimal = false;
        if (peek('.')) {
            decimal = true;
            index++;
            readDigits();
        }

        if (peek('e') || peek('E')) {
            decimal = true;
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            readDigits();
        }

        String number = source.substring(start, index);
        try {
            return decimal ? Double.parseDouble(number) : Long.parseLong(number);
        } catch (NumberFormatException e) {
            throw error("Invalid number.");
        }
    }

    private void readDigits() {
        int start = index;
        while (!isDone() && Character.isDigit(source.charAt(index))) {
            index++;
        }
        if (start == index) {
            throw error("Expected digit.");
        }
    }

    private void readLiteral(String literal) {
        if (!source.startsWith(literal, index)) {
            throw error("Expected '" + literal + "'.");
        }
        index += literal.length();
    }

    private void skipWhitespace() {
        while (!isDone() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
    }

    private void expect(char expected) {
        skipWhitespace();
        if (isDone() || source.charAt(index) != expected) {
            throw error("Expected '" + expected + "'.");
        }
        index++;
    }

    private boolean peek(char expected) {
        return !isDone() && source.charAt(index) == expected;
    }

    private boolean isDone() {
        return index >= source.length();
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " At character " + index + ".");
    }
}
