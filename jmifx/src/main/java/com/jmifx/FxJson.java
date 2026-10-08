package com.jmifx;

/**
 * Minimal JSON support for the wasm client: build a single-member object and
 * read a top-level string member back. Zero reflection, zero dependencies —
 * TeaVM-safe. Not a general JSON API (no parsing of nested structures).
 */
public final class FxJson {

    private FxJson() {
    }

    /** {@code obj("name", "Berlin")} → {@code {"name":"Berlin"}}; null value → {@code {"name":null}}. */
    public static String obj(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return "{\"" + escape(key) + "\":" + (value == null ? "null" : "\"" + escape(value) + "\"") + "}";
    }

    /**
     * Unescaped value of a top-level string member, or {@code null} if the key
     * is absent, the input is not a flat JSON object, or the value is not a string.
     */
    public static String stringValue(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        int i = 0;
        int n = json.length();
        i = skipWhitespace(json, i);
        if (i >= n || json.charAt(i) != '{') {
            return null;
        }
        i++;
        while (true) {
            i = skipWhitespace(json, i);
            if (i >= n || json.charAt(i) == '}') {
                return null; // end of object — key not found
            }
            if (json.charAt(i) == ',') {
                i++;
                continue;
            }
            if (json.charAt(i) != '"') {
                return null; // malformed member
            }
            String currentKey = readString(json, i);
            if (currentKey == null) {
                return null;
            }
            i = skipWhitespace(json, afterString(json, i));
            if (i >= n || json.charAt(i) != ':') {
                return null;
            }
            i = skipWhitespace(json, i + 1);
            if (i < n && json.charAt(i) == '"') {
                String value = readString(json, i);
                if (key.equals(currentKey)) {
                    return value;
                }
                i = afterString(json, i);
            } else {
                i = skipNonStringValue(json, i); // number / true / false / null / nested — skipped
                if (i < 0) {
                    return null;
                }
            }
        }
    }

    // --- writing ---------------------------------------------------------------

    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    // --- reading ---------------------------------------------------------------

    private static int skipWhitespace(String json, int i) {
        while (i < json.length() && json.charAt(i) <= ' ') {
            i++;
        }
        return i;
    }

    /** Reads the string literal starting at {@code i} (must be a quote); null if malformed. */
    private static String readString(String json, int i) {
        if (json.charAt(i) != '"') {
            return null;
        }
        StringBuilder out = new StringBuilder();
        i++;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                if (i + 1 >= json.length()) {
                    return null;
                }
                char e = json.charAt(i + 1);
                switch (e) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (i + 5 >= json.length()) {
                            return null;
                        }
                        out.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                        i += 4;
                    }
                    default -> {
                        return null;
                    }
                }
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return null; // unterminated
    }

    /** Index just past the string literal starting at {@code i}; assumes well-formed literal. */
    private static int afterString(String json, int i) {
        i++;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == '"') {
                return i + 1;
            } else {
                i++;
            }
        }
        return i;
    }

    /** Skips a non-string member value; returns the next index or -1 if malformed. */
    private static int skipNonStringValue(String json, int i) {
        int depth = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') { // nested strings inside objects/arrays
                i = afterString(json, i);
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                if (depth == 0) {
                    return i; // end of the top-level object — let the caller see it
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                return i;
            }
            i++;
        }
        return -1;
    }
}
