package com.wordpress.randomexplorations.honeyimhome;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.shapes.Shape;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutCompat;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

import android.text.method.ScrollingMovementMethod;


public class MainActivity extends ActionBarActivity {

    private SpeechRecognizer sr;
    private MyRecognitionListener myRecognitionListener;

    private Intent resultIntent = null;

    private Timer speechServiceTimer = null;
    private speechServiceTask speechTask = null;

    // State variables
    private boolean connected_to_car = false;
    private boolean is_phone_charging = false;
    private String user_notification = null;
    private boolean is_listening = false;
    private boolean is_ready_for_speech = false;

    //Communication between main UI thread and timer threads
    private int speech_timer_seq_num = 0;
    private Handler speech_timer_handler = new Handler(Looper.getMainLooper()) {
      @Override
        public void handleMessage (Message msg) {
          if (msg.what == speech_timer_seq_num) {
              // Message corresponding to current timer session
              // Speech Timer has expired, return null-message to Jarvis
              Log.d("this", "In main thread: Speech timer expiry received");
              if (sr != null) {

                  is_listening = false;
                  is_ready_for_speech = false;
                  update_ui();

                  Log.d("this", "Stopping speech recognition");
                  stopListening();
                  resultIntent = new Intent((Context)msg.obj, Jarvis.class);
                  resultIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT);
                  resultIntent.putExtra(MyReceiver.EXTRA_VALUE, (String)null);
                  MyReceiver.startWakefulService((Context)msg.obj, resultIntent);
              }
          }
      }
    };

    private void update_ui() {
        LinearLayout li = (LinearLayout)findViewById(R.id.main_layout);
        Button bt = (Button)findViewById(R.id.GoButton);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String layout_color = null;


        connected_to_car = prefs.getBoolean(MyReceiver.AM_IN_CAR, false);
        is_phone_charging = prefs.getBoolean(getString(R.string.battery_charging_state), false);
        user_notification = prefs.getString(getString(R.string.intentSummary), null);

        TextView tv = (TextView)findViewById(R.id.google_label);
        tv.setVisibility(View.GONE);

        if (is_ready_for_speech) {
            layout_color = getString(R.string.color_ready_for_speech);
        } else {
            layout_color = getString(R.string.color_dashboard_gray);
        }
        li.setBackgroundColor(Color.parseColor(layout_color));
        //getSupportActionBar().hide();


        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        int color = 0;

        if (connected_to_car) {
            // Activate the background color
            color = Color.parseColor(getString(R.string.button_color_connected));
        } else {
            color = Color.parseColor(getString(R.string.button_color_disconnected));
        }
        gd.setStroke(3, color);

        if (is_listening) {
            bt.setText("...");
            bt.setEnabled(false);
        } else {
            bt.setText("Go");
            bt.setEnabled(true);
        }

        bt.setBackground(gd);
        bt.setTextColor(color);

        if (is_phone_charging && connected_to_car) {
            // Keep screen always ON
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        tv = (TextView)findViewById(R.id.notification);
        if (user_notification == null) {
            // Set user-notification on screen
            user_notification = "";
        }
        tv.setText(user_notification);
    }

    private void handleRequestFromJarvis(Intent intent) {
        int value = intent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);

        if (value == MyReceiver.EXTRA_PURPOSE_START_VOICE_RECOGNITION) {
            Log.d("this", "MainActivity invoked for speech recognition");
            boolean offline_mode = intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
            start_speech_recognition(offline_mode);
        } else if (value == MyReceiver.EXTRA_PURPOSE_SYNC_MAIN_ACTIVITY) {
            // If we just disconnected from car, then close the activity
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean new_state = prefs.getBoolean(MyReceiver.AM_IN_CAR, false);
            boolean new_charging_state = prefs.getBoolean(getString(R.string.battery_charging_state), false);
            if (connected_to_car && !new_state) {
                // We disconnected from car, close the activity
                finish();
            }

            /*
            * todo: Following check is subject to jitter.
            * Temporary off-on in charging state may close the activity.
             */
            Log.d("this", "Phone previously charging: " + is_phone_charging + ", now: " + new_charging_state);
            if (!new_charging_state && is_phone_charging) {
                // When we switch off car ignition, blue-tooth state remains connected for some-time
                // but its time to remove phone from dock.
                finish();
            }

        }

        update_ui();
    }


    public class speechServiceTask extends TimerTask {

        private Context ctx;
        private int seq_num;

        public speechServiceTask(Context cont, int seq) {
            ctx = cont;
            seq_num = seq;
        }

        public void run() {

            /*
            * Timer expired while waiting for speech-recognition service
            * Treat it as - user didn't speak for now
            */
            Log.d("this", "speechService Timer expired. Notifying Main thread...");
            Message msg = speech_timer_handler.obtainMessage(seq_num, ctx);
            msg.sendToTarget();
            //finish();


        }
    }


    private void startWeatherUpdate() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String woeid = prefs.getString(getString(R.string.weather_woeid), "0");
        String weather = prefs.getString(getString(R.string.current_weather), "Not available");

        TextView weatherView = (TextView)findViewById(R.id.weatherView);
        weatherView.setText(weather);
        weatherView.setTextColor(Color.parseColor("#808080"));
        new WeatherUpdate(this, woeid).execute(null, null, null);
    }

    public void updateWeather(String description) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(getString(R.string.current_weather), description);
        editor.commit();

        TextView weatherView = (TextView)findViewById(R.id.weatherView);
        weatherView.setTextColor(Color.parseColor("#ffffff"));
        weatherView.setText(description);
    }

    protected void onNewIntent (Intent intent) {
        Log.d("this", "Received new intent");

        int value = intent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);
        if (value != -1) {
            handleRequestFromJarvis(intent);
        }

    }

    @Override
    protected void onResume() {

        super.onResume();

        startWeatherUpdate();
        update_ui();
    }


    private void print_google_label() {
        TextView tv = (TextView)findViewById(R.id.google_label);
        String label = "<i><font color='white'>Starting </font> <font color='blue'>G</font><font color='red'>o</font><font color='yellow'>o</font><font color='blue'>g</font><font color='green'>l</font><font color='red'>e</font></i>";
        tv.setText(Html.fromHtml(label), TextView.BufferType.SPANNABLE);
        tv.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor edit = prefs.edit();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_layout);

        edit.putBoolean(getString(R.string.ui_running), true);
        edit.commit();

        String assistant = prefs.getString(getString(R.string.voice_assistant), "???");
        getSupportActionBar().setTitle(assistant);

        myRecognitionListener = new MyRecognitionListener(this);

        TextView tv = (TextView)findViewById(R.id.hello_world);
        tv.setMovementMethod(new ScrollingMovementMethod());

        TextView weatherView = (TextView)findViewById(R.id.weatherView);
        weatherView.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {
                startWeatherUpdate();
            }

        });

        ImageView newsView = (ImageView)findViewById(R.id.news_icon);
        newsView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent i = new Intent(getApplicationContext(), Jarvis.class);
                i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_FETCH_NEWS);
                i.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);
                MyReceiver.startWakefulService(getApplicationContext(), i);
            }
        });

        Button bt = (Button)findViewById(R.id.GoButton);
        /*bt.setOnClickListener(new Button.OnClickListener() {

            public void onClick(View bt) {
                command_speech_recognition();
            }
        });*/

        bt.setOnTouchListener(new Button.OnTouchListener() {
            private int motion_events = 0;
            private static final int MOTION_EVENT_THRESHOLD = 5;

            public boolean onTouch(View view, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        motion_events++;
                        if (motion_events == MOTION_EVENT_THRESHOLD) {
                            // External search
                            print_google_label();
                        }
                        return true;
                    case MotionEvent.ACTION_BUTTON_PRESS:
                        Log.d("this", "Got Button press");
                        return true;
                    case MotionEvent.ACTION_UP:
                        Log.d("this", "Got Go Button ACTION_UP after " + motion_events + " moves");
                        int events = motion_events;
                        motion_events = 0;
                        if (events >= MOTION_EVENT_THRESHOLD) {
                            // This was a drag
                            command_speech_recognition(true);
                        } else {
                            // This was a click
                            command_speech_recognition(false);
                        }
                        return true;
                    case MotionEvent.ACTION_BUTTON_RELEASE:
                        Log.d("this", "Got Button release");
                    default:
                        return true;

                }
            }

        });

        Intent intent = getIntent();
        int value = intent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);

        if (value != -1) {
            handleRequestFromJarvis(intent);
        }

        // onResume will do weather update and update_ui()
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void stopListening() {

        if (sr != null) {
            sr.stopListening();
            sr.destroy();
            sr = null;
        }

    }

    @Override
    protected void onDestroy() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor edit = prefs.edit();

        if (sr != null) {
            // Ideally should not happen, but if it does, that means Jarvis is waiting
            // for speech recognition results
            stopListening();

            resultIntent = new Intent(this, Jarvis.class);
            resultIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT);
            resultIntent.putExtra(MyReceiver.EXTRA_VALUE, (String)null);
            MyReceiver.startWakefulService(this, resultIntent);
        }

        edit.putBoolean(getString(R.string.ui_running), false);
        edit.commit();

        super.onDestroy();
    }




    private class MyRecognitionListener implements RecognitionListener {
        Context ctx;
        private boolean recognition_started = false;

        public void setNewRecognition() {
            /*
            * Since we use the same RecogntionListener across all speech recognitions
            * Use this function to refresh from outside.
             */
            recognition_started = false;
        }

        public MyRecognitionListener(Context context) {

            ctx = context;
        }

        public void onBeginningOfSpeech() {

            Log.d("this", "onBeginningOfSpeech");
            recognition_started = true;

            // Just in case readyForSpeech does not get called
            is_ready_for_speech = true;
            update_ui();

            restart_speech_recognition_timer(ctx);
        }

        public void onBufferReceived(byte[] buffer) {

            Log.d("this", "onBufferReceived");
        }

        public void onEndOfSpeech() {

            Log.d("this", "onEndOfSpeech");

            is_ready_for_speech = false;
            update_ui();

            restart_speech_recognition_timer(ctx);
        }

        public void onError(int error) {

            if (!recognition_started && error == SpeechRecognizer.ERROR_NO_MATCH) {
                /*
                * Known issue that sometimes onError(7) gets called even before
                * ReadyforSpeech. Advised on social media to ignore it
                 */
                Log.d("this", "onError: ERROR_NO_MATCH before onReady. Ignoring...");
                return;
            }
              /*
            * We have seen getting multiple events (onError after onResult)
            * Make sure we process them and poke Jarvis only once
            * User speechServiceTimer object as a check for it
             */
            if (speechServiceTimer != null) {

                is_listening = false;
                is_ready_for_speech = false;
                update_ui();

                Log.d("this", "onError: " + String.valueOf(error));
                speechServiceTimer.cancel();
                speechServiceTimer = null;
                resultIntent = new Intent(ctx, Jarvis.class);
                resultIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT);
                resultIntent.putExtra(MyReceiver.EXTRA_VALUE, (String) null);
                stopListening();
                MyReceiver.startWakefulService(ctx, resultIntent);
                //finish();
            } else {
                Log.d("this", "Ignoring onError: " + String.valueOf(error));
            }

        }

        public void onEvent(int eventType, Bundle params) {
            Log.d("this", "onEvent");
        }

        public void onPartialResults(Bundle partialResults) {
            Log.d("this", "onPartialResults");
        }

        public void onReadyForSpeech(Bundle params) {

            Log.d("this", "onReadyForSpeech");

            recognition_started = true;

            is_ready_for_speech = true;
            update_ui();

            restart_speech_recognition_timer(ctx);
        }

        public void onResults(Bundle results) {
            Log.d("this", "onResults");

            /*
            * We have seen getting multiple events (onError after onResult)
            * Make sure we process them and poke Jarvis only once
            * User speechServiceTimer object as a check for it
             */
            if (speechServiceTimer != null) {

                is_listening = false;
                is_ready_for_speech = false;
                update_ui();

                Log.d("this", "Processing onResults...");
                speechServiceTimer.cancel();
                speechServiceTimer = null;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String firstMatch = matches.get(0);
                resultIntent = new Intent(ctx, Jarvis.class);
                resultIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT);
                resultIntent.putExtra(MyReceiver.EXTRA_VALUE, firstMatch);
                stopListening();
                MyReceiver.startWakefulService(ctx, resultIntent);
                //finish();

            } else {
                Log.d("this", "Ignoring onResults...");
            }


        }

        public void onRmsChanged(float rmsdB) {
            //Log.d("this", "onRmsChanged");
        }
    }

    private void restart_speech_recognition_timer(Context ctx) {

        if (speechServiceTimer != null) {
            speechServiceTimer.cancel();
            Log.d("this", "Re-starting speechRecognition timer...");

        } else {
            Log.d("this", "Starting speechRecognition timer...");
        }

        speechServiceTimer = new Timer();
        speech_timer_seq_num++;
        speechTask = new speechServiceTask(ctx, speech_timer_seq_num);
        speechServiceTimer.schedule(speechTask, 5000);  // 5 second timer
    }

    private void start_speech_recognition(boolean offline_mode) {

        Log.d("this", "Starting speech recognition in Main activity");


        sr = SpeechRecognizer.createSpeechRecognizer(this);
        myRecognitionListener.setNewRecognition();   //runtime constructor for persistent object
        sr.setRecognitionListener(myRecognitionListener);

        /*
        * Speech recognition sometimes just hangs without listening
        * Start timer for recovery
        */
        restart_speech_recognition_timer(this);


        is_listening = true;
        update_ui();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline_mode);
        //intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
          //      "Please speak slowly and enunciate clearly.");
        sr.startListening(intent);

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void get_bill_payments() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String message = "";


        message += "CREDIT CARDS:\n";
        message += prefs.getString("icici_credit_card_bill",
                                "ICICI credit card payment records not found");
        message += "\n";
        message += prefs.getString("citibank_credit_card", "Citibank credit card payment records not found");

        message += "\n\nBILLS:\n";
        message += prefs.getString("airtel_broadband_bill", "Airtel broadband bill payment records not found");
        message += "\n";
        message += prefs.getString("meha_vodafone_bill", "Meha Vodafone bill payment records not found");
        message += "\n";
        message += prefs.getString("my_airtel_bill", "My Airtel bill payment records not found");
        message += "\n";
        message += prefs.getString("besom_bill", "BESCOM bill payment records not found");


        message += "\n\nRENT:\n";
        message += prefs.getString("house_rent", "House Rent payment records not found");



        message += "\n\n";
        message += prefs.getString("unrelated_message", "");

        TextView tv = (TextView)findViewById(R.id.hello_world);
        tv.setText(message);
    }

    private void get_parameters() {

        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Log.d("this", "Fetching parameters...");
        int mode = am.getMode();

        String output_sample_rate = null;
        String output_frames_per_buffer = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            output_sample_rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            output_frames_per_buffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        }

        boolean fixed_volume = false;
        if (Build.VERSION.SDK_INT >= 21) {
             fixed_volume = am.isVolumeFixed();
        }

        boolean a2dp_on = am.isBluetoothA2dpOn();
        boolean sco_on = am.isBluetoothScoOn();


        String message = "mode: " + mode + "\n";
        message += "output_sample_rate: " + output_sample_rate + "\n";
        message += "output_frames_per_buffer: " + output_frames_per_buffer + "\n";
        message += "a2dp: " + a2dp_on + "\n";
        message += "sco_on: " + sco_on + "\n";
        message += "fixed_volume: " + fixed_volume + "\n\n";

        message += "\n\nDumping Shared Preferences: \n";
        message += "last connected wifi SSID: " + prefs.getString(MyReceiver.EXTRA_LAST_WIFI_NAME, "<not-found>") + "\n";
        message += "wifi connection status: " + prefs.getBoolean(MyReceiver.EXTRA_LAST_WIFI_CONNECTED, false) + "\n";
        message += MyReceiver.EXTRA_LAST_WIFI_EVENT_TIMESTAMP + " " + prefs.getLong(MyReceiver.EXTRA_LAST_WIFI_EVENT_TIMESTAMP, 0) + "\n";
        message += "ride_start_time: " + prefs.getLong("ride_start_time", 0) + "\n";
        message += "ride_end_time: " + prefs.getLong("ride_end_time", 0) + "\n";
        message += getString(R.string.repeat_rejected_request) + ": " + prefs.getString(getString(R.string.repeat_rejected_request), "<Not found>") + "\n";
        message += getString(R.string.repeat_sent_message) + ": " + prefs.getString(getString(R.string.repeat_sent_message), "<Not found>") + "\n";
        message += getString(R.string.repeat_last_message) + ": " + prefs.getString(getString(R.string.repeat_last_message), "<Not found>") + "\n";
        message += "Am I in car: "  + prefs.getBoolean(MyReceiver.AM_IN_CAR, false) + "\n";
        message += "Is phone charging: " +
                 prefs.getBoolean(getString(R.string.battery_charging_state), false) + "\n";




        /*
        smsParser obj = new smsParser(null, this);
        obj.handleMessage_debug("121",
                "Your Bill payment for number 9886438800 has been processed successfully. ok");
         */

        TextView tv = (TextView)findViewById(R.id.hello_world);
        Log.d("this", "Setting parameters in textView");
        tv.setText(message);

        return;
    }

    private void clear_logging() {
        try {
            new ProcessBuilder()
                    .command("logcat", "-c")
                    .redirectErrorStream(true)
                    .start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        TextView tv = (TextView)findViewById(R.id.hello_world);
        tv.setText("");
    }

    private void show_logging() {
        try {
            Process process = Runtime.getRuntime().exec("logcat -d");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder log=new StringBuilder();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                line += "\n";
                log.append(line);
            }
            TextView tv = (TextView)findViewById(R.id.hello_world);
            tv.setText(log.toString());
        }
        catch (Exception e) {}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivityForResult(i, 0);
            return true;
        } else if (id == R.id.action_test) {
            String str = pref.getString(
                            getString(R.string.test_message), null);
            Log.d("this", "MainActivity: Speaking " + str);
            if (str != null) {
                Intent i = new Intent(this, Jarvis.class);
                i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_MESSAGE_TO_PLAY);
                i.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);
                i.putExtra(MyReceiver.EXTRA_VALUE, str);
                MyReceiver.startWakefulService(this, i);
            }
        } else if (id == R.id.action_start_sco) {
            Intent i = new Intent(this, Jarvis.class);
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_START_SCO);
            i.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);
            MyReceiver.startWakefulService(this, i);

        } else if (id == R.id.action_fetch_weather) {
            Intent i = new Intent(this, Jarvis.class);
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_FETCH_WEATHER);
            i.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);
            MyReceiver.startWakefulService(this, i);
        } else if (id == R.id.action_time_to_office) {
            Intent i = new Intent(this, Jarvis.class);
            String orig = pref.getString(getString(R.string.home_loc), null);
            String dest = pref.getString(getString(R.string.office_loc), null);
            String dest_name = "office";
            Log.d("this", "Fetching time to office: orig: " + orig +
                   " dest:" + dest + " and name: " + dest_name);
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_TIME_TO_DESTINATION);
            i.putExtra(MyReceiver.EXTRA_DEST_NAME, dest_name);
            i.putExtra(MyReceiver.EXTRA_DEST_LOCATION, dest);
            i.putExtra(MyReceiver.EXTRA_ORIG_LOCATION, orig);
            i.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);
            MyReceiver.startWakefulService(this, i);
        } else if (id == R.id.action_fetch_news) {
            Intent i = new Intent(this, Jarvis.class);
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_FETCH_NEWS);
            i.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);
            MyReceiver.startWakefulService(this, i);
        } else if (id == R.id.action_get_params) {
            get_parameters();
        } else if (id == R.id.action_get_bills) {
            get_bill_payments();
        } else if (id == R.id.action_start_SR) {
            command_speech_recognition(false);
        } else if (id == R.id.clear_logging) {
            clear_logging();
        } else if (id == R.id.show_logging) {
            show_logging();
        }


        return super.onOptionsItemSelected(item);
    }

    private void command_speech_recognition(boolean is_external) {

        if (is_external) {


            /*
            * We are going to launch Google voice, which launches another activity.
            * Schedule Jarvis to bring us back to fore-ground in some time
            *
            * ** DISABLING IT FOR NOW
            * ** as Jarvis gets hung for that time and user cannot initiate any other useful
            * command in the mean time.

            Intent i = new Intent(this, Jarvis.class);
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_POKE_ACTIVITY_BACK);
            i.putExtra(MyReceiver.EXTRA_VALUE, 60000); // 1 minute
            MyReceiver.startWakefulService(this, i);
            */
            Log.d("this", "Launching google voice assist");
            Intent gl = new Intent("android.intent.action.VOICE_ASSIST");
            startActivity(gl);

        } else {

            is_listening = true;
            update_ui();

            Intent i = new Intent(this, Jarvis.class);
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_START_VOICE_RECOGNITION);
            MyReceiver.startWakefulService(this, i);
        }
    }
}
