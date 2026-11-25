package com.jarvisai.app.ai;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Minimal RFC-6455 WebSocket client (TLS only) built on the platform socket API.
 * Needed because the app ships without third-party libraries; the Gemini Live API
 * requires a raw WebSocket connection.
 */
public class WebSocketClient {

    private static final String TAG = "WebSocketClient";
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public interface Listener {
        void onOpen();
        void onMessage(String text);
        void onClose(int code, String reason);
        void onError(Throwable t);
    }

    private final String url;
    private final Listener listener;
    private SSLSocket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean open;
    private volatile boolean closing;
    private Thread reader, pinger;

    public WebSocketClient(String url, Listener listener) {
        this.url = url;
        this.listener = listener;
    }

    public boolean isOpen() { return open; }

    public void connect() {
        Thread t = new Thread(this::doConnect, "ws-connect");
        t.setDaemon(true);
        t.start();
    }

    private void doConnect() {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 443;
            String path = uri.getRawPath();
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket s = (SSLSocket) factory.createSocket();
            s.connect(new InetSocketAddress(host, port), 20000);
            s.setSoTimeout(0);
            s.startHandshake();
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, s.getSession())) {
                throw new IOException("TLS hostname verification failed");
            }
            socket = s;
            in = s.getInputStream();
            out = s.getOutputStream();

            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            String key = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP);

            String req = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: " + key + "\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "Origin: https://" + host + "\r\n" +
                    "User-Agent: JarvisAI/1.0 (Android)\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String status = readLine();
            if (status == null || !status.contains("101")) {
                String body = "";
                try { body = readLine(); } catch (Exception ignored) {}
                throw new IOException("WebSocket upgrade failed: " + status + " " + body);
            }
            String accept = null;
            String line;
            int guard = 0;
            while ((line = readLine()) != null && !line.isEmpty() && guard++ < 100) {
                int c = line.indexOf(':');
                if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("Sec-WebSocket-Accept")) {
                    accept = line.substring(c + 1).trim();
                }
            }
            String expected = base64(sha1((key + GUID).getBytes(StandardCharsets.UTF_8)));
            if (accept == null || !accept.equals(expected)) {
                throw new IOException("Bad WebSocket accept header");
            }

            open = true;
            listener.onOpen();
            startPinger();
            readLoop();
        } catch (Throwable t) {
            if (!closing) {
                Log.w(TAG, "connect failed: " + t);
                listener.onError(t);
            }
            closeQuietly();
            if (!closing) listener.onClose(1006, t.getMessage() == null ? "connection error" : t.getMessage());
        }
    }

    private void startPinger() {
        pinger = new Thread(() -> {
            while (open && !closing) {
                try {
                    Thread.sleep(25000);
                    if (!open) break;
                    sendFrame(0x9, new byte[0]);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    break;
                }
            }
        }, "ws-ping");
        pinger.setDaemon(true);
        pinger.start();
    }

    private void readLoop() throws IOException {
        ByteArrayOutputStream fragments = new ByteArrayOutputStream();
        int fragOpcode = -1;
        while (open && !closing) {
            int b0 = in.read();
            if (b0 < 0) break;
            int b1 = in.read();
            if (b1 < 0) break;
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = ((long) readByte() << 8) | readByte();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | readByte();
            }
            if (len > 32L * 1024 * 1024) throw new IOException("frame too large");
            byte[] mask = null;
            if (masked) {
                mask = readFully(4);
            }
            byte[] payload = readFully((int) len);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
            }

            switch (opcode) {
                case 0x0: // continuation
                    fragments.write(payload);
                    if (fin) {
                        deliver(fragOpcode, fragments.toByteArray());
                        fragments.reset();
                        fragOpcode = -1;
                    }
                    break;
                case 0x1:
                case 0x2:
                    if (fin) {
                        deliver(opcode, payload);
                    } else {
                        fragOpcode = opcode;
                        fragments.reset();
                        fragments.write(payload);
                    }
                    break;
                case 0x8: { // close
                    int code = 1000;
                    String reason = "";
                    if (payload.length >= 2) {
                        code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                        if (payload.length > 2) {
                            reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
                        }
                    }
                    closing = true;
                    open = false;
                    listener.onClose(code, reason);
                    return;
                }
                case 0x9: // ping
                    sendFrame(0xA, payload);
                    break;
                case 0xA: // pong
                    break;
                default:
                    break;
            }
        }
    }

    private void deliver(int opcode, byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8);
        if (opcode == 0x2) {
            // Gemini Live may use binary frames carrying JSON.
            text = text.trim();
            if (!text.startsWith("{")) return;
        }
        listener.onMessage(text);
    }

    public void send(String text) {
        if (!open) return;
        try {
            sendFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        if (out == null) throw new IOException("not connected");
        int len = payload.length;
        ByteArrayOutputStream frame = new ByteArrayOutputStream(len + 14);
        frame.write(0x80 | opcode);
        if (len < 126) {
            frame.write(0x80 | len);
        } else if (len <= 0xFFFF) {
            frame.write(0x80 | 126);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        } else {
            frame.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) frame.write((int) (((long) len >> (8 * i)) & 0xFF));
        }
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        frame.write(mask);
        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) masked[i] = (byte) (payload[i] ^ mask[i % 4]);
        frame.write(masked);
        out.write(frame.toByteArray());
        out.flush();
    }

    public void close() {
        closing = true;
        if (open) {
            try { sendFrame(0x8, new byte[]{(byte) 0x03, (byte) 0xE8}); } catch (Exception ignored) {}
        }
        open = false;
        closeQuietly();
    }

    private void closeQuietly() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b < 0) throw new IOException("eof");
        return b;
    }

    private byte[] readFully(int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new IOException("eof");
            off += r;
        }
        return b;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b < 0) return buf.size() == 0 ? null : buf.toString("UTF-8");
            if (b == '\n') {
                String s = buf.toString("UTF-8");
                return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
            }
            buf.write(b);
            prev = b;
        }
    }

    private static byte[] sha1(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-1").digest(data);
    }

    private static String base64(byte[] data) {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }
}
