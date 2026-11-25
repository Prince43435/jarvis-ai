package com.jarvisai.app;

import com.jarvisai.app.ai.GeminiClient;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Thin, dependency-free HTTP layer for the Gemini REST API. */
public final class Net {

    public static final String BASE = "https://generativelanguage.googleapis.com/v1beta";

    private Net() {}

    public static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    public static class ApiException extends IOException {
        public final int code;
        public final String body;

        public ApiException(int code, String body) {
            super(friendly(code, body));
            this.code = code;
            this.body = body;
        }

        public boolean retryable() { return code == 429 || code == 500 || code == 503 || code == 504; }

        private static String friendly(int code, String body) {
            String detail = "";
            try {
                JSONObject o = new JSONObject(body);
                JSONObject err = o.optJSONObject("error");
                if (err != null) {
                    detail = err.optString("message", "");
                    if (detail.length() > 220) detail = detail.substring(0, 220) + "…";
                }
            } catch (Exception ignored) {}
            switch (code) {
                case 400: return "Request rejected (400). " + detail;
                case 401:
                case 403: return "API key galat ya blocked lagti hai (HTTP " + code + "). Settings mein key check karein.";
                case 404: return "Model nahi mila (404). Settings mein model name check karein.";
                case 429: return "Rate limit / quota khatam (429). Thodi der baad try karein.";
                case 500:
                case 503: return "Gemini server busy hai (HTTP " + code + "). Retry karein.";
                default: return "Network error " + code + (detail.isEmpty() ? "" : ": " + detail);
            }
        }
    }

    private static HttpURLConnection open(String url, String apiKey, String method, int timeoutSec) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(timeoutSec * 1000);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) conn.setRequestProperty("x-goog-api-key", apiKey);
        conn.setRequestProperty("User-Agent", "JarvisAI/1.0 (Android)");
        return conn;
    }

    public static String get(String url, String apiKey, int timeoutSec) throws IOException {
        HttpURLConnection conn = open(url, apiKey, "GET", timeoutSec);
        try {
            int code = conn.getResponseCode();
            if (code >= 400) throw new ApiException(code, read(conn.getErrorStream()));
            return read(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    public static String post(String url, String apiKey, String body, int timeoutSec) throws IOException {
        HttpURLConnection conn = open(url, apiKey, "POST", timeoutSec);
        try {
            conn.setDoOutput(true);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            if (code >= 400) throw new ApiException(code, read(conn.getErrorStream()));
            return read(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    /** Streams an SSE response from :streamGenerateContent and returns the joined text. */
    public static String stream(String url, String apiKey, String body, int timeoutSec, StreamSink sink)
            throws IOException {
        HttpURLConnection conn = open(url, apiKey, "POST", timeoutSec);
        StringBuilder all = new StringBuilder();
        try {
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            if (code >= 400) throw new ApiException(code, read(conn.getErrorStream()));
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                try {
                    JSONObject chunk = new JSONObject(data);
                    String delta = GeminiClient.extractText(chunk);
                    if (delta != null && !delta.isEmpty()) {
                        all.append(delta);
                        if (sink != null) sink.onDelta(delta);
                    }
                    if (sink != null) {
                        sink.onChunk(chunk);
                        if (GeminiClient.hasFunctionCall(chunk)) sink.onFunctionCall();
                    }
                } catch (Exception ignored) {}
            }
            return all.toString();
        } finally {
            conn.disconnect();
        }
    }

    public interface StreamSink {
        void onDelta(String text);
        default void onChunk(JSONObject chunk) {}
        default void onFunctionCall() {}
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        return new String(Util.readAll(in), StandardCharsets.UTF_8);
    }
}
