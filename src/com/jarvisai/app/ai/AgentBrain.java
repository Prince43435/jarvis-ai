package com.jarvisai.app.ai;

import android.content.Context;

import com.jarvisai.app.JarvisApp;
import com.jarvisai.app.Net;
import com.jarvisai.app.Prefs;
import com.jarvisai.app.automation.DeviceActions;
import com.jarvisai.app.memory.MemoryDB;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The agent loop: conversation context in, tool calls out, verified results back,
 * until the model has a final answer. All work happens off the UI thread.
 */
public class AgentBrain {

    public interface Listener {
        void onStatus(String status);
        void onDelta(String text);
        void onTool(String name, String message, boolean ok);
        void onFinal(String text);
        void onError(String message);
    }

    private static final int MAX_STEPS = 6;
    private static final int MAX_TURNS = 24;

    private final Context ctx;
    private final Prefs prefs;
    private final JSONArray contents = new JSONArray();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private int conv;

    public AgentBrain(Context c, int conversationId) {
        this.ctx = c.getApplicationContext();
        this.prefs = Prefs.get(c);
        this.conv = conversationId;
        loadHistory();
    }

    public void setConversation(int id) {
        this.conv = id;
        while (contents.length() > 0) contents.remove(0);;
        loadHistory();
    }

    private void loadHistory() {
        try {
            List<MemoryDB.Message> history = JarvisApp.get().db().messages(conv, 16);
            for (MemoryDB.Message m : history) {
                String role = "user".equals(m.role) ? "user" : "model";
                if (m.text == null || m.text.trim().isEmpty()) continue;
                JSONObject turn = new JSONObject();
                turn.put("role", role);
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("text", m.text));
                turn.put("parts", parts);
                contents.put(turn);
            }
            trim();
        } catch (Exception ignored) {}
    }

    public void cancel() { cancelled = true; }

    public void shutdown() {
        cancelled = true;
        exec.shutdownNow();
    }

    public void send(String userText, byte[] jpeg, boolean voice, Listener l) {
        cancelled = false;
        final String text = userText == null ? "" : userText.trim();
        final byte[] image = jpeg;
        exec.execute(() -> run(text, image, voice, l));
    }

    private void run(String userText, byte[] jpeg, boolean voice, Listener l) {
        try {
            if (!prefs.hasApiKey()) {
                l.onError("Gemini API key set nahi hai. Settings → API key mein add karein.");
                return;
            }
            if (!Net.online(ctx)) {
                l.onError("Internet nahi hai. Connection check karke dobara bolein.");
                return;
            }
            prefs.countApiCall();
            if (userText.isEmpty() && jpeg == null) {
                l.onError("Message khaali hai.");
                return;
            }
            if (!userText.isEmpty()) JarvisApp.get().memory().captureAuto(userText);
            saveMessage("user", userText.isEmpty() ? "[image]" : userText);

            JSONArray userParts = new JSONArray();
            if (jpeg != null) {
                userParts.put(GeminiClient.inlineImage(jpeg));
                if (userText.isEmpty()) {
                    userText = "Ye image dekho aur batao isme kya hai.";
                    userParts.put(new JSONObject().put("text", userText));
                }
            } else {
                userParts.put(new JSONObject().put("text", userText));
            }
            addTurn("user", userParts);

            l.onStatus("Soch rahi hoon…");
            String finalText = null;
            String lastError = null;

            for (int step = 0; step < MAX_STEPS && !cancelled; step++) {
                JSONObject body = buildBody(voice);
                boolean stream = prefs.streaming() && !voice;
                List<JSONObject> calls;
                String answer;
                String blocked = null;

                if (stream) {
                    GeminiClient.StreamResult sr;
                    Net.StreamSink sink = new Net.StreamSink() {
                        @Override
                        public void onDelta(String t) {
                            if (!cancelled) l.onDelta(t);
                        }
                    };
                    try {
                        sr = GeminiClient.stream(prefs.apiKey(), prefs.textModel(), body, sink);
                    } catch (Net.ApiException e) {
                        if (e.code == 400 && body.toString().contains("thinkingConfig")) {
                            body = stripThinking(body);
                            sr = GeminiClient.stream(prefs.apiKey(), prefs.textModel(), body, sink);
                        } else {
                            throw e;
                        }
                    }
                    calls = sr.functionCalls;
                    answer = sr.text.toString();
                    blocked = sr.blockReason;
                } else {
                    JSONObject resp;
                    try {
                        resp = GeminiClient.generate(prefs.apiKey(), prefs.textModel(), body);
                    } catch (Net.ApiException e) {
                        if (e.code == 400 && body.toString().contains("thinkingConfig")) {
                            body = stripThinking(body);
                            resp = GeminiClient.generate(prefs.apiKey(), prefs.textModel(), body);
                        } else {
                            throw e;
                        }
                    }
                    calls = GeminiClient.functionCalls(resp);
                    answer = GeminiClient.extractText(resp);
                    blocked = GeminiClient.blockReason(resp);
                }

                if (cancelled) return;

                if (!calls.isEmpty()) {
                    JSONObject content = new JSONObject();
                    content.put("role", "model");
                    JSONArray parts = new JSONArray();
                    if (answer != null && !answer.trim().isEmpty()) {
                        parts.put(new JSONObject().put("text", answer));
                    }
                    for (JSONObject fc : calls) {
                        parts.put(new JSONObject().put("functionCall", fc));
                    }
                    content.put("parts", parts);
                    contents.put(content);

                    JSONArray responses = new JSONArray();
                    for (JSONObject fc : calls) {
                        String name = fc.optString("name", "");
                        JSONObject args = fc.optJSONObject("args");
                        l.onStatus("Chal rahi hoon: " + name);
                        DeviceActions.Result result = Tools.execute(ctx, name, args == null ? new JSONObject() : args);
                        if (result.ok) {
                            l.onTool(name, result.message, true);
                        } else {
                            lastError = result.message;
                            l.onTool(name, result.message, false);
                        }
                        JSONObject fr = new JSONObject();
                        if (fc.has("id")) fr.put("id", fc.optString("id"));
                        fr.put("name", name);
                        fr.put("response", result.toJson());
                        responses.put(new JSONObject().put("functionResponse", fr));
                    }
                    addTurn("user", responses);
                    l.onStatus("Verify kar rahi hoon…");
                    continue;
                }

                if (answer == null || answer.trim().isEmpty()) {
                    if (blocked != null) {
                        finalText = blocked;
                    } else if (lastError != null) {
                        finalText = "Main action poori nahi kar payi: " + lastError;
                    } else {
                        finalText = "Mujhe iska jawab nahi mila. Ek baar phir se poochhein?";
                    }
                    break;
                }
                finalText = answer.trim();
                break;
            }

            if (cancelled) return;
            if (finalText == null) {
                finalText = "Main is task ko poori tarah nahi kar payi. Chaliye chhote steps mein try karein.";
            }
            addTurn("model", new JSONArray().put(new JSONObject().put("text", finalText)));
            saveMessage("assistant", finalText);
            l.onStatus("");
            l.onFinal(finalText);
        } catch (Net.ApiException e) {
            if (!cancelled) l.onError(e.getMessage());
        } catch (Exception e) {
            if (!cancelled) l.onError("Kuch galat ho gaya: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " — " + e.getMessage()));
        }
    }

    public void saveMessage(String role, String text) {
        try {
            JarvisApp.get().db().addMessage(conv, role, text, "");
        } catch (Exception ignored) {}
    }

    private void addTurn(String role, JSONArray parts) {
        try {
            JSONObject turn = new JSONObject();
            turn.put("role", role);
            turn.put("parts", parts);
            contents.put(turn);
            trim();
        } catch (Exception ignored) {}
    }

    private void trim() {
        while (contents.length() > MAX_TURNS) contents.remove(0);
        // Keep the first turn from being a model turn (Gemini rejects that).
        while (contents.length() > 0) {
            JSONObject first = contents.optJSONObject(0);
            if (first != null && "model".equals(first.optString("role"))) contents.remove(0);
            else break;
        }
    }

    private JSONObject buildBody(boolean voice) throws Exception {
        JSONObject body = new JSONObject();
        JSONObject sys = new JSONObject();
        sys.put("parts", new JSONArray().put(new JSONObject().put("text", Prompts.system(ctx, voice))));
        body.put("systemInstruction", sys);
        body.put("contents", contents);
        body.put("tools", new JSONArray().put(new JSONObject().put("functionDeclarations", Tools.declarations())));

        JSONObject gen = new JSONObject();
        gen.put("temperature", prefs.temperature());
        gen.put("topP", 0.95);
        gen.put("maxOutputTokens", 4096);
        String model = prefs.textModel();
        if (model.contains("2.5")) {
            JSONObject think = new JSONObject();
            think.put("thinkingBudget", voice ? 0 : 700);
            gen.put("thinkingConfig", think);
        }
        body.put("generationConfig", gen);

        JSONObject toolConfig = new JSONObject();
        JSONObject fc = new JSONObject();
        fc.put("mode", "AUTO");
        toolConfig.put("functionCallingConfig", fc);
        body.put("toolConfig", toolConfig);
        return body;
    }

    private JSONObject stripThinking(JSONObject body) {
        try {
            JSONObject copy = new JSONObject(body.toString());
            JSONObject gen = copy.optJSONObject("generationConfig");
            if (gen != null) gen.remove("thinkingConfig");
            return copy;
        } catch (Exception e) {
            return body;
        }
    }
}
