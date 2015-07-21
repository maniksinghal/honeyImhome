package com.wordpress.randomexplorations.honeyimhome;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class Jarvis extends IntentService {

    private MessageSpeaker speaker = null;
    private Intent runningIntent = null;  // currently running intent
    private List<Intent> workList = null;
    private boolean connected_to_car = false;
    private String current_wifi_ssid = null;

    private boolean wifi_connected = false;
    private String last_wifi_connected = null;

    private Timer wifi_timer = null;

    public Jarvis() {
        super("Jarvis");
    }


    private void show_reminder(String reminder, Uri notification) {

        if (reminder != null) {

            Notification.Builder n = new Notification.Builder(this);
            n.setContentTitle("Myapp");
            n.setContentText(reminder);
            n.setSmallIcon(R.mipmap.ic_launcher);
            n.setAutoCancel(true);
            Notification nt = n.build();

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(0, nt);
            Log.d("this", "Notifying the message: " + reminder);

            /*
            // @todo: replace with Notification
            runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            runningIntent.putExtra(MyReceiver.EXTRA_VALUE, reminder);
            runningIntent.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);
            play_message(reminder);
            */
        }

        if (notification != null) {
            Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
            v.vibrate(500);
        }

    }

    private void set_ringer_volume(int notif_volume, int ring_volume) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor ed = pref.edit();
        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        int cur_notif_volume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        int cur_ring_volume = am.getStreamVolume(AudioManager.STREAM_RING);

        ed.putInt(getString(R.string.notification_volume), cur_notif_volume);
        ed.putInt(getString(R.string.ringer_volume), cur_ring_volume);
        ed.commit();

        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notif_volume, 0);
        am.setStreamVolume(AudioManager.STREAM_RING, ring_volume, 0);

        return;
    }

    private void restore_ringer_volume() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int notif_volume = pref.getInt(getString(R.string.notification_volume), -1);
        int ring_volume = pref.getInt(getString(R.string.ringer_volume), -1);
        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        if (notif_volume != -1) {
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notif_volume, 0);
        }

        if (ring_volume != -1) {
            am.setStreamVolume(AudioManager.STREAM_RING, ring_volume, 0);
        }


        return;
    }

    /*
    * Adjust phone ringer volume based on whether we logged in/off from office
     */
    private void handle_office_ringer_volume() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean set_office_volume = pref.getBoolean(getString(R.string.office_volume), false);
        String office_wifi = pref.getString(getString(R.string.office_wifi), null);
        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);


        if (office_wifi == null || !office_wifi.equals(last_wifi_connected)) {
            return;
        }

        // Don't change any audio setting if vibrate/silent mode is ON
        if (am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            return;
        }


        if (set_office_volume) {
            if (wifi_connected) {
                int max_ringer_volume = am.getStreamMaxVolume(AudioManager.STREAM_RING);
                int max_notif_volume = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
                set_ringer_volume(max_notif_volume/2, max_ringer_volume/2);
            } else {
                restore_ringer_volume();
            }
        }



    }

    private void handle_wifi_reminders() {

        Calendar date = Calendar.getInstance();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        Uri notification = Uri.parse(prefs.getString(getString(R.string.reminder_tone), null));


        String reminder = null;

        // Monday morning reminders
        if (date.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY && !wifi_connected) {
            String home_wifi = prefs.getString(getString(R.string.home_wifi), null);
            if (home_wifi != null && home_wifi.equals(last_wifi_connected)) {
                if (date.get(Calendar.HOUR_OF_DAY) <= 11) {
                    // Monday morning, disconnected from home-wifi => leaving for office
                    reminder = prefs.getString(getString(R.string.week_start_reminder), null);
                    show_reminder(reminder, notification);
                }
            }

        } else if (date.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY && !wifi_connected) {
            String office_wifi = prefs.getString(getString(R.string.office_wifi), null);
            if (office_wifi != null && office_wifi.equals(last_wifi_connected)) {
                if (date.get(Calendar.HOUR_OF_DAY) >= 15) {
                    // Friday evening, disconnected from office-wifi => leaving for home
                    reminder = prefs.getString(getString(R.string.week_end_reminder), null);
                    show_reminder(reminder, notification);
                }
            }

        }
    }

    /*
    * Timer run whenever wifi-state change event is received to
    * wait for multiple wifi events to stablize, and act on only the
    * last received event.
     */
    private class wifi_state_converger extends TimerTask {

        public void run() {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean new_state = prefs.getBoolean(MyReceiver.EXTRA_LAST_WIFI_CONNECTED, false);
            String new_wifi_name = prefs.getString(MyReceiver.EXTRA_LAST_WIFI_NAME, null);

            wifi_timer = null;

            wifi_connected = new_state;
            last_wifi_connected = new_wifi_name;
            Log.d("this", "Logged wifi-state change!! connected:" + new_state + ", name: " + new_wifi_name);

            handle_wifi_reminders();
            handle_office_ringer_volume();
            cleanupIntent();

        }

    }

    @Override
    public void onCreate() {

        Context context = getApplicationContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        speaker = new MessageSpeaker(this, getApplicationContext());
        runningIntent = null;
        workList = new ArrayList<>();
        connected_to_car = prefs.getBoolean(MyReceiver.AM_IN_CAR, false);
        Log.d("this", "Service created with car connection: " + connected_to_car);

        // Check wifi connectivity
        // Need to handle the case where sharedPreferences say Wifi is connected but we
        // are actually disconnected (phone power down during connected, and power-up during disconnected
        ConnectivityManager connManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (nInfo != null && nInfo.isConnected()) {
            // We are connected to some wifi.
            // Nothing to do here as we would be receiving events for this
        } else {
            // Wifi is disconnected
            boolean wifi_state_connected = prefs.getBoolean(MyReceiver.EXTRA_LAST_WIFI_CONNECTED, false);
            if (wifi_state_connected) {
                // Set state = wifi-disconnected.
                // 0 out the event timestamp, so that we don't treat it as a recent disconnection.
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(MyReceiver.EXTRA_LAST_WIFI_CONNECTED, true);
                editor.putLong(MyReceiver.EXTRA_LAST_WIFI_EVENT_TIMESTAMP, 0);
                editor.commit();
            }
        }

        wifi_connected = prefs.getBoolean(MyReceiver.EXTRA_LAST_WIFI_CONNECTED, false);
        last_wifi_connected = prefs.getString(MyReceiver.EXTRA_LAST_WIFI_NAME, null);
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

        // Process Exceptions...

        /*
        * 1. When we get a wifi-state change event, we start a timer for event to converge.
        * During this timer, we are holding the intent, but we want to allow other wifi-state
        * change intents to get processed.
        */
        int purpose = intent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);
        if (purpose == MyReceiver.EXTRA_PURPOSE_WIFI_STATE_CHANGE) {
            if (runningIntent != null && wifi_timer != null) {
                // Free-up previous WIFI_STATE_CHANGE event and schedule this one at top of queue
                workList.add(0, intent);
                cleanupIntent();
                return START_NOT_STICKY;
            }
        }

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


    private boolean leaving_office() {
        Calendar cal = Calendar.getInstance();
        long cur_time = cal.get(Calendar.DAY_OF_MONTH) * 24 * 60 +
                cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long last_wifi_event_time = prefs.getLong(MyReceiver.EXTRA_LAST_WIFI_EVENT_TIMESTAMP, 0);
        boolean wifi_connected = prefs.getBoolean(MyReceiver.EXTRA_LAST_WIFI_CONNECTED, false);
        String wifi_name = prefs.getString(MyReceiver.EXTRA_LAST_WIFI_NAME, "garbage_value");
        String office_wifi_name = prefs.getString(getString(R.string.office_wifi), "garbage_value2");

        long time_diff = cur_time - last_wifi_event_time;

        Log.d("this", "LeavingOffice: wifi:" + wifi_name + ", stored_wifi:" + office_wifi_name +
            ", state_connected:" + wifi_connected + ", time_diff:" + time_diff);

        // We are leaving office if we are still connected to office wifi or we
        // recently disconnected
        if (wifi_name.equals(office_wifi_name)) {
            if (wifi_connected) {
                return true;
            } else if (time_diff >= 0 && time_diff < 15) {
                // We disconnected from office wifi not more than 15 mintues ago
                return true;
            }
        }

        return false;
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
    * User just disconnected from car stereo
    */
    private void disconnected_from_car() {

        // @todo: just beep
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Uri notification = Uri.parse(prefs.getString(getString(R.string.car_disconnect_tone), null));
        if (notification != null) {
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        }

        /*
        * Use case: Parked car in office => office-wifi connected => car-bluetooth disconnected
        * Will cause volume to get restored
        * Skip restoring
         */
        //restore_ringer_volume();

        connected_to_car = false;
        cleanupIntent();
        return;
    }

    /*
    * User just connected to car stereo
    */
    private void connected_to_car() {

        int insert_location = 0;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Log.d("this", "Jarvis: Connected to Car");
        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        // Bump up the volume to max
        int music_volume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, music_volume, 0);


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
        if (leaving_office()) {
            String phone = prefs.getString(getString(R.string.hubby_phone_number), "0000");
            message = prefs.getString(getString(R.string.connect_message), "empty");
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone, null, message, null, null);

            message = "Informed ";
            message += prefs.getString(getString(R.string.hubby_name), null);
            message += " that you're leaving office";
            i.putExtra(MyReceiver.EXTRA_VALUE, message);

            // Make sure we do not look novice sending message again if car restarts.
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(MyReceiver.EXTRA_LAST_WIFI_EVENT_TIMESTAMP, 0);
            editor.commit();

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

    public void handle_wifi_state_change() {

        int interval = 2000; // 2 seconds

        if (wifi_timer != null) {
            // Event converger timer is running, restart it
            wifi_timer.cancel();
        }

        wifi_timer = new Timer();
        wifi_timer.schedule(new wifi_state_converger(), interval);

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

            case MyReceiver.EXTRA_PURPOSE_WIFI_STATE_CHANGE:
                handle_wifi_state_change();
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
