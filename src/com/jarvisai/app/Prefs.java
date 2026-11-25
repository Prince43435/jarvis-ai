package com.jarvisai.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Typed access to every user preference. Credentials go through SecureStore. */
public final class Prefs {

    private static final String FILE = "jarvis_prefs";
    private static Prefs instance;

    private final SharedPreferences sp;
    private final SecureStore secure;

    private Prefs(Context c) {
        Context app = c.getApplicationContext();
        sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        secure = new SecureStore(app);
    }

    public static synchronized Prefs get(Context c) {
        if (instance == null) instance = new Prefs(c);
        return instance;
    }

    // ---- API keys -------------------------------------------------------
    public String apiKey() { return secure.get("gemini_key", ""); }
    public void setApiKey(String v) { secure.put("gemini_key", v == null ? "" : v.trim()); }
    public boolean hasApiKey() { return apiKey().length() > 14; }

    public String elevenKey() { return secure.get("eleven_key", ""); }
    public void setElevenKey(String v) { secure.put("eleven_key", v == null ? "" : v.trim()); }
    public boolean hasElevenKey() { return elevenKey().length() > 10; }

    // ---- Models ---------------------------------------------------------
    public String textModel() { return sp.getString("text_model", "gemini-2.5-flash"); }
    public void setTextModel(String v) { sp.edit().putString("text_model", v).apply(); }

    public String liveModel() {
        return sp.getString("live_model", "gemini-2.5-flash-native-audio-preview-09-2025");
    }
    public void setLiveModel(String v) { sp.edit().putString("live_model", v).apply(); }

    // ---- Voice ----------------------------------------------------------
    public boolean liveVoice() { return sp.getBoolean("live_voice", true); }
    public void setLiveVoice(boolean v) { sp.edit().putBoolean("live_voice", v).apply(); }

    public boolean autoListen() { return sp.getBoolean("auto_listen", false); }
    public void setAutoListen(boolean v) { sp.edit().putBoolean("auto_listen", v).apply(); }

    public String liveVoiceName() { return sp.getString("live_voice_name", "Aoede"); }
    public void setLiveVoiceName(String v) { sp.edit().putString("live_voice_name", v).apply(); }

    public float speechRate() { return sp.getFloat("speech_rate", 1.0f); }
    public void setSpeechRate(float v) { sp.edit().putFloat("speech_rate", v).apply(); }

    public float pitch() { return sp.getFloat("pitch", 1.06f); }
    public void setPitch(float v) { sp.edit().putFloat("pitch", v).apply(); }

    public String language() { return sp.getString("language", "hinglish"); }
    public void setLanguage(String v) { sp.edit().putString("language", v).apply(); }

    public String ttsVoice() { return sp.getString("tts_voice", ""); }
    public void setTtsVoice(String v) { sp.edit().putString("tts_voice", v).apply(); }

    // ---- Personality / behaviour ---------------------------------------
    public String userName() { return sp.getString("user_name", ""); }
    public void setUserName(String v) { sp.edit().putString("user_name", v).apply(); }

    public String assistantName() { return sp.getString("assistant_name", "JARVIS"); }
    public void setAssistantName(String v) { sp.edit().putString("assistant_name", v).apply(); }

    public boolean memoryEnabled() { return sp.getBoolean("memory_enabled", true); }
    public void setMemoryEnabled(boolean v) { sp.edit().putBoolean("memory_enabled", v).apply(); }

    public boolean autoMemory() { return sp.getBoolean("auto_memory", true); }
    public void setAutoMemory(boolean v) { sp.edit().putBoolean("auto_memory", v).apply(); }

    public boolean confirmActions() { return sp.getBoolean("confirm_actions", true); }
    public void setConfirmActions(boolean v) { sp.edit().putBoolean("confirm_actions", v).apply(); }

    public boolean streaming() { return sp.getBoolean("streaming", true); }
    public void setStreaming(boolean v) { sp.edit().putBoolean("streaming", v).apply(); }

    public float temperature() { return sp.getFloat("temperature", 0.85f); }
    public void setTemperature(float v) { sp.edit().putFloat("temperature", v).apply(); }

    public boolean handsFree() { return sp.getBoolean("hands_free", true); }
    public void setHandsFree(boolean v) { sp.edit().putBoolean("hands_free", v).apply(); }

    // ---- Look -----------------------------------------------------------
    public String themeMode() { return sp.getString("theme_mode", "dark"); }
    public void setThemeMode(String v) { sp.edit().putString("theme_mode", v).apply(); }

    public int hairStyle() { return sp.getInt("hair_style", 0); }
    public void setHairStyle(int v) { sp.edit().putInt("hair_style", v).apply(); }

    public int eyeStyle() { return sp.getInt("eye_style", 0); }
    public void setEyeStyle(int v) { sp.edit().putInt("eye_style", v).apply(); }

    public int characterMood() { return sp.getInt("character_mood", 0); }
    public void setCharacterMood(int v) { sp.edit().putInt("character_mood", v).apply(); }

    // ---- States ---------------------------------------------------------
    public boolean onboarded() { return sp.getBoolean("onboarded", false); }
    public void setOnboarded(boolean v) { sp.edit().putBoolean("onboarded", v).apply(); }

    public boolean notifAsked() { return sp.getBoolean("notif_asked", false); }
    public void setNotifAsked(boolean v) { sp.edit().putBoolean("notif_asked", v).apply(); }

    public boolean welcomeShown() { return sp.getBoolean("welcome_shown", false); }
    public void setWelcomeShown(boolean v) { sp.edit().putBoolean("welcome_shown", v).apply(); }

    public boolean overlayEnabled() { return sp.getBoolean("overlay_enabled", false); }
    public void setOverlayEnabled(boolean v) { sp.edit().putBoolean("overlay_enabled", v).apply(); }

    public int overlayX() { return sp.getInt("overlay_x", 0); }
    public int overlayY() { return sp.getInt("overlay_y", 0); }
    public void setOverlayPos(int x, int y) { sp.edit().putInt("overlay_x", x).putInt("overlay_y", y).apply(); }

    public boolean orbHintShown() { return sp.getBoolean("orb_hint", false); }
    public void setOrbHintShown(boolean v) { sp.edit().putBoolean("orb_hint", v).apply(); }

    public long lastOpen() { return sp.getLong("last_open", 0); }
    public void setLastOpen(long v) { sp.edit().putLong("last_open", v).apply(); }

    public int conversationId() { return sp.getInt("conversation_id", 0); }
    public void setConversationId(int v) { sp.edit().putInt("conversation_id", v).apply(); }

    public long dailyApiCalls() {
        long day = System.currentTimeMillis() / 86400000L;
        if (sp.getLong("api_day", 0) != day) {
            sp.edit().putLong("api_day", day).putLong("api_calls", 0).apply();
            return 0;
        }
        return sp.getLong("api_calls", 0);
    }

    public void countApiCall() {
        long day = System.currentTimeMillis() / 86400000L;
        long calls = sp.getLong("api_day", 0) == day ? sp.getLong("api_calls", 0) : 0;
        sp.edit().putLong("api_day", day).putLong("api_calls", calls + 1).apply();
    }

    public void bumpLearned(String key) {
        sp.edit().putLong("learned_" + key, System.currentTimeMillis()).apply();
    }

    public boolean isLearned(String key) {
        return sp.getLong("learned_" + key, 0) > 0;
    }
}
