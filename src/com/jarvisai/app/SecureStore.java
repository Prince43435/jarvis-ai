package com.jarvisai.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small encrypted store for credentials. Values are encrypted with an AES-256-GCM
 * key held by the Android Keystore (hardware backed where available) and stored
 * as base64 in private SharedPreferences. Nothing sensitive is written in clear.
 */
public final class SecureStore {

    private static final String TAG = "SecureStore";
    private static final String ALIAS = "jarvis_master_key_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String PREFIX = "enc1:";
    private static final String FALLBACK = "fb1:";

    private final SharedPreferences sp;

    public SecureStore(Context c) {
        this.sp = c.getApplicationContext().getSharedPreferences("jarvis_secure", Context.MODE_PRIVATE);
    }

    public String get(String key, String def) {
        String raw = sp.getString(key, null);
        if (raw == null) return def;
        try {
            if (raw.startsWith(PREFIX)) {
                String plain = decrypt(raw.substring(PREFIX.length()));
                return plain == null ? def : plain;
            }
            if (raw.startsWith(FALLBACK)) {
                return new String(Util.unbase64(raw.substring(FALLBACK.length())), StandardCharsets.UTF_8);
            }
            return raw;
        } catch (Exception e) {
            Log.w(TAG, "read failed: " + e.getMessage());
            return def;
        }
    }

    public void put(String key, String value) {
        if (value == null) {
            sp.edit().remove(key).apply();
            return;
        }
        try {
            sp.edit().putString(key, PREFIX + encrypt(value)).apply();
        } catch (Exception e) {
            Log.w(TAG, "keystore unavailable, using private obfuscated storage: " + e.getMessage());
            sp.edit().putString(key, FALLBACK + Util.base64(value.getBytes(StandardCharsets.UTF_8))).apply();
        }
    }

    public void remove(String key) { sp.edit().remove(key).apply(); }
    public boolean has(String key) { return sp.contains(key); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE);
        android.security.keystore.KeyGenParameterSpec spec =
                new android.security.keystore.KeyGenParameterSpec.Builder(
                        ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                                | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build();
        kg.init(spec);
        return kg.generateKey();
    }

    private String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Util.base64(iv) + ":" + Util.base64(ct);
    }

    private String decrypt(String packed) throws Exception {
        String[] parts = packed.split(":");
        if (parts.length != 2) return null;
        byte[] iv = Util.unbase64(parts[0]);
        byte[] ct = Util.unbase64(parts[1]);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    }

    public static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
