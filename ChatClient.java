import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot CLI chat client for an OpenAI-compatible /chat/completions endpoint.
 *
 * Reads OPENROUTER_API_KEY, CHAT_BASE_URL, CHAT_MODEL from the environment.
 * Posts the question, prints the model's answer, then a final line of the
 * form: model=<name> in=<N> out=<N>, where <N> values come straight from
 * the API's usage record.
 */
public final class ChatClient {

    private static final String[] REQUIRED_ENV = {
        "OPENROUTER_API_KEY",
        "CHAT_BASE_URL",
        "CHAT_MODEL"
    };

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java ChatClient.java \"<question>\"");
            System.exit(2);
        }
        String question = args[0];

        Map<String, String> env = new LinkedHashMap<>();
        for (String name : REQUIRED_ENV) {
            String value = System.getenv(name);
            if (value == null || value.isEmpty()) {
                System.err.println("Error: environment variable " + name + " is required");
                System.exit(1);
            }
            env.put(name, value);
        }

        String model = env.get("CHAT_MODEL");
        String endpoint = stripTrailingSlash(env.get("CHAT_BASE_URL")) + "/chat/completions";
        String requestBody = buildRequestBody(model, question);

        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .header("Authorization", "Bearer " + env.get("OPENROUTER_API_KEY"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Error: request to " + endpoint + " was interrupted");
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("Error: failed to reach " + endpoint + ": " + e.getMessage());
            System.exit(1);
            return;
        }

        String body = response.body();
        if (response.statusCode() / 100 != 2) {
            String excerpt = body.length() > 500 ? body.substring(0, 500) + "..." : body;
            System.err.println("Error: HTTP " + response.statusCode() + " from " + endpoint + ": " + excerpt);
            System.exit(1);
        }

        Object parsed;
        try {
            parsed = parseJson(body);
        } catch (RuntimeException e) {
            System.err.println("Error: failed to parse response JSON: " + e.getMessage());
            System.exit(1);
            return;
        }

        String answer = getString(parsed, "choices[0].message.content");
        long inTokens = getLong(parsed, "usage.prompt_tokens");
        long outTokens = getLong(parsed, "usage.completion_tokens");

        if (answer != null && !answer.isEmpty()) {
            System.out.print(answer);
        }
        System.out.println();
        System.out.println("model=" + model + " in=" + inTokens + " out=" + outTokens);
    }

    private static String stripTrailingSlash(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') end--;
        return s.substring(0, end);
    }

    static String buildRequestBody(String model, String question) {
        return "{\"model\":\"" + jsonEscape(model) + "\","
             + "\"messages\":[{\"role\":\"system\",\"content\":\"Answer like a pirate\"},"
             + "{\"role\":\"user\",\"content\":\"" + jsonEscape(question) + "\"}],"
             + "\"max_tokens\":100}";
    }

    static String jsonEscape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b");  break;
                case '\f': out.append("\\f");  break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    // ---------- JSON response parsing ----------

    static Object parseJson(String src) {
        JsonParser p = new JsonParser(src);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (!p.atEnd()) throw new RuntimeException("trailing content at offset " + p.pos);
        return value;
    }

    static String getString(Object node, String path) {
        Object v = navigate(node, path);
        return v == null ? null : v.toString();
    }

    static long getLong(Object node, String path) {
        Object v = navigate(node, path);
        if (v == null) throw new RuntimeException("missing value at " + path);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) return Long.parseLong((String) v);
        throw new RuntimeException("expected number at " + path + ", got " + v.getClass().getSimpleName());
    }

    /** Navigate a parsed JSON tree using dotted/bracketed path syntax (e.g. "choices[0].message.content"). */
    static Object navigate(Object root, String path) {
        Object cur = root;
        StringBuilder name = new StringBuilder();
        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '.') {
                if (name.length() > 0) { cur = descend(cur, name.toString(), -1); name.setLength(0); }
                i++;
            } else if (c == '[') {
                if (name.length() > 0) { cur = descend(cur, name.toString(), -1); name.setLength(0); }
                int end = path.indexOf(']', i);
                if (end < 0) throw new RuntimeException("unclosed '[' in path " + path);
                String idxStr = path.substring(i + 1, end).trim();
                if (idxStr.isEmpty()) throw new RuntimeException("empty index in path " + path);
                int idx = Integer.parseInt(idxStr);
                cur = descend(cur, null, idx);
                i = end + 1;
            } else {
                name.append(c);
                i++;
            }
        }
        if (name.length() > 0) cur = descend(cur, name.toString(), -1);
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static Object descend(Object node, String key, int index) {
        if (node == null) throw new RuntimeException("cannot descend into null at " + (key != null ? key : "[" + index + "]"));
        if (key != null) {
            if (!(node instanceof Map)) throw new RuntimeException("expected object at ." + key);
            return ((Map<String, Object>) node).get(key);
        }
        if (!(node instanceof List)) throw new RuntimeException("expected array at [" + index + "]");
        List<Object> list = (List<Object>) node;
        if (index < 0 || index >= list.size()) throw new RuntimeException("array index " + index + " out of range");
        return list.get(index);
    }

    // ---------- Recursive-descent JSON parser ----------

    private static final class JsonParser {
        final String src;
        int pos;
        JsonParser(String src) { this.src = src; }

        boolean atEnd() { return pos >= src.length(); }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        private RuntimeException err(String msg) { return new RuntimeException(msg + " at offset " + pos); }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) throw err("unexpected end of input");
            char c = src.charAt(pos);
            switch (c) {
                case '{': return readObject();
                case '[': return readArray();
                case '"': return readString();
                case 't':
                case 'f': return readBoolean();
                case 'n': return readNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
                    throw err("unexpected character '" + c + "'");
            }
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw err("expected string key");
                String key = readString();
                skipWhitespace();
                if (peek() != ':') throw err("expected ':' after key");
                pos++;
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return map; }
                throw err("expected ',' or '}'");
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                Object value = readValue();
                list.add(value);
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
                throw err("expected ',' or ']'");
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (atEnd()) throw err("unterminated escape in string");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw err("truncated \\u escape");
                            int code = Integer.parseInt(src.substring(pos, pos + 4), 16);
                            pos += 4;
                            sb.append((char) code);
                            break;
                        default: throw err("unknown escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw err("unterminated string");
        }

        Number readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            String num = src.substring(start, pos);
            if (isFloat) return Double.parseDouble(num);
            return Long.parseLong(num);
        }

        Boolean readBoolean() {
            if (src.startsWith("true", pos))  { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw err("expected boolean literal");
        }

        Object readNull() {
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw err("expected null literal");
        }

        void expect(char c) {
            if (atEnd() || src.charAt(pos) != c) throw err("expected '" + c + "'");
            pos++;
        }

        char peek() {
            if (atEnd()) throw err("unexpected end of input");
            return src.charAt(pos);
        }
    }
}
