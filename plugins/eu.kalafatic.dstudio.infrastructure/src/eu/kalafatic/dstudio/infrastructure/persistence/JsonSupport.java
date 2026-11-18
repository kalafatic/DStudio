package eu.kalafatic.dstudio.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonSupport {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    public static String writeMap(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data == null ? Map.of() : data);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static Map<String, Object> readMap(String json) {
        try {
            JsonNode node = MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
            return MAPPER.convertValue(node,
                    MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class,
                            String.class, Object.class));
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static String writeStringMap(Map<String, String> data) {
        try {
            return MAPPER.writeValueAsString(data == null ? Map.of() : data);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static Map<String, String> readStringMap(String json) {
        try {
            JsonNode node = MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
            Map<String, String> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> result.put(e.getKey(),
                    e.getValue().asText()));
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static String writeBytes(byte[] bytes) {
        return new String(bytes == null ? new byte[0] : bytes, StandardCharsets.UTF_8);
    }

    public static String writeStringList(java.util.List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? java.util.List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    public static java.util.List<String> readStringList(String json) {
        try {
            JsonNode node = MAPPER.readTree(json == null || json.isBlank() ? "[]" : json);
            if (!node.isArray()) {
                return java.util.List.of();
            }
            java.util.List<String> result = new java.util.ArrayList<>();
            node.forEach(item -> result.add(item.asText()));
            return result;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }
}
