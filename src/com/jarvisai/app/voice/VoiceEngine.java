package com.jarvisai.app.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import com.jarvisai.app.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Classic voice path: Android speech recognition in, Indian-accented TTS out.
 * Used as a fallback and as the default when Live voice is disabled.
 */
public class VoiceEngine {

    public interface Listener {
        void onReady(boolean ok, String message);
        void onListening();
        void onPartial(String text);
        void onHeard(String text);
        void onSpeechStart();
        void onSpeechDone();
        void onError(String message);
    }

    private final Context ctx;
    private final Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean ttsReady;
    private boolean listening;
    private boolean speaking;
    private int utteranceSeq;

    public VoiceEngine(Context c, Listener l) {
        this.ctx = c.getApplicationContext();
        this.prefs = Prefs.get(c);
        this.listener = l;
    }

    public void init() {
        tts = new TextToSpeech(ctx, status -> {
            if (status == TextToSpeech.SUCCESS) {
                configureTts();
                ttsReady = true;
                main.post(() -> listener.onReady(true, null));
            } else {
                ttsReady = false;
                main.post(() -> listener.onReady(false, "Text-to-speech engine available nahi hai."));
            }
        });
        if (tts != null) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    speaking = true;
                    main.post(listener::onSpeechStart);
                }

                @Override
                public void onDone(String utteranceId) {
                    speaking = false;
                    main.post(listener::onSpeechDone);
                }

                @Override
                public void onError(String utteranceId) {
                    speaking = false;
                    main.post(listener::onSpeechDone);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    speaking = false;
                    main.post(listener::onSpeechDone);
                }
            });
        }
    }

    private void configureTts() {
        if (tts == null) return;
        Locale locale = preferredLocale();
        int r = tts.setLanguage(locale);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH);
        }
        applyVoice();
        tts.setSpeechRate(prefs.speechRate());
        tts.setPitch(prefs.pitch());
    }

    private Locale preferredLocale() {
        String lang = prefs.language();
        if ("hindi".equals(lang)) return new Locale("hi", "IN");
        if ("english".equals(lang)) return new Locale("en", "IN");
        return new Locale("en", "IN");
    }

    private void applyVoice() {
        if (tts == null) return;
        String wanted = prefs.ttsVoice();
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null || voices.isEmpty()) return;
            Voice best = null;
            if (wanted != null && !wanted.isEmpty()) {
                for (Voice v : voices) {
                    if (v.getName().equals(wanted)) { best = v; break; }
                }
            }
            if (best == null) {
                int bestScore = -1;
                for (Voice v : voices) {
                    if (v.isNetworkConnectionRequired()) continue;
                    Locale l = v.getLocale();
                    int score = 0;
                    if ("IN".equalsIgnoreCase(l.getCountry())) score += 6;
                    if ("hi".equalsIgnoreCase(l.getLanguage())) score += 4;
                    else if ("en".equalsIgnoreCase(l.getLanguage())) score += 3;
                    if (l.equals(preferredLocale())) score += 5;
                    String name = v.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("female") || name.contains("#female") || name.contains("f00")) score += 2;
                    score += v.getQuality();
                    if (score > bestScore) { bestScore = score; best = v; }
                }
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) {}
    }

    public List<String[]> availableVoices() {
        List<String[]> out = new ArrayList<>();
        if (tts == null) return out;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return out;
            for (Voice v : voices) {
                Locale l = v.getLocale();
                if (!"IN".equalsIgnoreCase(l.getCountry())) continue;
                out.add(new String[]{v.getName(), l.getDisplayName()});
            }
            out.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        } catch (Exception ignored) {}
        return out;
    }

    public void setVoice(String name) {
        prefs.setTtsVoice(name);
        applyVoice();
        tts.setSpeechRate(prefs.speechRate());
        tts.setPitch(prefs.pitch());
    }

    public boolean isSpeaking() { return speaking; }

    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!ttsReady || tts == null) {
            main.post(() -> listener.onError("TTS ready nahi hai."));
            return;
        }
        String clean = speechify(text);
        try {
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, new Bundle(), "jarvis-" + (++utteranceSeq));
        } catch (Exception e) {
            main.post(() -> listener.onError("Bol nahi payi: " + e.getClass().getSimpleName()));
        }
    }

    public void stopSpeaking() {
        speaking = false;
        if (tts != null) tts.stop();
    }

    public void listen() {
        if (listening) return;
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            main.post(() -> listener.onError("Speech recognition is device par available nahi hai. Type karke baat karein."));
            return;
        }
        try {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    listening = true;
                    main.post(listener::onListening);
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    listening = false;
                    String msg;
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            msg = "Kuch sunai nahi diya. Dobara bolein.";
                            break;
                        case SpeechRecognizer.ERROR_AUDIO:
                            msg = "Audio recording error.";
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            msg = "Microphone permission chahiye.";
                            break;
                        case SpeechRecognizer.ERROR_NETWORK:
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            msg = "Speech recognition network error.";
                            break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            msg = "Recognizer busy hai, ek second baad try karein.";
                            break;
                        case SpeechRecognizer.ERROR_SERVER:
                            msg = "Recognition server error.";
                            break;
                        default:
                            msg = "Sun nahi payi (error " + error + ").";
                    }
                    main.post(() -> listener.onError(msg));
                }
                @Override public void onResults(Bundle results) {
                    listening = false;
                    String best = firstResult(results);
                    if (best == null || best.trim().isEmpty()) {
                        main.post(() -> listener.onError("Kuch samajh nahi aaya."));
                        return;
                    }
                    main.post(() -> listener.onHeard(best.trim()));
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    String partial = firstResult(partialResults);
                    if (partial != null && !partial.trim().isEmpty()) {
                        main.post(() -> listener.onPartial(partial));
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale());
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLocale());
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800);
            recognizer.startListening(i);
            listening = true;
            main.post(listener::onListening);
        } catch (Exception e) {
            main.post(() -> listener.onError("Mic start nahi ho paya: " + e.getMessage()));
        }
    }

    private String recognitionLocale() {
        String lang = prefs.language();
        if ("hindi".equals(lang)) return "hi-IN";
        if ("english".equals(lang)) return "en-IN";
        return "hi-IN"; // Hinglish recognises well with the Hindi model
    }

    public void stopListening() {
        listening = false;
        try { if (recognizer != null) recognizer.stopListening(); } catch (Exception ignored) {}
    }

    private static String firstResult(Bundle b) {
        if (b == null) return null;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    public void shutdown() {
        speaking = false;
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception ignored) {}
        tts = null;
        ttsReady = false;
        try { if (recognizer != null) { recognizer.destroy(); } } catch (Exception ignored) {}
        recognizer = null;
        listening = false;
    }

    /** Strips markdown/emoji so TTS sounds like a person, not a document. */
    public static String speechify(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replaceAll("```[\\s\\S]*?```", " Code snippet. ");
        s = s.replaceAll("`([^`]*)`", "$1");
        s = s.replaceAll("\\*\\*([^*]*)\\*\\*", "$1");
        s = s.replaceAll("\\*([^*]*)\\*", "$1");
        s = s.replaceAll("#+\\s*", "");
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        s = s.replaceAll("https?://\\S+", " link ");
        s = s.replaceAll("[\\u{1F300}-\\u{1FAFF}\\u{2600}-\\u{27BF}]", "");
        s = s.replaceAll("[•>|_~^]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
