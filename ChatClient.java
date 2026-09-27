import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

// Single-turn CLI chat client. Reads one question from argv, POSTs to
// {CHAT_BASE_URL}/chat/completions, prints the assistant's answer, then a
// final line of the form `model=<name> in=<N> out=<N>`. All configuration
// is env-var driven; nothing secret in this file.
public class ChatClient {

    private static final String KEY_ENV = "OPENROUTER_API_KEY";
    private static final String BASE_ENV = "CHAT_BASE_URL";
    private static final String MODEL_ENV = "CHAT_MODEL";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: ChatClient <question>");
            System.exit(2);
        }
        String question = args[0];

        String apiKey = requireEnv(KEY_ENV);
        String baseUrl = requireEnv(BASE_ENV);
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String model = requireEnv(MODEL_ENV);

        String requestBody = "{\"model\":\"" + jsonEscape(model)
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\""
                + jsonEscape(question) + "\"}]}";

        URI url = URI.create(baseUrl + "/chat/completions");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(url)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> resp;
        try {
            resp = client.send(req, BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("Network/IO error: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(5);
            return;
        }

        String respBody = resp.body() == null ? "" : resp.body();
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            String excerpt = respBody.length() > 500
                    ? respBody.substring(0, 500) + "..." : respBody;
            System.err.println("HTTP " + status + " from " + url);
            if (!excerpt.isEmpty()) {
                System.err.println(excerpt);
            }
            System.exit(4);
        }

        Parsed parsed;
        try {
            parsed = parseResponse(respBody);
        } catch (Exception e) {
            System.err.println("Failed to parse response: " + e.getMessage());
            System.exit(6);
            return;
        }

        System.out.println(parsed.content);
        System.out.println("model=" + parsed.model
                + " in=" + parsed.promptTokens
                + " out=" + parsed.completionTokens);
    }

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) {
            System.err.println("Missing required env var: " + name);
            System.exit(3);
        }
        return v;
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String jsonUnescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char next = s.charAt(++i);
            switch (next) {
                case '"':  sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case '/':  sb.append('/');  break;
                case 'n':  sb.append('\n'); break;
                case 'r':  sb.append('\r'); break;
                case 't':  sb.append('\t'); break;
                case 'b':  sb.append('\b'); break;
                case 'f':  sb.append('\f'); break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try {
                            int code = Integer.parseInt(s.substring(i + 1, i + 5), 16);
                            sb.append((char) code);
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append(next);
                        }
                    } else {
                        sb.append(next);
                    }
                    break;
                default:
                    sb.append(next);
            }
        }
        return sb.toString();
    }

    private static class Parsed {
        String model;
        String content;
        long promptTokens;
        long completionTokens;
    }

    private static Parsed parseResponse(String body) {
        Parsed p = new Parsed();
        p.model = extractString(body, "\"model\":\"");
        p.content = extractString(body, "\"content\":\"");
        p.promptTokens = extractLong(body, "\"prompt_tokens\":");
        p.completionTokens = extractLong(body, "\"completion_tokens\":");
        if (p.model == null || p.content == null) {
            throw new IllegalStateException("response missing required fields (model or content)");
        }
        return p;
    }

    // Find `marker` like `"content":"` and return the JSON string that follows
    // it, unescaped. Returns null if the marker is absent or the string is
    // unterminated. The scan stops at the first unescaped closing quote.
    private static String extractString(String body, String marker) {
        int idx = body.indexOf(marker);
        if (idx < 0) return null;
        int i = idx + marker.length();
        StringBuilder raw = new StringBuilder();
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') {
                return jsonUnescape(raw.toString());
            }
            if (c == '\\' && i + 1 < body.length()) {
                raw.append(c);
                raw.append(body.charAt(i + 1));
                i += 2;
            } else {
                raw.append(c);
                i++;
            }
        }
        return null; // unterminated
    }

    private static long extractLong(String body, String marker) {
        int idx = body.indexOf(marker);
        if (idx < 0) return 0;
        int i = idx + marker.length();
        int start = i;
        if (i < body.length() && body.charAt(i) == '-') i++;
        while (i < body.length() && Character.isDigit(body.charAt(i))) i++;
        if (i == start || (i == start + 1 && body.charAt(start) == '-')) return 0;
        try {
            return Long.parseLong(body.substring(start, i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
