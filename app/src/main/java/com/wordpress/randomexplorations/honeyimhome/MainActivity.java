package com.wordpress.randomexplorations.honeyimhome;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.MenuItem;
import android.widget.TextView;
import android.speech.RecognizerIntent;

import java.util.HashMap;
import java.util.Locale;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {

    private static final int VOICE_RECOGNITION_REQUEST = 0x0101;
    private MyRecognitionListener myRecognitionListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myRecognitionListener = new MyRecognitionListener(this);

        TextView tv = (TextView)findViewById(R.id.hello_world);
        tv.setText("Hit Settings to make changes");

        Intent intent = getIntent();
        int value = intent.getIntExtra("purpose", -1);
        tv.setText("Activity started with value: " + String.valueOf(value));

        if (value == MyReceiver.EXTRA_PURPOSE_START_VOICE_RECOGNITION) {
            /*
            * Jarvis (re)launched MainActivity to launch voice recognition
            */
            start_speech_recognition();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }


    private class MyRecognitionListener implements RecognitionListener {
        Context ctx;

        public MyRecognitionListener(Context context) {
            ctx = context;
        }

        public void onBeginningOfSpeech() {
            Log.d("this", "onBeginningOfSpeech");
        }

        public void onBufferReceived(byte[] buffer) {
            Log.d("this", "onBufferReceived");
        }

        public void onEndOfSpeech() {
            Log.d("this", "onEndOfSpeech");
        }

        public void onError(int error) {
            Log.d("this", "onError: " + String.valueOf(error));
            Intent intent = new Intent(ctx, Jarvis.class);
            intent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT);
            intent.putExtra(MyReceiver.EXTRA_VALUE, (String)null);
            MyReceiver.startWakefulService(ctx, intent);
            finish();

        }

        public void onEvent(int eventType, Bundle params) {
            Log.d("this", "onEvent");
        }

        public void onPartialResults(Bundle partialResults) {
            Log.d("this", "onPartialResults");
        }

        public void onReadyForSpeech(Bundle params) {
            Log.d("this", "onReadyForSpeech");
        }

        public void onResults(Bundle results) {
            Log.d("this", "onResults");
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String firstMatch = matches.get(0);
            Intent intent = new Intent(ctx, Jarvis.class);
            intent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT);
            intent.putExtra(MyReceiver.EXTRA_VALUE, firstMatch);
            MyReceiver.startWakefulService(ctx, intent);
            finish();

        }

        public void onRmsChanged(float rmsdB) {
            //Log.d("this", "onRmsChanged");
        }
    }
    private void start_speech_recognition() {

        SpeechRecognizer sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(myRecognitionListener);


        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        //intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
          //      "Please speak slowly and enunciate clearly.");
        sr.startListening(intent);
        //startActivityForResult(intent, VOICE_RECOGNITION_REQUEST);

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
        message += prefs.getString("bsnl_bill", "BSNL bill payment records not found");
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String firstMatch = null;
        if (requestCode == VOICE_RECOGNITION_REQUEST) {
            if (resultCode == RESULT_OK) {
                ArrayList<String> matches = data
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                firstMatch = matches.get(0);
            }

            Intent intent = new Intent(this, Jarvis.class);
            intent.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT);
            intent.putExtra(MyReceiver.EXTRA_VALUE, firstMatch);
            MyReceiver.startWakefulService(this, intent);
            finish();

            /* Run the match through VoCI
            VoCI voci = new VoCI(this);
            String action = voci.execute(firstMatch);

            firstMatch += "\nAction=" + action;
            firstMatch += "\nGroup_count=" + String.valueOf(voci.groupCount);
            if (voci.groupCount >= 1) {
                firstMatch += "\narg1=" + voci.arg1;
            }
            if (voci.groupCount >= 2) {
                firstMatch += "\narg2=" + voci.arg2;
            }

            TextView tv = (TextView)findViewById(R.id.hello_world);
            tv.setText(firstMatch);
            */
        }
    }

    private void get_parameters() {

        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
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
        message += "last connected wifi SSID: " + prefs.getString(MyReceiver.EXTRA_LAST_WIFI_NAME, "<not-found>") + "\n";
        message += "wifi connection status: " + prefs.getBoolean(MyReceiver.EXTRA_LAST_WIFI_CONNECTED, false) + "\n";

        /*
        smsParser obj = new smsParser(null, this);
        obj.handleMessage_debug("121",
                "Your Bill payment for number 9886438800 has been processed successfully. ok");
         */

        TextView tv = (TextView)findViewById(R.id.hello_world);
        tv.setText(message);

        return;
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
            //start_speech_recognition();
            Intent i = new Intent(this, Jarvis.class);
            i.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_START_VOICE_RECOGNITION);
            //i.putExtra(MyReceiver.EXTRA_FORCE_PLAY, true);
            MyReceiver.startWakefulService(this, i);
        }


        return super.onOptionsItemSelected(item);
    }
}
