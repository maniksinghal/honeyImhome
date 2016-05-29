package com.wordpress.randomexplorations.honeyimhome;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.MenuItem;
import android.widget.Button;
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
    private boolean offline_mode = false;
    private MyRecognitionListener myRecognitionListener;

    private Intent resultIntent = null;

    private Timer speechServiceTimer = null;
    private speechServiceTask speechTask = null;

    private void handleRequestFromJarvis(Intent intent) {
        int value = intent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);

        if (value == MyReceiver.EXTRA_PURPOSE_START_VOICE_RECOGNITION) {
            Log.d("this", "MainActivity invoked for speech recognition");
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            offline_mode = intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
            start_speech_recognition();
        }
    }


    public class speechServiceTask extends TimerTask {

        private Context ctx;

        public speechServiceTask(Context cont) {
            ctx = cont;
        }

        public void run() {

            /*
            * Timer expired while waiting for speech-recognition service
            * Treat it as - user didn't speak for now
            */
            Log.d("this", "speechService Timer expired. Cleaning up...");
            resultIntent = new Intent(ctx, Jarvis.class);
            resultIntent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT);
            resultIntent.putExtra(MyReceiver.EXTRA_VALUE, (String)null);
            //MyReceiver.startWakefulService(ctx, resultIntent);
            finish();


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
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_layout);

        myRecognitionListener = new MyRecognitionListener(this);

        TextView tv = (TextView)findViewById(R.id.hello_world);
        tv.setMovementMethod(new ScrollingMovementMethod());

        TextView weatherView = (TextView)findViewById(R.id.weatherView);
        weatherView.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {
                startWeatherUpdate();
            }

        });

        Button bt = (Button)findViewById(R.id.GoButton);
        bt.setEnabled(true);
        bt.setOnClickListener(new Button.OnClickListener() {

            public void onClick(View bt) {
                command_speech_recognition();
            }
        });

        startWeatherUpdate();

        Intent intent = getIntent();
        int value = intent.getIntExtra(MyReceiver.EXTRA_PURPOSE, -1);

        if (value != -1) {
            handleRequestFromJarvis(intent);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void stopListening() {
        Button bt = (Button)findViewById(R.id.GoButton);

        if (sr != null) {
            sr.stopListening();
            sr.destroy();
            sr = null;
        }

        bt.setEnabled(true);
    }

    @Override
    protected void onDestroy() {

        if (sr != null) {
            Log.d("this", "Stopping listening");
            sr.stopListening();
            sr.destroy();
        }

        super.onDestroy();
    }




    private class MyRecognitionListener implements RecognitionListener {
        Context ctx;

        public MyRecognitionListener(Context context) {
            ctx = context;
        }

        public void onBeginningOfSpeech() {

            Log.d("this", "onBeginningOfSpeech");
            restart_speech_recognition_timer(ctx);
        }

        public void onBufferReceived(byte[] buffer) {

            Log.d("this", "onBufferReceived");
        }

        public void onEndOfSpeech() {

            Log.d("this", "onEndOfSpeech");
            restart_speech_recognition_timer(ctx);
        }

        public void onError(int error) {

              /*
            * We have seen getting multiple events (onError after onResult)
            * Make sure we process them and poke Jarvis only once
            * User speechServiceTimer object as a check for it
             */
            if (speechServiceTimer != null) {
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
        speechTask = new speechServiceTask(ctx);
        speechServiceTimer.schedule(speechTask, 5000);  // 5 second timer
    }

    private void start_speech_recognition() {
        Button bt = (Button)findViewById(R.id.GoButton);

        bt.setEnabled(false);

        Log.d("this", "Starting speech recognition in Main activity");
        sr.setRecognitionListener(myRecognitionListener);

        /*
        * Speech recognition sometimes just hangs without listening
        * Start timer for recovery
        */
        restart_speech_recognition_timer(this);



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
        message += MyReceiver.AM_IN_CAR + ": " + prefs.getBoolean(MyReceiver.AM_IN_CAR, false) + "\n";




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
            command_speech_recognition();
        } else if (id == R.id.clear_logging) {
            clear_logging();
        } else if (id == R.id.show_logging) {
            show_logging();
        }


        return super.onOptionsItemSelected(item);
    }

    private void command_speech_recognition() {
        Button bt = (Button)findViewById(R.id.GoButton);
        Intent i = new Intent(this, Jarvis.class);

        bt.setEnabled(false);

        i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_START_VOICE_RECOGNITION);
        MyReceiver.startWakefulService(this, i);
    }
}
