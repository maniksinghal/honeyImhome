package com.wordpress.randomexplorations.honeyimhome;

import android.app.IntentService;
import android.content.Intent;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;


public class MessagePlayService extends IntentService implements
        TextToSpeech.OnInitListener,TextToSpeech.OnUtteranceCompletedListener {

    private TextToSpeech tts = null;
    private Intent thisIntent = null;

    public MessagePlayService() {
        super("MessagePlayService");
    }

    /*
    * MyReceiver invoked an intentService which overrides onStartCommand
    * to launch another thread => call onhandleIntent => stop the service
    * But we need async calls for TextToSpeech and cannot afford getting stopped
    * So we override this method and instead of the jargon above, perform TTS
    * on this thread itself.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleIntent(intent);
        return START_NOT_STICKY;
    }

    private void cleanup() {
        tts.shutdown();
        MyReceiver.completeWakefulIntent(thisIntent);  // release the wakelock
        stopSelf();
    }

    public void onUtteranceCompleted(String uttId) {
        Log.d("this", "utterence completed");
        cleanup();
    }

    // Text-to-speech initialization callback
    @Override
    public void onInit(int status) {

        if (status != TextToSpeech.SUCCESS) {
            Log.d("this", "Some problem initializing tts: " + status);
            cleanup();
            return;
        }

        // All set to speak
        if (tts.isLanguageAvailable(Locale.ENGLISH) != TextToSpeech.LANG_AVAILABLE) {
            Log.d("this", "English locale not available.. aborting");
            cleanup();
            return;
        }

        // All set to speak
        tts.setLanguage(Locale.ENGLISH);

        String message = thisIntent.getStringExtra(MyReceiver.MESSAGE_TO_PLAY);
        Log.d("this", "Going to speak out: " + message);

        HashMap<String, String> myHashAlarm = new HashMap();
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                "end of wakeup message ID");
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                String.valueOf(AudioManager.STREAM_NOTIFICATION));
        tts.setOnUtteranceCompletedListener(this);  // callback when speech is complete.
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
        return;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }

    private void handleIntent(Intent intent) {

        thisIntent = intent;

        if (tts != null) {
            tts.shutdown();
            tts = null;
        }

        // Initialize text-to-speech
        tts = new TextToSpeech(this, this);
        return;
    }
}
