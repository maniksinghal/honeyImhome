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
import android.preference.EditTextPreference;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
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

    private boolean wifi_connected = false;
    private String last_wifi_connected = null;

    /*
    * How many times to retry listening to user during
    * voice conversation.
     */
    public static final int MAX_RECOGNITION_RETRY = 3;
    public static final int INFINITE_RECOGNITION_RETRY = 255; // Wait for user to decide what to speak
    private int recognition_retry = MAX_RECOGNITION_RETRY;

    /*
    * Is Jarvis speaking for the first time?
    * For first-conv case, user may not even be speaking to us, don't blindly start complaining
    * if you don't know whether he is even talking to you.
     */
    private boolean first_conv = false;

    private Timer intentMonitor_timer = null;


    private Timer wifi_timer = null;

    /*
    private Timer poke_timer = null;
    */

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
            v.vibrate(2000);
        }

    }

    private void set_ringer_volume(int notif_volume, int ring_volume) {

        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notif_volume, 0);
        am.setStreamVolume(AudioManager.STREAM_RING, ring_volume, 0);

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
            int max_ringer_volume = am.getStreamMaxVolume(AudioManager.STREAM_RING);
            int max_notif_volume = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
            if (wifi_connected) {
                set_ringer_volume(max_notif_volume/2, max_ringer_volume/2);
            } else {
                set_ringer_volume(max_notif_volume, max_ringer_volume);
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

    // @todo: Running TimerTask in service is buggy, as it runs in another thread
    // @todo: and other thread would start accessing workList/running-intents in parallel.
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

    /*
    private class intentMonitorTask extends TimerTask {


        //Current intent ran/waited for too long
        //Kill it to resume operations

        public void run() {
            if (runningIntent != null) {
                int purpose = runningIntent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);
                String message = "Task " + purpose + " ran/waited for too long. Killing";
                sync_main_activity(true, message);
                cleanupIntent();
                return;
            }
        }
    }

    private class poke_activity_back extends TimerTask {
        public void run() {
            if (runningIntent == null) {
                Log.d("this", "Running intent NULL!!");
                return;
            }

            // Poke activity
            poke_timer = null;
            sync_main_activity(true);
            cleanupIntent();

        }
    }
*/

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
    * But we need async calls for TextToSpeech (service gets called back when TTS is ready) and hence
    * cannot afford getting stopped
    * So we override this method and instead of the jargon above, perform TTS
    * on this thread itself.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        int purpose = intent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);
        if (runningIntent != null) {
            int runningIntentPurpose = runningIntent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);
            Log.d("this", "Received intent " + purpose + "while running " + runningIntentPurpose);
        } else {
            Log.d("this", "Received intent: " + purpose);
        }


        // Process Exceptions...

        /*
        * 1. When we get a wifi-state change event, we start a timer for event to converge.
        * During this timer, we are holding the intent, but we want to allow other wifi-state
        * change intents to get processed.
        */

        if (purpose == MyReceiver.EXTRA_PURPOSE_WIFI_STATE_CHANGE) {
            if (runningIntent != null && wifi_timer != null) {
                // Free-up previous WIFI_STATE_CHANGE event and schedule this one at top of queue
                workList.add(0, intent);
                cleanupIntent();
                return START_NOT_STICKY;
            }
        }

        if (purpose == MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT) {
            if (runningIntent != null) {
                // Should always be the case

                /*
                * We were waiting for voice recognition result with runningIntent = CONV_RUNNING
                * Need to start processing VOICE_RECOGNITION_RESULT, but keep CONV_RUNNING in the
                * queue (as it holds the wake-lock for the service) and also for further listens
                 */
                workList.add(0, runningIntent);
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

        /*
        * If no other intents remain, do a cleanup before releasing the
        * possible wakelock with the current intent
         */
        if (workList.isEmpty()) {
            // Cleanup all resources
            if (speaker.ready) {
                /*
                * We need to shutdown speaker (at least SCO) as we may not get
                * notifications when user disconnects SCO (say from car stereo)
                * if our service is sleeping ??
                */
                speaker.shutdown();
            }
            setIntentSummary(null, false);
        }

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

    private boolean could_i_be_leaving_for_office() {

        // Check the day/time right now
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) >= 12) {
            // Its afternoon/evening
            return false;
        }

        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            return false;
        }

        /*
        * Its a weekday morning. Its highly probable that I am going to office
         */
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

        end_ride(prefs);

        /*
        * Use case: Parked car in office => office-wifi connected => car-bluetooth disconnected
        * Will cause volume to get restored
        * Skip restoring
         */
        //restore_ringer_volume();

        connected_to_car = false;
        sync_main_activity(false);
        cleanupIntent();
        return;
    }

    /*
    * Log the time when we started the car ride
     */
    private void start_ride(SharedPreferences prefs) {
        Long cur_time = System.currentTimeMillis();
        Long prev_end_time = prefs.getLong("ride_end_time", 0);

        if (cur_time - prev_end_time < (30*60*1000) &&
                cur_time - prev_end_time >= 0) {
            // looks like an upto half an hour break in a long journey
            // Continue the long ride
        } else {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong("ride_start_time", cur_time);
            editor.commit();
        }

    }

    /*
    * Log the end time of the car ride
     */
    private void end_ride(SharedPreferences prefs) {
        Long end_time = System.currentTimeMillis();
        Long start_time = prefs.getLong("ride_start_time", 0);

        if (end_time <= start_time) {
            // Something invalid.. ignore
            return;
        } else {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong("ride_end_time", end_time);
            editor.commit();

            Long minutes_diff = (end_time - start_time) / (1000 *60);
            show_reminder("Ride time today: " + minutes_diff + " minutes.", null);

        }
    }


    private void send_sms_to_hubby(String message) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String phone = prefs.getString(getString(R.string.hubby_phone_number), "0000");
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phone, null, message, null, null);
    }
    /*
    * User just connected to car stereo
    */
    private void connected_to_car() {

        int insert_location = 0;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean am_i_leaving_office;
        Log.d("this", "Jarvis: Connected to Car");
        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        boolean play_office_commute_eta = prefs.getBoolean(getString(R.string.office_commute_eta), true);

        // Bump up the volume to max
        int music_volume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, music_volume, 0);

        connected_to_car = true;
        sync_main_activity(true);   //Launch UI activity

        start_ride(prefs);


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
        am_i_leaving_office = leaving_office();
        if (am_i_leaving_office) {
            message = prefs.getString(getString(R.string.connect_message), "empty");
            send_sms_to_hubby(message);

            message = "Informed ";
            message += prefs.getString(getString(R.string.hubby_name), null);
            message += " that you're leaving office";
            i.putExtra(MyReceiver.EXTRA_VALUE, message);

            // Make sure we do not look novice sending message again if car restarts.
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(MyReceiver.EXTRA_LAST_WIFI_EVENT_TIMESTAMP, 0);
            editor.commit();

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
        if (am_i_leaving_office && play_office_commute_eta) {
            // Play the ETA for reaching home
            i = new Intent();
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_TIME_TO_DESTINATION);
            String orig = prefs.getString(getString(R.string.office_loc), "0000");
            String dest = prefs.getString(getString(R.string.home_loc), "0000");
            i.putExtra(MyReceiver.EXTRA_ORIG_LOCATION, orig);
            i.putExtra(MyReceiver.EXTRA_DEST_LOCATION, dest);
            i.putExtra(MyReceiver.EXTRA_DEST_NAME, "home");
            i.putExtra(MyReceiver.EXTRA_NON_WAKEFUL, true);
            workList.add(insert_location, i);
            insert_location++;
        } else if (could_i_be_leaving_for_office() && play_office_commute_eta) {
            // Play the ETA for reaching office
            i = new Intent();
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_TIME_TO_DESTINATION);
            String orig = prefs.getString(getString(R.string.home_loc), "0000");
            String dest = prefs.getString(getString(R.string.office_loc), "0000");
            i.putExtra(MyReceiver.EXTRA_ORIG_LOCATION, orig);
            i.putExtra(MyReceiver.EXTRA_DEST_LOCATION, dest);
            i.putExtra(MyReceiver.EXTRA_DEST_NAME, "office");
            i.putExtra(MyReceiver.EXTRA_NON_WAKEFUL, true);
            workList.add(insert_location, i);
            insert_location++;
        }

        // Keep the car-connected intent to hold the wakelock, so that the sub-intents can run
        // without any issue
        runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_INVALID);
        workList.add(insert_location, runningIntent);  // re-queue after above intents.
        runningIntent = null;  // so that it does not get wake-lock-released by cleanupIntent
        cleanupIntent();  // Cleanup current 'connect-to-car' intent and pick up first child-intent
        return;
    }

    private void play_message(ArrayList<String> list, int interval_ms) {
        String message = list.get(0);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Boolean use_sco = prefs.getBoolean(getString(R.string.bluetooth_sco), false);

        if (!connected_to_car &&
                !runningIntent.getBooleanExtra(MyReceiver.EXTRA_FORCE_PLAY, false)) {
            // Should play this message only if user allows playing all messages
            if (!prefs.getBoolean(getString(R.string.always_speak_out), false)) {
                Log.d("this", "NOT PLAYING " + message + " as not connected to car and FORCE option not ON");
                cleanupIntent();
                return;
            }
        }

        if (!speaker.ready) {
            if (!connected_to_car) {
                // Can't use SCO
                Log.d("this", "Not using SCO as not connected to car");
                use_sco = false;
            }
            speaker.initialize(use_sco);  // Initializer will call us back
            return;
        }

        speaker.speak(list, interval_ms);
    }


    private void play_message(String message, boolean store) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (store) {
            // Store the message as well
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(getString(R.string.repeat_last_message), message);
            editor.commit();
        }

        // Speaker is ready
        // Generate user greeting message
        String greeting = generate_greeting();
        if (greeting != null) {
            message = greeting + ", " + message;
        }

        // All ready to speak?
        ArrayList<String> list = new ArrayList<>();
        list.add(message);
        play_message(list, 0);

        return;

    }

    private void play_message(String message) {
        play_message(message, true);
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

    private void start_voice_recognition() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Boolean use_sco = prefs.getBoolean(getString(R.string.bluetooth_sco), false);
        Boolean prefer_offline = prefs.getBoolean(getString(R.string.prefer_offline_recognition), false);

        Log.d("this", "Prefer offline recognition: " + prefer_offline);

        /*
        * If voice recognition starts, then sometimes it picks up garbage
        * voice from the ongoing car stereo music.
        * If SCO is enabled, then start voice recognition after switching ON SCO
        * This would give a better experience to the user by muting the stereo music and
        * also allowing listening from the stereo, than the phone.
         */
        if (use_sco && connected_to_car && !speaker.ready) {
            Log.d("this", "Starting voice recognition after switching ON SCO\n");
            /* Speaker shall call us back and lead to re-processing of CONV_RUNNING intent.
             * This would lead to decrementing recognition-retry count, so compensate for it here */
            recognition_retry++;
            speaker.initialize(use_sco);  // speaker shall call us back and re-process CONV_RUNNING intent.
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_START_VOICE_RECOGNITION);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, prefer_offline);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /*
    * Function to handle the decoded voice command
    * At this stage, we have runningIntent = VoiceCommandResult, and
    * CONVERSATION_RUNNING intent pending in the queue
    *
    * This function should trigger an eventual cleanup of the current
    * VoiceCommandResult running Intent.
     */
    private void handle_voice_command(String message)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String service_name = prefs.getString(getString(R.string.voice_assistant), "jennifer");
        VoCI voci = null;
        String action = null;

        // Flatten all cases
        message = message.toLowerCase();
        service_name =service_name.toLowerCase();

        voci = new VoCI(this, service_name);
        action = voci.execute(message);

        Log.d("this", "Recognized following message: " + message);

        /*
        * We were successfully able to decode the voice.
        * We may not be able to decode the message, but we leave it to the user
        * to try as much as he/she wants.
         */



        if (action.equals(VoCI.VOCI_ACTION_INVALID)) {
            // Could not interpret the command, try again

            // Store the last rejected request
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(getString(R.string.repeat_rejected_request), message);
            editor.commit();

            Log.d("this", "Not matching our list. first_conv:" + first_conv +
              ", contains assistant-name " + service_name + ": " + message.contains(service_name));

            // Complain only during middle of conversations or if we are sure that user was
            // speaking to us
            if (!first_conv || message.contains(service_name)) {

                // We have user's attention
                first_conv = false;
                recognition_retry = MAX_RECOGNITION_RETRY;  // leave it to user to decide how long he wants to retry

                // Change intent-purpose to play-message
                String retry_msg = "I Don't know how to handle the request";
                runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
                runningIntent.putExtra(MyReceiver.EXTRA_VALUE, retry_msg);
                runningIntent.putExtra(MyReceiver.EXTRA_STORE_MSG, false);
                play_message(retry_msg, false);  // This will play message and pick up CONV_RUNNING intent
            } else {
                cleanupIntent();  // retry by picking up CONV_RUNNING intent again from the queue
            }

            //Log.d("this", "Could not interpret action from message");
            return;
        } else {
            // We were able to interpret what user said
            // Assume, now we are in middle of conversation and we have user's attention
            first_conv = false;
            recognition_retry = MAX_RECOGNITION_RETRY;
        }


        /* Found recognizable action */

        // Some actions are handled by Jarvis itself
        if (action.equals(VoCI.VOCI_REPEAT_LAST_MESSAGE)) {
            Log.d("this", "action: repeat last message");
            String last_msg = prefs.getString(getString(R.string.repeat_last_message), "Last message not found");
            last_msg = "This is the last message I played, " + last_msg;
            runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            runningIntent.putExtra(MyReceiver.EXTRA_VALUE, last_msg);
            play_message(last_msg, false);  // This will play message and pick up CONV_RUNNING intent

        } else if (action.equals(VoCI.VOCI_REPEAT_REJECTED_REQUEST)) {
            String last_msg = prefs.getString(getString(R.string.repeat_rejected_request), "Last rejected request not found");
            last_msg = "This is the last request I could not understand, " + last_msg;
            runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            runningIntent.putExtra(MyReceiver.EXTRA_VALUE, last_msg);
            play_message(last_msg, false);  // This will play message and pick up CONV_RUNNING intent

        } else if (action.equals(VoCI.VOCI_SMS_TO_HUBBY)) {
            if (voci.arg1 != null && !voci.arg1.isEmpty()) {
                // arg1 is the actual message to send
                send_sms_to_hubby(voci.arg1);
                Log.d("this", "action: Send sms to hubby: " + voci.arg1);

                // Store the last message sent
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(getString(R.string.repeat_sent_message), voci.arg1);
                editor.commit();

                message = "Ok, Sent the message as you requested";

                runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
                runningIntent.putExtra(MyReceiver.EXTRA_VALUE, message);
                play_message(message, false);  // This will play message and pick up CONV_RUNNING intent


            } else {
                message = "Could not understand the message you want to send";
                runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
                runningIntent.putExtra(MyReceiver.EXTRA_VALUE, message);
                play_message(message, false);  // This will play message and pick up CONV_RUNNING intent

            }
        } else if (action.equals(VoCI.VOCI_REPEAT_SENT_MESSAGE)) {


            String last_msg = prefs.getString(getString(R.string.repeat_sent_message), "Last sent message not found");
            last_msg = "This is the last message I sent, " + last_msg;
            Log.d("this", "action: repeat sent message : " + last_msg);
            runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            runningIntent.putExtra(MyReceiver.EXTRA_VALUE, last_msg);
            play_message(last_msg, false);  // This will play message and pick up CONV_RUNNING intent

        } else if (action.equals(VoCI.VOCI_FETCH_WEATHER)) {
            Log.d("this", "action: Weather update");
            String woeid = prefs.getString(getString(R.string.weather_woeid), "0");
            new WeatherUpdate(this, woeid).execute(null, null, null);

        } else if (action.equals(VoCI.VOCI_END_SESSION)) {
            Log.d("this", "action: End session");
            recognition_retry = 0;  // No more retries for voice recognition
            cleanupIntent();  // Cleanup this intent and pick CONV_RUNNING intent which should also clean-up

        } else if (action.equals(VoCI.VOCI_KEEP_LISTENING)) {
            // User wants a minute to decide what to instruct
            // Bump-up the recognize-retry count to keep retrying listening for the user.
            Log.d("this", "action: Keep listening");
            recognition_retry = INFINITE_RECOGNITION_RETRY;
            cleanupIntent();

        } else if (action.equals(VoCI.VOCI_ACTION_PLAY)) {

            // Playback the decoded message
            if (!voci.arg1.isEmpty()) {
                message = "Playing back " + voci.arg1;
            } else {
                message = "Could not get the message you want to play back";
            }

            runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            runningIntent.putExtra(MyReceiver.EXTRA_VALUE, message);
            play_message(message, true); //store as last played message as well

        } else if (action.equals(VoCI.VOCI_PLAY_NEWS)) {
            runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            runningIntent.putExtra(MyReceiver.EXTRA_VALUE, 1000);  // pause-interval ms
            setIntentSummary("Playing news from timesofindia.com", false); // UI must be running
            new NewsUpdate(this).execute(null, null, null);

        } else {
            // Default case
            Log.d("this", "No action associated with interpreted voice command");
            message = "I Did not understand the request, some internal error!!";
            runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            runningIntent.putExtra(MyReceiver.EXTRA_VALUE, message);
            runningIntent.putExtra(MyReceiver.EXTRA_STORE_MSG, false);
            play_message(message, false);  // This will play message and pick up CONV_RUNNING intent
        }

        return;
    }

    private void sync_main_activity(boolean force_sync, String message) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean ui_running = prefs.getBoolean(getString(R.string.ui_running), false);

        if (!ui_running && !force_sync) {
              // No need, main-activity may not be spawned
            Log.d("this", "Not notifying main activity as not connected to car");
            return;
        }

        Log.d("this", "Starting sync with main activity");
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_SYNC_MAIN_ACTIVITY);
        if (message != null) {
            intent.putExtra(MyReceiver.EXTRA_VALUE, message);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        /* Main activity shall read shared-preferences to sync with Jarvis */
        startActivity(intent);

    }

    private void sync_main_activity(boolean force_sync) {
        sync_main_activity(force_sync, null);
    }

    private void handle_power_state_change(boolean power_state) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor edit = prefs.edit();

        Log.d("this", "Battery charging state of phone changed to: " + power_state);

        edit.putBoolean(getString(R.string.battery_charging_state), power_state);
        edit.commit();

        sync_main_activity(false);

    }


    private void process_hubbys_message() {
        String message = null;

        if (connected_to_car) {
            /*
                * Play the current message, but also queue an intent to start the
                * conversation.
                * Since the current (SMS) intent is wakeful, so we set it as the conversation
                * intent, and enqueue a new intent for playing the message.
                */
            message = runningIntent.getStringExtra(MyReceiver.EXTRA_VALUE);
            Intent i = new Intent();
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
            i.putExtra(MyReceiver.EXTRA_VALUE, message);

            first_conv = true;
            runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_CONVERSATION_RUNNING);
            recognition_retry = MAX_RECOGNITION_RETRY;
            workList.add(runningIntent);

            runningIntent = i;
            play_message(message);
        } else {
            // Not connected to car, so no play and no conversation
            Log.d("this", "Not playing hubby's message as not connected to car");
            cleanupIntent();
        }
    }

    private void setIntentSummary(String summary, boolean force_sync) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(getString(R.string.intentSummary), summary);
        edit.commit();
        sync_main_activity(force_sync);

    }

    public void processIntent() {
        String message = null;
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int purpose = runningIntent.getIntExtra(MyReceiver.EXTRA_PURPOSE,
                MyReceiver.EXTRA_PURPOSE_INVALID);

        Log.d("this", "Processing intent with purpose: " + purpose);
        switch(purpose) {
            case MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY:

                ArrayList<String> list = runningIntent.getStringArrayListExtra(MyReceiver.EXTRA_MESSAGE_LIST);
                boolean store = runningIntent.getBooleanExtra(MyReceiver.EXTRA_STORE_MSG, true);
                if (list != null) {
                    int interval = runningIntent.getIntExtra(MyReceiver.EXTRA_VALUE, 0);
                    play_message(list, interval);
                } else {
                    message = runningIntent.getStringExtra(MyReceiver.EXTRA_VALUE);
                    if (message != null) {
                        play_message(message, store);
                    } else {
                        cleanupIntent();
                    }
                }
                break;

            case MyReceiver.EXTRA_PURPOSE_START_SCO:

                // Override purpose to play success message
                runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
                runningIntent.putExtra(MyReceiver.EXTRA_VALUE, "SCO initialization success");
                boolean status = speaker.initialize_sco();
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

            case MyReceiver.EXTRA_PURPOSE_TIME_TO_DESTINATION:
                String orig = runningIntent.getStringExtra(MyReceiver.EXTRA_ORIG_LOCATION);
                String dest = runningIntent.getStringExtra(MyReceiver.EXTRA_DEST_LOCATION);
                String dest_name = runningIntent.getStringExtra(MyReceiver.EXTRA_DEST_NAME);
                new TrafficUpdate(this, orig, dest, dest_name).execute(null, null, null);
                break;

            case MyReceiver.EXTRA_PURPOSE_FETCH_NEWS:
                /*
                * Call NewsUpdate which shall prepare the news list and call-back Jarvis
                * Setup the pause-interval between news items here
                 */
                runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
                runningIntent.putExtra(MyReceiver.EXTRA_VALUE, 1000);  // pause-interval ms
                setIntentSummary("Playing news from timesofindia.com", false); // UI must be running
                new NewsUpdate(this).execute(null, null, null);
                break;

            case MyReceiver.EXTRA_PURPOSE_SMS_RECEIVED:
                process_hubbys_message();
                break;

            case MyReceiver.EXTRA_PURPOSE_CONVERSATION_RUNNING:
                if (recognition_retry > 0) {
                    recognition_retry--;

                    /* Hold the runningIntent active so that any other interfering events are only queued
                     * in the workList, and not processed.
                     */
                    Log.d("this", "Starting voice recognition, retry-count: " + recognition_retry);
                    start_voice_recognition();  //Main activity will call us back
                } else {
                    // User stopped speaking or couldn't get what he is saying in max retries
                    Log.d("this", "User stopped speaking, or politely asked us to stop listening, closing listener");

                    // Fill up recognition retries for next time
                    recognition_retry = MAX_RECOGNITION_RETRY;
                    setIntentSummary(null, false); // Main-activity must be running when running voice-recognition
                    cleanupIntent();
                }
                break;

            case MyReceiver.EXTRA_PURPOSE_WIFI_STATE_CHANGE:
                handle_wifi_state_change();
                break;

            /*
            * Test command from Main-activity menu options
             */
            case MyReceiver.EXTRA_PURPOSE_START_VOICE_RECOGNITION:
                Log.d("this", "Starting voice recognition as per UI command...");
                first_conv = false;  // User-initiated recognition, user is going to speak to us
                runningIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_CONVERSATION_RUNNING);
                recognition_retry = MAX_RECOGNITION_RETRY;
                start_voice_recognition();
                break;

            /*
            * We should receive this only if our previously runningIntent was
            * CONVERSATION_RUNNING. We would have enqueued that intent again and
            * would be processing this intent now.
             */
            case MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT:
                String msg = runningIntent.getStringExtra(MyReceiver.EXTRA_VALUE);
                if (msg == null) {
                    // @todo: See if we want to alert user here!!
                    // Pick the CONVERSATION_RUNNING intent from the queue and process again
                    // if retry-count is left.
                    Log.d("this", "Voice recognition results: Null message");
                    cleanupIntent();
                } else {
                    // Voice recognition is active only through main-activity, so force-sync
                    // the UI update
                    setIntentSummary("heard:     " + msg, false);  // display the message read on user-screen
                    handle_voice_command(msg);
                }
                break;

            case MyReceiver.EXTRA_PURPOSE_POWER_STATE_CHANGED:
                boolean power_state = runningIntent.getBooleanExtra(MyReceiver.EXTRA_VALUE, false);
                handle_power_state_change(power_state);
                cleanupIntent();
                break;

            /*
            // Poke the main-activity back in the specified interval
            case MyReceiver.EXTRA_PURPOSE_POKE_ACTIVITY_BACK:
                int interval = runningIntent.getIntExtra(MyReceiver.EXTRA_VALUE, 5000);

                Log.d("this", "Poking back main activity in " + interval/1000 + " seconds");
                poke_timer = new Timer();
                poke_timer.schedule(new poke_activity_back(), interval);
                // Hold the running-intent to disallow other operations
                // Timer event shall release it
                break;
          */


            default:
                cleanupIntent();
                break;
        }

        return;
    }

    public void processMessagesIntent(ArrayList<String> list) {
        // Jarvis should have already set-up the pause-interval in runningIntent
        runningIntent.putStringArrayListExtra(MyReceiver.EXTRA_MESSAGE_LIST, list);
        processIntent();
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
