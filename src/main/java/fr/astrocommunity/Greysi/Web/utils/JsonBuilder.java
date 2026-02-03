package fr.astrocommunity.Greysi.Web.utils;

import java.util.List;
import java.util.Map;

/**
 * JsonBuilder - Simple JSON serialization utility
 */
public class JsonBuilder {
    /**
     * Convert a Map to JSON string
     */
    public static String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(entry.getKey()).append("\":")
                .append(valueToJson(entry.getValue()));
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Convert a value to JSON representation
     */
    private static String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            return toJson((Map<String, Object>) value);
        }
        if (value instanceof List) {
            return listToJson((List<?>) value);
        }
        return "\"" + escapeString(value.toString()) + "\"";
    }

    /**
     * Convert a List to JSON array
     */
    private static String listToJson(List<?> list) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) json.append(",");
            first = false;
            json.append(valueToJson(item));
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Escape special characters in string
     */
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Extract a JSON value from response
     */
    public static String extractJsonValue(String json, String key) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start == -1) return null;
            start += search.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
