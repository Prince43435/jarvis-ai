package com.jarvisai.app.ai;

import com.jarvisai.app.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Gemini REST calls (generateContent + SSE streaming) and response parsing. */
public final class GeminiClient {

    private GeminiClient() {}

    public static String cleanModel(String model) {
        if (model == null) return "gemini-2.5-flash";
        String m = model.trim();
        if (m.startsWith("models/")) m = m.substring(7);
        return m;
    }

    public static JSONObject generate(String key, String model, JSONObject body) throws Exception {
        String url = Net.BASE + "/models/" + cleanModel(model) + ":generateContent";
        String raw = Net.post(url, key, body.toString(), 120);
        return new JSONObject(raw);
    }

    public static class StreamResult {
        public final StringBuilder text = new StringBuilder();
        public final List<JSONObject> functionCalls = new ArrayList<>();
        public JSONObject lastChunk;
        public String blockReason;
    }

    public static StreamResult stream(String key, String model, JSONObject body,
                                      Net.StreamSink sink) throws Exception {
        String url = Net.BASE + "/models/" + cleanModel(model) + ":streamGenerateContent?alt=sse";
        final StreamResult result = new StreamResult();
        final java.util.Set<String> seenCalls = new java.util.HashSet<>();
        Net.stream(url, key, body.toString(), 120, new Net.StreamSink() {
            @Override
            public void onDelta(String text) {
                result.text.append(text);
                if (sink != null) sink.onDelta(text);
            }

            @Override
            public void onChunk(JSONObject chunk) {
                result.lastChunk = chunk;
                for (JSONObject fc : functionCalls(chunk)) {
                    String key2 = fc.optString("name") + "|" + fc.optJSONObject("args");
                    if (seenCalls.add(key2)) result.functionCalls.add(fc);
                }
                String blocked = blockReason(chunk);
                if (blocked != null) result.blockReason = blocked;
                if (sink != null) sink.onChunk(chunk);
            }
        });
        return result;
    }

    // ---- Parsing --------------------------------------------------------

    public static String extractText(JSONObject response) {
        if (response == null) return "";
        try {
            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return "";
            JSONObject cand = candidates.getJSONObject(0);
            JSONObject content = cand.optJSONObject("content");
            if (content == null) return "";
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;
                if (part.optBoolean("thought", false)) continue;
                String t = part.optString("text", "");
                if (!t.isEmpty()) sb.append(t);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean hasFunctionCall(JSONObject response) {
        return !functionCalls(response).isEmpty();
    }

    public static List<JSONObject> functionCalls(JSONObject response) {
        List<JSONObject> out = new ArrayList<>();
        if (response == null) return out;
        try {
            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return out;
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) return out;
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null) return out;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;
                JSONObject fc = part.optJSONObject("functionCall");
                if (fc != null) out.add(fc);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static JSONObject modelContent(JSONObject response) {
        try {
            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return null;
            return candidates.getJSONObject(0).optJSONObject("content");
        } catch (Exception e) {
            return null;
        }
    }

    public static String blockReason(JSONObject response) {
        if (response == null) return null;
        try {
            JSONObject feedback = response.optJSONObject("promptFeedback");
            if (feedback != null) {
                String reason = feedback.optString("blockReason", "");
                if (!reason.isEmpty()) return "Request blocked: " + reason;
            }
            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                String finish = candidates.getJSONObject(0).optString("finishReason", "");
                if ("SAFETY".equals(finish)) return "Response blocked by safety filters.";
                if ("MAX_TOKENS".equals(finish)) return "Response hit the token limit.";
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static List<String> listModels(String key) throws Exception {
        String raw = Net.get(Net.BASE + "/models?pageSize=100", key, 30);
        List<String> names = new ArrayList<>();
        JSONArray models = new JSONObject(raw).optJSONArray("models");
        if (models != null) {
            for (int i = 0; i < models.length(); i++) {
                JSONObject m = models.optJSONObject(i);
                if (m == null) continue;
                JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                if (methods == null) continue;
                boolean gen = false;
                for (int j = 0; j < methods.length(); j++) {
                    if ("generateContent".equals(methods.optString(j))) gen = true;
                }
                if (!gen) continue;
                String name = m.optString("name", "").replace("models/", "");
                if (!name.isEmpty()) names.add(name);
            }
        }
        return names;
    }

    /** Sends a tiny request; returns null when the key works. */
    public static String validate(String key, String model) {
        try {
            JSONObject body = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject turn = new JSONObject();
            turn.put("role", "user");
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", "Reply with exactly: OK"));
            turn.put("parts", parts);
            contents.put(turn);
            body.put("contents", contents);
            JSONObject cfg = new JSONObject();
            cfg.put("maxOutputTokens", 16);
            cfg.put("temperature", 0);
            body.put("generationConfig", cfg);
            JSONObject resp = generate(key, model, body);
            String text = extractText(resp);
            if (text == null || text.trim().isEmpty()) {
                String blocked = blockReason(resp);
                return blocked == null ? "Model ne khaali jawab diya." : blocked;
            }
            return null;
        } catch (Net.ApiException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "Connection fail: " + e.getClass().getSimpleName();
        }
    }

    public static JSONObject inlineImage(byte[] jpeg) throws Exception {
        JSONObject d = new JSONObject();
        d.put("mimeType", "image/jpeg");
        d.put("data", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP));
        return new JSONObject().put("inlineData", d);
    }
}
