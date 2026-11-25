package com.jarvisai.app.ai;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.jarvisai.app.JarvisApp;
import com.jarvisai.app.Net;
import com.jarvisai.app.Prefs;
import com.jarvisai.app.automation.DeviceActions;
import com.jarvisai.app.memory.MemoryDB;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gemini Live audio session: microphone PCM in, 24 kHz PCM out, server-side
 * turn detection, interruptions, tool calls and automatic reconnect.
 */
public class LiveSession {

    public static final int CONNECTING = 0, LISTENING = 1, SPEAKING = 2, THINKING = 3, CLOSED = 4;

    public interface Listener {
        void onState(int state);
        void onUserText(String text);
        void onAssistantText(String text);
        void onTurn(String userText, String assistantText);
        void onAudioLevel(float level);
        void onError(String message);
    }

    private static final String TAG = "LiveSession";
    private static final int SAMPLE_IN = 16000;
    private static final int SAMPLE_OUT = 24000;

    private final Context ctx;
    private final Prefs prefs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService toolExec = Executors.newSingleThreadExecutor();

    private WebSocketClient ws;
    private AudioRecord record;
    private AudioTrack track;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor ns;

    private volatile boolean micRunning;
    private volatile boolean playing;
    private volatile boolean userClosed;
    private volatile boolean setupComplete;
    private volatile boolean minimalSetup;
    private volatile int state = CLOSED;
    private int attempts;
    private Thread micThread, playThread;
    private final ArrayDeque<byte[]> audioQueue = new ArrayDeque<>();
    private final Object audioLock = new Object();
    private long lastAudioAt;

    private final StringBuilder userTurn = new StringBuilder();
    private final StringBuilder assistantTurn = new StringBuilder();
    private int conv;

    public LiveSession(Context c, int conversationId, Listener l) {
        this.ctx = c.getApplicationContext();
        this.prefs = Prefs.get(c);
        this.listener = l;
        this.conv = conversationId;
    }

    public boolean isRunning() {
        return ws != null && ws.isOpen() && !userClosed;
    }

    public int state() { return state; }

    public void start() {
        userClosed = false;
        attempts = 0;
        minimalSetup = false;
        restart();
    }

    private void restart() {
        if (!prefs.hasApiKey()) {
            error("Gemini API key set nahi hai. Settings mein add karein.");
            return;
        }
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            error("Microphone permission nahi hai.");
            return;
        }
        if (!Net.online(ctx)) {
            error("Internet nahi hai. Live conversation ke liye network chahiye.");
            return;
        }
        setupComplete = false;
        setState(CONNECTING);
        String model = prefs.liveModel().replace("models/", "");
        String url = "wss://generativelanguage.googleapis.com/ws/"
                + "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
                + android.net.Uri.encode(prefs.apiKey());
        Log.i(TAG, "connecting live session, model=" + model + (minimalSetup ? " (minimal setup)" : ""));
        ws = new WebSocketClient(url, new WebSocketClient.Listener() {
            @Override
            public void onOpen() {
                sendSetup(!minimalSetup);
            }

            @Override
            public void onMessage(String text) {
                handleMessage(text);
            }

            @Override
            public void onClose(int code, String reason) {
                onSocketClosed(code, reason);
            }

            @Override
            public void onError(Throwable t) {
                Log.w(TAG, "ws error " + t);
            }
        });
        ws.connect();
    }

    private void sendSetup(boolean withTranscriptions) {
        try {
            String model = prefs.liveModel().replace("models/", "");
            JSONObject setup = new JSONObject();
            setup.put("model", "models/" + model);

            JSONObject gen = new JSONObject();
            gen.put("responseModalities", new JSONArray().put("AUDIO"));
            JSONObject speech = new JSONObject();
            JSONObject voiceConfig = new JSONObject();
            voiceConfig.put("prebuiltVoiceConfig",
                    new JSONObject().put("voiceName", prefs.liveVoiceName()));
            speech.put("voiceConfig", voiceConfig);
            gen.put("speechConfig", speech);
            gen.put("temperature", prefs.temperature());
            setup.put("generationConfig", gen);

            setup.put("systemInstruction", new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", Prompts.system(ctx, true)))));
            setup.put("tools", new JSONArray().put(
                    new JSONObject().put("functionDeclarations", Tools.declarations())));

            if (withTranscriptions) {
                setup.put("inputAudioTranscription", new JSONObject());
                setup.put("outputAudioTranscription", new JSONObject());
            }

            JSONObject realtime = new JSONObject();
            JSONObject vad = new JSONObject();
            vad.put("disabled", false);
            vad.put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH");
            vad.put("endOfSpeechSensitivity", "END_SENSITIVITY_HIGH");
            vad.put("prefixPaddingMs", 20);
            vad.put("silenceDurationMs", 600);
            realtime.put("automaticActivityDetection", vad);
            setup.put("realtimeInputConfig", realtime);

            ws.send(new JSONObject().put("setup", setup).toString());
        } catch (Exception e) {
            error("Live setup fail: " + e.getMessage());
        }
    }

    private void handleMessage(String text) {
        try {
            JSONObject o = new JSONObject(text);

            if (o.has("setupComplete")) {
                setupComplete = true;
                attempts = 0;
                initTrack();
                startMic();
                setState(LISTENING);
                return;
            }

            if (o.has("error")) {
                JSONObject err = o.optJSONObject("error");
                String msg = err == null ? o.toString() : err.optString("message", o.toString());
                if (!setupComplete) {
                    if (!minimalSetup) {
                        minimalSetup = true;
                        Log.w(TAG, "setup rejected, retrying minimal setup: " + msg);
                        try { ws.close(); } catch (Exception ignored) {}
                        main.postDelayed(this::restart, 400);
                        return;
                    }
                }
                error("Live API: " + msg);
                return;
            }

            if (o.has("goAway")) {
                Log.i(TAG, "server goAway, reconnecting");
                reconnect();
                return;
            }

            JSONObject sc = o.optJSONObject("serverContent");
            if (sc != null) {
                if (sc.optBoolean("interrupted", false)) {
                    flushAudio();
                    setState(LISTENING);
                }
                JSONObject inputT = sc.optJSONObject("inputTranscription");
                if (inputT != null) {
                    String t = inputT.optString("text", "");
                    if (!t.isEmpty()) {
                        userTurn.append(t);
                        final String snapshot = userTurn.toString();
                        main.post(() -> listener.onUserText(snapshot));
                    }
                }
                JSONObject outputT = sc.optJSONObject("outputTranscription");
                if (outputT != null) {
                    String t = outputT.optString("text", "");
                    if (!t.isEmpty()) {
                        assistantTurn.append(t);
                        final String snapshot = assistantTurn.toString();
                        main.post(() -> listener.onAssistantText(snapshot));
                    }
                }
                JSONObject modelTurn = sc.optJSONObject("modelTurn");
                if (modelTurn != null) {
                    JSONArray parts = modelTurn.optJSONArray("parts");
                    if (parts != null) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.optJSONObject(i);
                            if (part == null) continue;
                            JSONObject inline = part.optJSONObject("inlineData");
                            if (inline == null) inline = part.optJSONObject("inline_data");
                            if (inline != null) {
                                String data = inline.optString("data", "");
                                if (!data.isEmpty()) enqueueAudio(data);
                            }
                        }
                    }
                }
                if (sc.optBoolean("turnComplete", false)) {
                    String u = userTurn.toString().trim();
                    String a = assistantTurn.toString().trim();
                    userTurn.setLength(0);
                    assistantTurn.setLength(0);
                    saveTurn(u, a);
                    main.post(() -> listener.onTurn(u, a));
                    if (!playing) setState(LISTENING);
                }
                if (sc.optBoolean("generationComplete", false) && !playing) {
                    setState(LISTENING);
                }
            }

            JSONObject toolCall = o.optJSONObject("toolCall");
            if (toolCall != null) {
                JSONArray calls = toolCall.optJSONArray("functionCalls");
                if (calls != null && calls.length() > 0) {
                    handleToolCalls(calls);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "bad message: " + e.getMessage());
        }
    }

    private void handleToolCalls(JSONArray calls) {
        setState(THINKING);
        final JSONArray copy = calls;
        toolExec.execute(() -> {
            JSONArray responses = new JSONArray();
            for (int i = 0; i < copy.length(); i++) {
                JSONObject fc = copy.optJSONObject(i);
                if (fc == null) continue;
                String name = fc.optString("name", "");
                JSONObject args = fc.optJSONObject("args");
                DeviceActions.Result r = Tools.execute(ctx, name, args == null ? new JSONObject() : args);
                try {
                    JSONObject fr = new JSONObject();
                    if (fc.has("id")) fr.put("id", fc.optString("id"));
                    fr.put("name", name);
                    fr.put("response", r.toJson());
                    responses.put(fr);
                } catch (Exception ignored) {}
            }
            try {
                JSONObject msg = new JSONObject();
                msg.put("toolResponse", new JSONObject().put("functionResponses", responses));
                if (ws != null) ws.send(msg.toString());
            } catch (Exception e) {
                Log.w(TAG, "toolResponse failed " + e.getMessage());
            }
            main.post(() -> { if (!playing) setState(LISTENING); });
        });
    }

    private void saveTurn(String user, String assistant) {
        try {
            MemoryDB db = JarvisApp.get().db();
            if (user != null && !user.isEmpty()) db.addMessage(conv, "user", user, "live");
            if (assistant != null && !assistant.isEmpty()) db.addMessage(conv, "assistant", assistant, "live");
        } catch (Exception ignored) {}
    }

    // ---- Audio in -------------------------------------------------------

    private void startMic() {
        if (micRunning) return;
        try {
            int min = AudioRecord.getMinBufferSize(SAMPLE_IN, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int buf = Math.max(min * 2, 6400);
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_IN, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                error("Microphone initialize nahi hua.");
                return;
            }
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(record.getAudioSessionId());
                    if (aec != null) aec.setEnabled(true);
                }
                if (NoiseSuppressor.isAvailable()) {
                    ns = NoiseSuppressor.create(record.getAudioSessionId());
                    if (ns != null) ns.setEnabled(true);
                }
            } catch (Exception ignored) {}
            record.startRecording();
            micRunning = true;
            micThread = new Thread(() -> {
                byte[] buffer = new byte[3200]; // 100 ms of 16 kHz mono PCM16
                while (micRunning) {
                    int n;
                    try {
                        n = record.read(buffer, 0, buffer.length);
                    } catch (Exception e) {
                        break;
                    }
                    if (n <= 0) continue;
                    float rms = rms(buffer, n);
                    final float level = Math.min(1f, rms * 9f);
                    main.post(() -> listener.onAudioLevel(level));
                    if (!setupComplete || ws == null) continue;
                    String b64 = Base64.encodeToString(buffer, 0, n, Base64.NO_WRAP);
                    try {
                        JSONObject audio = new JSONObject();
                        audio.put("mimeType", "audio/pcm;rate=16000");
                        audio.put("data", b64);
                        ws.send(new JSONObject().put("realtimeInput", new JSONObject().put("audio", audio)).toString());
                    } catch (Exception ignored) {}
                }
            }, "live-mic");
            micThread.setDaemon(true);
            micThread.start();
        } catch (Exception e) {
            error("Mic start fail: " + e.getMessage());
        }
    }

    private static float rms(byte[] pcm, int n) {
        double sum = 0;
        int samples = n / 2;
        for (int i = 0; i + 1 < n; i += 2) {
            short s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            sum += (double) s * s;
        }
        if (samples == 0) return 0;
        return (float) (Math.sqrt(sum / samples) / 32768.0);
    }

    // ---- Audio out ------------------------------------------------------

    private void initTrack() {
        if (track != null) return;
        try {
            int min = AudioTrack.getMinBufferSize(SAMPLE_OUT, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_OUT,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min * 3, 12000), AudioTrack.MODE_STREAM);
            track.play();
            playing = true;
            playThread = new Thread(() -> {
                byte[] chunk;
                while (playing) {
                    synchronized (audioLock) {
                        chunk = audioQueue.pollFirst();
                        if (chunk == null) {
                            try { audioLock.wait(120); } catch (InterruptedException e) { break; }
                        }
                    }
                    if (chunk != null && track != null) {
                        try {
                            track.write(chunk, 0, chunk.length);
                            lastAudioAt = System.currentTimeMillis();
                            final float lvl = Math.min(1f, rms(chunk, chunk.length) * 6f);
                            main.post(() -> listener.onAudioLevel(lvl));
                        } catch (Exception ignored) {}
                    } else if (playing && state == SPEAKING
                            && System.currentTimeMillis() - lastAudioAt > 700) {
                        setState(LISTENING);
                    }
                }
            }, "live-play");
            playThread.setDaemon(true);
            playThread.start();
        } catch (Exception e) {
            error("Speaker start fail: " + e.getMessage());
        }
    }

    private void enqueueAudio(String base64) {
        try {
            byte[] pcm = Base64.decode(base64, Base64.NO_WRAP);
            synchronized (audioLock) {
                if (audioQueue.size() > 80) audioQueue.clear();
                audioQueue.addLast(pcm);
                audioLock.notifyAll();
            }
            lastAudioAt = System.currentTimeMillis();
            if (state != SPEAKING) setState(SPEAKING);
        } catch (Exception ignored) {}
    }

    private void flushAudio() {
        synchronized (audioLock) {
            audioQueue.clear();
            audioLock.notifyAll();
        }
        if (track != null) {
            try {
                track.pause();
                track.flush();
                track.play();
            } catch (Exception ignored) {}
        }
    }

    // ---- Control --------------------------------------------------------

    public void sendText(String text) {
        if (ws == null || !ws.isOpen()) return;
        try {
            JSONObject turn = new JSONObject();
            turn.put("role", "user");
            turn.put("parts", new JSONArray().put(new JSONObject().put("text", text)));
            JSONObject content = new JSONObject();
            content.put("turns", new JSONArray().put(turn));
            content.put("turnComplete", true);
            ws.send(new JSONObject().put("clientContent", content).toString());
            setState(THINKING);
        } catch (Exception ignored) {}
    }

    public void stop() {
        userClosed = true;
        stopMic();
        playing = false;
        synchronized (audioLock) { audioQueue.clear(); audioLock.notifyAll(); }
        try { if (track != null) { track.pause(); track.flush(); track.release(); } } catch (Exception ignored) {}
        track = null;
        try { if (ws != null) ws.close(); } catch (Exception ignored) {}
        ws = null;
        setState(CLOSED);
    }

    private void stopMic() {
        micRunning = false;
        try { if (record != null) { record.stop(); record.release(); } } catch (Exception ignored) {}
        record = null;
        try { if (aec != null) aec.release(); } catch (Exception ignored) {}
        try { if (ns != null) ns.release(); } catch (Exception ignored) {}
        aec = null;
        ns = null;
    }

    private void reconnect() {
        if (userClosed) return;
        if (attempts >= 3) {
            error("Live connection baar-baar toot rahi hai. Classic voice mode try karein.");
            stop();
            return;
        }
        attempts++;
        stopMic();
        try { if (ws != null) ws.close(); } catch (Exception ignored) {}
        ws = null;
        setState(CONNECTING);
        long delay = 800L * attempts;
        main.postDelayed(this::restart, delay);
    }

    private void onSocketClosed(int code, String reason) {
        if (userClosed) return;
        Log.i(TAG, "socket closed " + code + " " + reason);
        if (code == 1000) {
            releaseAudio();
            setState(CLOSED);
            return;
        }
        if (!setupComplete) {
            if (!minimalSetup) {
                minimalSetup = true;
                main.postDelayed(this::restart, 400);
                return;
            }
        }
        reconnect();
    }

    private void releaseAudio() {
        stopMic();
        playing = false;
        synchronized (audioLock) { audioQueue.clear(); audioLock.notifyAll(); }
        try { if (track != null) { track.pause(); track.flush(); track.release(); } } catch (Exception ignored) {}
        track = null;
    }

    private void error(String msg) {
        main.post(() -> listener.onError(msg));
    }

    private void setState(int s) {
        if (state == s) return;
        state = s;
        main.post(() -> listener.onState(s));
    }

    public void shutdown() {
        stop();
        toolExec.shutdownNow();
    }
}
