package com.example.tests.generator.util;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON utilities to avoid external dependencies.
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    private static final Pattern CONTENT_PATTERN = Pattern.compile(
            "\\\"content\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*+)\\\"",
            Pattern.DOTALL);

    public static String findString(String json, String key) {
        Pattern pattern = Pattern.compile(
                String.format("\\\"%s\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\"])*)\\\"", Pattern.quote(key)),
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescape(matcher.group(1));
        }
        return null;
    }

    public static Long findLong(String json, String key) {
        Pattern pattern = Pattern.compile(String.format("\\\"%s\\\"\\s*:\\s*(\\d+)", Pattern.quote(key)));
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    public static String extractChoiceContent(String json) {
        Matcher matcher = CONTENT_PATTERN.matcher(json);
        if (matcher.find()) {
            return unescape(matcher.group(1));
        }
        return null;
    }

    public static String extractStreamContent(String json) {
        Matcher matcher = CONTENT_PATTERN.matcher(json);
        if (matcher.find()) {
            return unescape(matcher.group(1));
        }
        return null;
    }

    public static void appendJsonValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                builder.append('"').append(escape(String.valueOf(entry.getKey()))).append('"').append(':');
                appendJsonValue(builder, entry.getValue());
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                appendJsonValue(builder, iterator.next());
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append(']');
        } else {
            builder.append('"').append(escape(value.toString())).append('"');
        }
    }

    public static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String unescape(String value) {
        return value.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
