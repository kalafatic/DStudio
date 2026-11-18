package eu.kalafatic.dstudio.ui.contextual;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.kalafatic.dstudio.core.agent.AgentProfile;

/**
 * Minimal JSON round-trip for agent profiles (no JSON library in the UI bundle).
 * Only the portable fields are serialized: name, description, role, instructions,
 * tool permissions, restrictions and preferred environment. The id is preserved on
 * export and regenerated on import collision.
 */
public final class AgentProfileJson {

    private AgentProfileJson() {
    }

    public static String toJson(AgentProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        field(sb, "id", profile.getId());
        field(sb, "name", profile.getName());
        field(sb, "description", profile.getDescription());
        field(sb, "role", profile.getRole());
        field(sb, "systemInstructions", profile.getSystemInstructions());
        listField(sb, "toolPermissions", profile.getToolPermissions());
        listField(sb, "restrictions", profile.getRestrictions());
        field(sb, "preferredHarnessId", profile.getPreferredHarnessId());
        field(sb, "preferredProviderId", profile.getPreferredProviderId());
        field(sb, "preferredModel", profile.getPreferredModel());
        mapField(sb, "executionParameters", profile.getExecutionParameters());
        // strip trailing comma of the last real field
        int lastComma = sb.lastIndexOf(",");
        if (lastComma > 0) {
            sb.deleteCharAt(lastComma);
        }
        sb.append("}\n");
        return sb.toString();
    }

    public static AgentProfile fromJson(String json) {
        Map<String, Object> values = parseObject(json);
        String id = string(values, "id");
        if (id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        return new AgentProfile(id,
                string(values, "name"),
                string(values, "description"),
                string(values, "role"),
                string(values, "systemInstructions"),
                stringList(values.get("toolPermissions")),
                stringList(values.get("restrictions")),
                string(values, "preferredHarnessId"),
                string(values, "preferredProviderId"),
                string(values, "preferredModel"),
                stringMap(values.get("executionParameters")),
                Instant.now(), Instant.now());
    }

    private static void field(StringBuilder sb, String key, String value) {
        sb.append("  \"").append(key).append("\": \"")
                .append(escape(value == null ? "" : value)).append("\",\n");
    }

    private static void listField(StringBuilder sb, String key, List<String> values) {
        sb.append("  \"").append(key).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(escape(values.get(i))).append("\"");
        }
        sb.append("],\n");
    }

    private static void mapField(StringBuilder sb, String key, Map<String, String> values) {
        sb.append("  \"").append(key).append("\": {");
        int i = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(escape(entry.getKey())).append("\": \"")
                    .append(escape(entry.getValue())).append("\"");
        }
        sb.append("}\n");
    }

    /**
     * Deliberately small JSON parser for the flat agent-profile shape only
     * (string values, string lists, string maps). Not a general JSON parser.
     */
    private static Map<String, Object> parseObject(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Not a JSON object");
        }
        String body = json.substring(start + 1, end);
        int i = 0;
        while (i < body.length()) {
            while (i < body.length() && (Character.isWhitespace(body.charAt(i))
                    || body.charAt(i) == ',')) {
                i++;
            }
            if (i >= body.length()) {
                break;
            }
            int keyStart = body.indexOf('"', i);
            int keyEnd = body.indexOf('"', keyStart + 1);
            String key = body.substring(keyStart + 1, keyEnd);
            i = body.indexOf(':', keyEnd) + 1;
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i < body.length() && body.charAt(i) == '"') {
                int valueEnd = body.indexOf('"', i + 1);
                result.put(key, unescape(body.substring(i + 1, valueEnd)));
                i = valueEnd + 1;
            } else if (i < body.length() && body.charAt(i) == '[') {
                int listEnd = findClosing(body, i, '[', ']');
                String listBody = body.substring(i + 1, listEnd);
                List<String> items = new ArrayList<>();
                int p = 0;
                while (p < listBody.length()) {
                    int itemStart = listBody.indexOf('"', p);
                    if (itemStart < 0) {
                        break;
                    }
                    int itemEnd = listBody.indexOf('"', itemStart + 1);
                    items.add(unescape(listBody.substring(itemStart + 1, itemEnd)));
                    p = itemEnd + 1;
                }
                result.put(key, items);
                i = listEnd + 1;
            } else if (i < body.length() && body.charAt(i) == '{') {
                int mapEnd = findClosing(body, i, '{', '}');
                String mapBody = body.substring(i + 1, mapEnd);
                Map<String, String> map = new LinkedHashMap<>();
                int p = 0;
                while (p < mapBody.length()) {
                    int kStart = mapBody.indexOf('"', p);
                    if (kStart < 0) {
                        break;
                    }
                    int kEnd = mapBody.indexOf('"', kStart + 1);
                    int sep = mapBody.indexOf(':', kEnd);
                    int vStart = mapBody.indexOf('"', sep);
                    int vEnd = mapBody.indexOf('"', vStart + 1);
                    map.put(unescape(mapBody.substring(kStart + 1, kEnd)),
                            unescape(mapBody.substring(vStart + 1, vEnd)));
                    p = vEnd + 1;
                }
                result.put(key, map);
                i = mapEnd + 1;
            } else {
                throw new IllegalArgumentException("Unsupported value type for key " + key);
            }
        }
        return result;
    }

    private static int findClosing(String text, int open, char openChar, char closeChar) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (text.charAt(i) == openChar) {
                depth++;
            } else if (text.charAt(i) == closeChar) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced " + openChar + closeChar);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()),
                        String.valueOf(entry.getValue()));
            }
            return result;
        }
        return Map.of();
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
