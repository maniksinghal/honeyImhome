package com.wordpress.randomexplorations.honeyimhome;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


public class Jarvis extends IntentService {

    private MessageSpeaker speaker = null;
    private Intent runningIntent = null;  // currently running intent
    private List<Intent> workList = null;
    private boolean connected_to_car = false;


    public Jarvis() {
        super("Jarvis");
    }

    @Override
    public void onCreate() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        speaker = new MessageSpeaker(this, getApplicationContext());
        runningIntent = null;
        workList = new ArrayList<>();
        connected_to_car = prefs.getBoolean(MyReceiver.AM_IN_CAR, false);
        Log.d("this", "Service created with car connection: " + connected_to_car);
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

        Log.d("this", "Received intent: " + intent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1));
        workList.add(intent);
        if (runningIntent == null) {
            cleanupIntent();
        }
        return START_NOT_STICKY;
    }

    public void cleanupIntent() {

        if (runningIntent != null) {
            // Cleanup runningIntent

            if (!runningIntent.getBooleanExtra(MyReceiver.EXTRA_NON_WAKEFUL, false)) {
                MyReceiver.completeWakefulIntent(runningIntent);
            }
            runningIntent = null;
        }

        if (!workList.isEmpty()) {
            runningIntent = workList.remove(0);
            processIntent();
        } else {
            // Cleanup all resources
            if (speaker.ready && !connected_to_car) {
                speaker.shutdown();
            }
        }

    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }


    // Update the time when we last spoke. This is used
    // to decide whether to re-greet the user when speaking
    // again after a long time gap.
    public void update_last_greeting_time() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        Calendar cal = Calendar.getInstance();

        long now_time = (cal.get(Calendar.DAY_OF_MONTH) * 24 * 60) +
                (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE);

        editor.putLong(getString(R.string.last_play_time), now_time);

        if (runningIntent.getBooleanExtra(MyReceiver.EXTRA_IS_WEATHER_UPDATE, false)) {
            Log.d("this", "Jarvis: Updated last weather-update time in shared preferences");
            editor.putLong(getString(R.string.last_weather_time), now_time);
        }

        editor.commit();
    }


    /*
    * Add some prefix to speech depending on time of day and
    * last time when we spoke something.
     */
    private String generate_greeting() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Calendar cal = Calendar.getInstance();
        long last_play_time = 0;
        long now_time = 0;
        String greeting = null;

        last_play_time = prefs.getLong(getString(R.string.last_play_time), 0);
        now_time = (cal.get(Calendar.DAY_OF_MONTH) * 24 * 60) +
                (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE);

        long last_time_day = last_play_time / (24 * 60);

        if (cal.get(Calendar.DAY_OF_MONTH) != last_time_day &&
                cal.get(Calendar.HOUR_OF_DAY) < 12) {
            // We didn't greet today and its less than 12 noon
            greeting = "Good Morning " + prefs.getString(getString(R.string.my_name), null);

        } else if (now_time < last_play_time || (now_time - last_play_time) > 10) {
            // Its been more than 10 minutes
            greeting = prefs.getString(getString(R.string.my_name), null);
        }

        return greeting;
    }


    private boolean is_weekday_evening() {
        Calendar cal = Calendar.getInstance();

        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY &&
                cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY &&
                cal.get(Calendar.HOUR_OF_DAY) > 16) {
            // >4pm on weekday
            return true;
        } else {
            return false;
        }
    }

    private boolean weather_update_required() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean weather_enabled = pref.getBoolean(getString(R.string.weather_update), false);

        if (!weather_enabled) {
            return false;
        }

        // Check the time right now
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) >= 12) {
            // Its afternoon/evening
            return false;
        }

        long last_weather_time = pref.getLong(getString(R.string.last_weather_time), 0);
        long last_time_day = last_weather_time / (24 * 60);
        if (last_time_day == now.get(Calendar.DAY_OF_MONTH)) {
            // Weather updated this morning itself
            return false;
        }

        return true;
    }


    /*
    * User just connected to car stereo
    */
    private void disconnected_from_car() {

        int insert_location = 0;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        // Break-up this intent into multiple child intents.
        // Queue the child intents in the beginning of the workList so they get processed now.


        // Check if need to inform Hubby about reaching home
        if (is_weekday_evening()) {
            Intent i = new Intent();
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            i.putExtra(MyReceiver.EXTRA_NON_WAKEFUL, true);

            String phone = prefs.getString(getString(R.string.hubby_phone_number), "0000");
            String message = prefs.getString(getString(R.string.disconnect_message), "empty");
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone, null, message, null, null);

            message = "Informed ";
            message += prefs.getString(getString(R.string.hubby_name), null);
            message += " that you've reached home";
            i.putExtra(MyReceiver.EXTRA_VALUE, message);
            workList.add(insert_location, i);
            insert_location++;
        }

        // add more possible intents here


        connected_to_car = false;
        // Keep the car-disconnected intent to hold the wakelock, so that the sub-intents can run
        // without any issue
        runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_INVALID); // now nop
        workList.add(insert_location, runningIntent);  // re-queue after above intents.
        runningIntent = null;  // so that it does not get wake-lock-released by cleanupIntent
        cleanupIntent();  // Cleanup current 'disconnect-from-car' intent and pick up first child-intent
        return;
    }

    /*
    * User just connected to car stereo
    */
    private void connected_to_car() {

        int insert_location = 0;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Log.d("this", "Jarvis: Connected to Car");

        // MessageSpeaker might be initialized but may not be using BluetoothSCO.
        // Shutdown MessageSpeaker so that it re-initializes with BluetoothSCO
        if (speaker.ready) {
            Log.d("this", "Jarvis: Re-initing the speaker");
            speaker.shutdown();
        }

        // Break-up this intent into multiple child intents.
        // Queue the child intents in the beginning of the workList so they get processed now.

        // Queue an intent for welcome message
        Intent i = new Intent();
        i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
        i.putExtra(MyReceiver.EXTRA_NON_WAKEFUL, true);
        i.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);

        // Check if need to inform Hubby about leaving office
        String message = null;
        if (is_weekday_evening()) {
            String phone = prefs.getString(getString(R.string.hubby_phone_number), "0000");
            message = prefs.getString(getString(R.string.connect_message), "empty");
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone, null, message, null, null);

            message = "Informed ";
            message += prefs.getString(getString(R.string.hubby_name), null);
            message += " that you're leaving office";
            i.putExtra(MyReceiver.EXTRA_VALUE, message);
        } else {
            // Just print a regular welcome message
            message = prefs.getString(getString(R.string.welcome_message), null);
            i.putExtra(MyReceiver.EXTRA_VALUE, message);
        }
        Log.d("this", "Jarvis: Enqueuing message: '" + message + "' for play");
        workList.add(insert_location, i);
        insert_location++;

        // Check if weather-update is available/applicable
        if (weather_update_required()) {
            i = new Intent();
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_FETCH_WEATHER);
            i.putExtra(MyReceiver.EXTRA_NON_WAKEFUL, true);
            workList.add(insert_location, i);
            insert_location++;
        }

        // Check if time-to office is applicable

        connected_to_car = true;
        // Keep the car-connected intent to hold the wakelock, so that the sub-intents can run
        // without any issue
        runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_INVALID); // now nop
        workList.add(insert_location, runningIntent);  // re-queue after above intents.
        runningIntent = null;  // so that it does not get wake-lock-released by cleanupIntent
        cleanupIntent();  // Cleanup current 'connect-to-car' intent and pick up first child-intent
        return;
    }


    private void play_message(String message) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Boolean use_sco = prefs.getBoolean(getString(R.string.bluetooth_sco), false);
        Log.d("this", "Jarvis: Playing " + message + " with use_sco: " + use_sco);

        if (!connected_to_car &&
                !runningIntent.getBooleanExtra(MyReceiver.EXTRA_FORCE_PLAY, false)) {
            // Should play this message only if user allows playing all messages
            if (!prefs.getBoolean(getString(R.string.always_speak_out), false)) {
                cleanupIntent();
                return;
            }
        }



        if (!speaker.ready) {
            int sco_mode = Integer.parseInt(prefs.getString(getString(R.string.sco_mode), null));
            speaker.initialize(use_sco, sco_mode);  // Initializer will call us back
            return;
        }

        // Speaker is ready
        // Generate user greeting message
        String greeting = generate_greeting();
        if (greeting != null) {
            message = greeting + ", " + message;
        }

        // All ready to speak?
        speaker.speak(message);

        return;

    }

    public void processIntent() {

        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int purpose = runningIntent.getIntExtra(MyReceiver.EXTRA_PURPOSE,
                MyReceiver.EXTRA_PURPOSE_INVALID);

        switch(purpose) {
            case MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY:
                String message = runningIntent.getStringExtra(MyReceiver.EXTRA_VALUE);
                if (message != null) {
                    play_message(message);
                } else {
                    cleanupIntent();
                }
                break;

            case MyReceiver.EXTRA_PURPOSE_START_SCO:
                int sco_mode = Integer.parseInt(prefs.getString(getString(R.string.sco_mode), null));

                // Override purpose to play success message
                runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
                runningIntent.putExtra(MyReceiver.EXTRA_VALUE, "SCO initialization success");
                boolean status = speaker.initialize_sco(sco_mode);
                if (!status) {
                    cleanupIntent();
                }
                break;

            case MyReceiver.EXTRA_PURPOSE_CAR_CONNECT:
                connected_to_car();
                break;

            case MyReceiver.EXTRA_PURPOSE_CAR_DISCONNECT:
                disconnected_from_car();
                break;

            case MyReceiver.EXTRA_PURPOSE_INVALID:
                // Represents a NOP action
                // Could be used for retaining a wakelock held inside the intent.
                cleanupIntent();
                break;

            case MyReceiver.EXTRA_PURPOSE_FETCH_WEATHER:
                String woeid = prefs.getString(getString(R.string.weather_woeid), "0");
                new WeatherUpdate(this, woeid).execute(null, null, null);
                break;

            case MyReceiver.EXTRA_PURPOSE_FETCH_NEWS:
                new NewsUpdate(this).execute(null, null, null);
                break;

            default:
                cleanupIntent();
                break;
        }

        return;
    }

    public void processIntent(String message, List<String> bools) {
        runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
        runningIntent.putExtra(MyReceiver.EXTRA_VALUE, message);

        if (bools != null) {
            while (!bools.isEmpty()) {
                String s = bools.remove(0);
                runningIntent.putExtra(s, true);
            }
        }

        processIntent();
    }
}
