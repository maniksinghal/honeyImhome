package com.wordpress.randomexplorations.honeyimhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by maniksin on 6/19/15.
 */
public class MessageSpeaker implements
        TextToSpeech.OnInitListener,TextToSpeech.OnUtteranceCompletedListener{

    // Technical parameters
    private TextToSpeech tts = null;
    private String audio_stream = null;
    private HashMap<String,String> myHashAlarm = null;
    private BluetoothScoManager scoMgr = null;

    // Timers to wait for SCO
    private Timer timer = null;
    private TimerTasks timerTask = null;

    // Caller information
    private Jarvis jarvis = null;
    private Context context = null;

    // flag shared between tts and sco to inform if anything errored out
    private boolean msg_speaker_errored = false;

    // Status information
    public boolean ready = false;
    private boolean waiting_for_tts = false;
    private boolean waiting_for_sco = false;

    public MessageSpeaker(Jarvis jvs, Context ctx) {
        jarvis = jvs;
        context = ctx;
    }

    /*
    * Timer to stop waiting-for-sco state and
    * continue processing the intentQueue
     */
    private class TimerTasks extends TimerTask {

        public void run() {

            Log.d("this", "Sco Timer expired when waiting_for_sco: " + waiting_for_sco);
            if (waiting_for_sco) {
                /*
                * Not received SCO update, assume failed
                * Continue to speak from the mobile
                 */
                waiting_for_sco = false;

                if (scoMgr != null) {
                    context.unregisterReceiver(scoMgr);
                    scoMgr = null;
                }

                jarvis.processIntent();
            }
        }

    }

    private class BluetoothScoManager extends BroadcastReceiver {

        private boolean connection_attempt_started = false;

        public void startManager(Context ctx) {
            IntentFilter in = new IntentFilter();
            in.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            context.registerReceiver(this, in);
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (action.equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {

                int sco_audio_state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_ERROR);
                Log.d("this", "ScoManager: Received sco state: " + sco_audio_state);

                switch (sco_audio_state) {
                    case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                        if (!connection_attempt_started && waiting_for_sco) {
                            connection_attempt_started = true;
                        }
                        break;


                    case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                        Log.d("this", "SCO connected while waiting_for_sco: " + waiting_for_sco +
                             " waiting_for_tts: " + waiting_for_tts);

                        if (timer != null) {
                            // Timer was waiting for SCO
                            timer.cancel();
                            timer = null;
                        }

                        if (waiting_for_sco) {
                            waiting_for_sco = false;

                            AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

                            if (!connection_attempt_started) {
                                // SCO was already started. Start from your end too so that
                                // if the other app disconnects, we still have it connected.
                                connection_attempt_started = true;
                                am.startBluetoothSco();
                                am.setBluetoothScoOn(true);

                            } else {
                                // SCO started on our request
                                int mode;
                                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                                am.setSpeakerphoneOn(false);

                                // Check if mode setting worked
                                mode = am.getMode();
                                Log.d("this", "SCO established while attempting to connect, mode: " + mode);
                            }

                            ready = true;
                            jarvis.processIntent();
                        }
                        break;


                    case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                    case AudioManager.SCO_AUDIO_STATE_ERROR:
                        Log.d("this", "SCO disconnect/error waiting_sco: " + waiting_for_sco +
                                " waiting_tts: " + waiting_for_tts + " conn_attempt: " + connection_attempt_started);
                        AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                        if (connection_attempt_started && waiting_for_sco) {
                            /*
                            * We reach here only if TTS is already initialized
                            * If SCO fails, then continue speaking from mobile
                             */
                            waiting_for_sco = false;
                            //msg_speaker_errored = true;

                            if (timer != null) {   // Stop the timer, that the event has occured.
                                timer.cancel();
                                timer = null;
                            }

                            context.unregisterReceiver(this);
                            scoMgr = null;
                            ready = true;

                            jarvis.processIntent();

                        } else if (waiting_for_sco) {
                            Log.d("this", "Received event SCO disconnected/Errored!! " +
                                            "Now starting first connection attempt");
                            connection_attempt_started = true;
                            am.stopBluetoothSco();  // Stop before start todo: see if it works
                            am.startBluetoothSco();
                        } else {
                            // We were already connected and now SCO got switched off
                            if (scoMgr != null) {
                                am.setMode(AudioManager.MODE_NORMAL);
                                am.setSpeakerphoneOn(true);
                                scoMgr = null;
                            }
                        }
                        break;

                    default:
                        Log.d("this", "Unknown SCO state!!");
                        break;
                }

            }

        }
    }


    // Text-to-speech initialization callback
    @Override
    public void onInit(int status) {

        boolean tts_errored = false;

        waiting_for_tts = false;

        if (status != TextToSpeech.SUCCESS) {
            Log.d("this", "TextToSpeech init failed: " + status);
            tts_errored = true;
        }

        if (!tts_errored && tts.isLanguageAvailable(Locale.ENGLISH) != TextToSpeech.LANG_AVAILABLE) {
            Log.d("this", "TextToSpeech Locale not available");
            tts_errored = true;
            tts.shutdown();
        }

        if (tts_errored) {
            msg_speaker_errored = true;
            if (!waiting_for_sco) {

                if (scoMgr != null) {
                    // TTS errored while sco initialized.
                    context.unregisterReceiver(scoMgr);
                    scoMgr = null;
                }

                jarvis.cleanupIntent();
            }
            return;
        }

        tts.setLanguage(Locale.ENGLISH);

        myHashAlarm = new HashMap();
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                "end of wakeup message ID");

        // Overriding stream to music always as it works fine both
        // from phone (non-sco) and stereo (during sco)
        audio_stream = String.valueOf(AudioManager.STREAM_MUSIC);

        /*if (scoMgr != null) {
            // SCO in action
            audio_stream = String.valueOf(AudioManager.STREAM_VOICE_CALL);
        } else {
            audio_stream = String.valueOf(AudioManager.STREAM_MUSIC);
        }*/

        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                audio_stream);

        tts.setOnUtteranceCompletedListener(this);  // callback when speech is complete.
        //tts.setSpeechRate((float) 0.7);
        //tts.setPitch((float)0.8);

        // Check if SCO is also ready
        waiting_for_tts = false;
        if (waiting_for_sco) {
            initialize_sco(0);  // We'll get called back in case of success/error
        } else {
            ready = true;
            jarvis.processIntent();
        }
    }

    public void speak(String message) {
        if (ready) {
            Log.d("this", "TTS: Speaking: " + message);
            tts.speak(message, TextToSpeech.QUEUE_ADD, myHashAlarm);
        } else {
            Log.d("this", "MessageSpeaker: TTS not ready");
            jarvis.cleanupIntent();
        }
    }


    public void shutdown() {
        if (ready) {
            tts.shutdown();
            tts = null;

            if (scoMgr != null) {
                AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                am.stopBluetoothSco();
                am.setMode(AudioManager.MODE_NORMAL);
                am.setSpeakerphoneOn(true);
                scoMgr = null;
            }

            ready = false;
        }
    }


    public void onUtteranceCompleted(String uttId) {
        jarvis.update_last_greeting_time();
        jarvis.cleanupIntent();
    }


    public void initialize(boolean use_sco, int mode) {

        /*
        * TTS initialization takes time as compared to SCO
        * If we init both of them in parallel, then there's an awkward silence
        * in the car stereo while waiting for TTS
        * Therefore, we init the SCO after the TTS is initialized.
         */
        shutdown();
        msg_speaker_errored = false;

        Log.d("this", "Initializing speaker with use_sco: " + use_sco + " and mode: " + mode);

        if (use_sco) {
            waiting_for_sco = true;
        }

        tts = new TextToSpeech(context, this);
        waiting_for_tts = true;
    }

    public boolean initialize_sco(int mode) {

        //msg_speaker_errored = false;

        AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        Log.d("this", "MessageSpeaker: SCO supported? " + am.isBluetoothScoAvailableOffCall());

        if (am.isBluetoothScoAvailableOffCall()) {

            int timer_interval = 8000;  // msec

            // Switch to bluetooth SCO as well
            scoMgr = new BluetoothScoManager();
            waiting_for_sco = true;

            Log.d("this", "Attempting Sco connect with timer: " + timer_interval);
            scoMgr.startManager(context);

            // Start timer to monitor SCO receiver response
            timer = new Timer();
            timerTask = new TimerTasks();

            timer.schedule(timerTask, timer_interval);

        } else {
            return false;
        }

        return true;

    }
}
