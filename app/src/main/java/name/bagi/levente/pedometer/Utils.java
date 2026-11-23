package name.bagi.levente.pedometer;

import java.util.Locale;

import android.app.Service;
import android.speech.tts.TextToSpeech;
import android.text.format.Time;
import android.util.Log;

public class Utils implements TextToSpeech.OnInitListener {
    private static final String TAG = "Utils";
    private Service mService;

    private static Utils instance = null;

    private Utils() {
    }
     
    public static Utils getInstance() {
        if (instance == null) {
            instance = new Utils();
        }
        return instance;
    }
    
    public void setService(Service service) {
        mService = service;
    }
    
    /********** SPEAKING **********/
    
    private TextToSpeech mTts;
    private boolean mSpeak = false;
    private boolean mSpeakingEngineAvailable = false;

    public void initTTS() {
        // Initialize text-to-speech. This is an asynchronous operation.
        // The OnInitListener (second argument) is called after initialization completes.
        Log.i(TAG, "Initializing TextToSpeech...");
        mTts = new TextToSpeech(mService,
            this  // TextToSpeech.OnInitListener
            );
    }
    public void shutdownTTS() {
        Log.i(TAG, "Shutting Down TextToSpeech...");

        mSpeakingEngineAvailable = false;
        if (mTts != null) {
            mTts.shutdown();
            mTts = null;
        }
        // Don't keep the stopped service alive through this singleton.
        mService = null;
        Log.i(TAG, "TextToSpeech Shut Down.");

    }
    public void say(String text) {
        if (mSpeak && mSpeakingEngineAvailable && mTts != null) {
            mTts.speak(text,
                    TextToSpeech.QUEUE_ADD,  // Drop all pending entries in the playback queue.
                    null);
        }
    }

    /** Speaks a translatable prompt from strings.xml, filled in with args. */
    public void say(int resId, Object... args) {
        if (mService == null) return;
        say(mService.getString(resId, args));
    }

    // Implements TextToSpeech.OnInitListener.
    public void onInit(int status) {
        // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
        if (status == TextToSpeech.SUCCESS) {
            // Speak in the phone's language; fall back to US English.
            int result = mTts.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = mTts.setLanguage(Locale.US);
            }
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
               // Language data is missing or the language is not supported.
                Log.e(TAG, "Language is not available.");
            } else {
                Log.i(TAG, "TextToSpeech Initialized.");
                mSpeakingEngineAvailable = true;
            }
        } else {
            // Initialization failed.
            Log.e(TAG, "Could not initialize TextToSpeech.");
        }
    }

    public void setSpeak(boolean speak) {
        mSpeak = speak;
    }

    public boolean isSpeakingEnabled() {
        return mSpeak;
    }

    public boolean isSpeakingNow() {
        return mTts != null && mTts.isSpeaking();
    }

    public void ding() {
    }
    
    /********** Time **********/
    
    public static long currentTimeInMillis() {
        Time time = new Time();
        time.setToNow();
        return time.toMillis(false);
    }
}
