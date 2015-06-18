package com.wordpress.randomexplorations.honeyimhome;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


public class MessagePlayService extends IntentService implements
        TextToSpeech.OnInitListener,TextToSpeech.OnUtteranceCompletedListener {

    private TextToSpeech tts = null;
    private Intent runningIntent = null;  // currently running intent
    private List<Intent> workList = null;


    public MessagePlayService() {
        super("MessagePlayService");
    }

    @Override
    public void onCreate() {

        tts = null;
        runningIntent = null;
        workList = new ArrayList<>();
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

        workList.add(intent);
        if (runningIntent == null) {
            cleanup_and_next();
        }
        return START_NOT_STICKY;
    }

    private void cleanup_and_next() {

        if (runningIntent != null) {
            // Cleanup runningIntent
            MyReceiver.completeWakefulIntent(runningIntent);
            if (tts != null) {
                tts.shutdown();
                tts = null;
            }
            runningIntent = null;
        }

        if (!workList.isEmpty()) {
            runningIntent = workList.remove(0);
            handleIntent();
        }

    }

    public void onUtteranceCompleted(String uttId) {
        Log.d("this", "utterence completed");
        cleanup_and_next();
    }

    // Text-to-speech initialization callback
    @Override
    public void onInit(int status) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        long last_message_time = prefs.getLong(getString(R.string.last_play_time), 0);
        long now_time = 0;
        Calendar cal = Calendar.getInstance();

        now_time = (cal.get(Calendar.DAY_OF_MONTH) * 24 * 60) +
                (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE);


        if (status != TextToSpeech.SUCCESS) {
            Log.d("this", "Some problem initializing tts: " + status);
            cleanup_and_next();
            return;
        }

        // All set to speak
        if (tts.isLanguageAvailable(Locale.ENGLISH) != TextToSpeech.LANG_AVAILABLE) {
            Log.d("this", "English locale not available.. aborting");
            cleanup_and_next();
            return;
        }

        // Add a small greeting to the message if not spoken for long
        String message = runningIntent.getStringExtra(MyReceiver.EXTRA_VALUE);
        if (now_time < last_message_time || (now_time - last_message_time) > 10) {
            // Not spoken for last 10 minutes at least
            String greeting = "Hey ";
            greeting += prefs.getString(getString(R.string.my_name), null);
            message = greeting + ", " + message;

        }

        editor.putLong(getString(R.string.last_play_time), now_time);
        editor.commit();

        // All set to speak
        tts.setLanguage(Locale.ENGLISH);


        Log.d("this", "Going to speak out: " + message);

        HashMap<String, String> myHashAlarm = new HashMap();
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                "end of wakeup message ID");

        if (prefs.getBoolean(getString(R.string.voice_call_stream), true)) {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                    String.valueOf(AudioManager.STREAM_VOICE_CALL));  // use car-stereo
        } else {
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                    String.valueOf(AudioManager.STREAM_MUSIC));
        }

        tts.setOnUtteranceCompletedListener(this);  // callback when speech is complete.
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
        return;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }

    private void handleIntent() {

        int purpose = runningIntent.getIntExtra(MyReceiver.EXTRA_PURPOSE,
                MyReceiver.EXTRA_PURPOSE_INVALID);

        switch(purpose) {
            case MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY:
                tts = new TextToSpeech(this, this);
                break;

            default:
                cleanup_and_next();
                break;
        }

        return;
    }
}
