package com.wordpress.randomexplorations.honeyimhome;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import com.wordpress.randomexplorations.honeyimhome.R;

import java.util.Calendar;
import java.util.Date;

public class MyReceiver extends WakefulBroadcastReceiver {

    public static final String EXTRA_PURPOSE = "com.wordpress.randomexplorations.honeyimhome.purpose";
    public static final String EXTRA_VALUE = "com.wordpress.randomexplorations.honeyimhome.play_message";

    // flag to specify whether to store the last played message in shared preferences
    public static final String EXTRA_STORE_MSG = "com.wordpress.randomexplorations.honeyimhome.store_message";

    public static final String EXTRA_IS_WEATHER_UPDATE =
            "com.wordpress.randomexplorations.honeyimhome.weather_update";
    public static final String EXTRA_IS_NEWS_UPDATE =
            "com.wordpress.randomexplorations.honeyimhome.news_update";
    public static final String EXTRA_IS_TRAFFIC_UPDATE =
            "com.wordpress.randomexplorations.honeyimhome.traffic_update";

    // Intent does not have a wakelock
    // Used by internally generated intents in Jarvis
    public static final String EXTRA_NON_WAKEFUL = "com.wordpress.randomexplorations.honeyimhome.non_wakeful";

    // Wifi management
    public static final String EXTRA_LAST_WIFI_NAME = "com.wordpress.randomexplorations.honeyimhome.last_wifi_name";
    // boolean flag to check if wifi was last connected or not
    public static final String EXTRA_LAST_WIFI_CONNECTED = "com.wordpress.randomexplorations.honeyimhome.last_wifi_state";
    public static final String EXTRA_LAST_WIFI_EVENT_TIMESTAMP =
            "com.wordpress.randomexplorations.honeyimhome.last_wifi_event_timestamp";

    public static final String EXTRA_ORIG_LOCATION = "com.wordpress.randomexplorations.honeyimhome.orig_loc";
    public static final String EXTRA_DEST_LOCATION = "com.wordpress.randomexplorations.honeyimhome.dest_loc";
    public static final String EXTRA_DEST_NAME = "com.wordpress.randomexplorations.honeyimhome.dest_name";

    // Play the message with this intent even if not connected to car.
    // Used by internally generated messages.
    public static final String EXTRA_FORCE_PLAY = "com.wordpress.randomexplorations.honeyimhome.force_play";


    public static final int EXTRA_PURPOSE_INVALID = 0;
    public static final int EXTRA_PURPOSE_MESSAGE_TO_PLAY = 1;
    public static final int EXTRA_PURPOSE_CAR_CONNECT = 2;
    public static final int EXTRA_PURPOSE_CAR_DISCONNECT = 3;
    public static final int EXTRA_PURPOSE_START_SCO = 4;
    public static final int EXTRA_PURPOSE_FETCH_WEATHER = 5;
    public static final int EXTRA_PURPOSE_FETCH_NEWS = 6;
    public static final int EXTRA_PURPOSE_WIFI_STATE_CHANGE = 7;
    public static final int EXTRA_PURPOSE_TIME_TO_DESTINATION = 8;
    public static final int EXTRA_PURPOSE_START_VOICE_RECOGNITION = 9;
    public static final int EXTRA_PURPOSE_VOICE_RECOGNITION_RESULT = 10;
    public static final int EXTRA_PURPOSE_SMS_RECEIVED = 11;
    public static final int EXTRA_PURPOSE_CONVERSATION_RUNNING = 12;
    public static final int EXTRA_PURPOSE_POWER_STATE_CHANGED = 13;
    public static final int EXTRA_PURPOSE_SYNC_MAIN_ACTIVITY = 14;

    public static final String AM_IN_CAR = "com.wordpress.randomexplorations.honeyimhome.am_in_car";

    private Context thisContext = null;


    public MyReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        thisContext = context;
        boolean power_state_changed = false;
        boolean power_state = false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String action = intent.getAction();

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ||
                BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            handle_bluetooth_actions(context, intent, prefs);
        } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            handle_sms_actions(context, intent, prefs);
        //} else if (WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION.equals(action)) {
          //  handle_wifi_actions(context, intent, prefs);
        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);

            if (netInfo != null && netInfo.isConnected() && wifiInfo != null) {
                String ssid = wifiInfo.getSSID();
                handle_wifi_actions(context, true, prefs, ssid);
            } else if (netInfo != null && netInfo.getState() == NetworkInfo.State.DISCONNECTED) {
                handle_wifi_actions(context, false, prefs, null);
            }
        } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            handle_power_state_change(context, true);
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            handle_power_state_change(context, false);
        }

    }

    private void handle_power_state_change(Context context, boolean power_state) {
        Intent i = new Intent(context, Jarvis.class);
        i.putExtra(EXTRA_PURPOSE, EXTRA_PURPOSE_POWER_STATE_CHANGED);
        i.putExtra(EXTRA_VALUE, power_state);
        Log.d("this", "Broadcast received for battery charging state change: " + power_state);
        startWakefulService(context, i);
    }

    private void handle_wifi_actions(Context context, boolean connected, SharedPreferences prefs,
                                     String ssid) {

        Calendar cal = Calendar.getInstance();
        long time_in_mins = cal.get(Calendar.DAY_OF_MONTH) * 24 * 60 +
                cal.get(Calendar.HOUR_OF_DAY) * 60 +
                cal.get(Calendar.MINUTE);

        boolean was_connected = prefs.getBoolean(EXTRA_LAST_WIFI_CONNECTED, false);
        /*
        * When disconnecting, we get an intermediate 'connected' with invalid ssid
        * This makes us update our last connected ssid to invalid value which causes
        * malfunctioning further.
        * Ignore duplicate state updates for now..
         */
        if (connected == was_connected) {
            // Ignore this update
            return;
        }


        if (!connected) {
            Log.d("this", "Some wifi disabled");
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(EXTRA_LAST_WIFI_CONNECTED, false);
            ed.putLong(EXTRA_LAST_WIFI_EVENT_TIMESTAMP, time_in_mins);
            ed.commit();
        } else {

            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(EXTRA_LAST_WIFI_CONNECTED, true);
            ed.putLong(EXTRA_LAST_WIFI_EVENT_TIMESTAMP, time_in_mins);

            // ssid is returned in quotes if it can be decoded as UTF-8, "office" instead of office
            // Remove the quotes if present
            if (ssid.startsWith("\"") && ssid.endsWith("\"")){
                ssid = ssid.substring(1, ssid.length()-1);
            }

            ed.putString(EXTRA_LAST_WIFI_NAME, ssid);
            ed.commit();
            Log.d("this", "Some wifi enabled now: " + ssid);
        }

        // We may receive WifiManager.NETWORK_STATE_CHANGE in bulk with toggling states.
        // But it converges to the final state.
        // So if we plan to pass this intent to the service, make note of the intermediate toggling
        // states.
        Intent i = new Intent(context, Jarvis.class);
        i.putExtra(EXTRA_PURPOSE, EXTRA_PURPOSE_WIFI_STATE_CHANGE);
        startWakefulService(context, i);

    }

    /*
    private void test_speech_recognition(Context context) {
        Intent i = new Intent(context, Jarvis.class);
        i.putExtra(EXTRA_PURPOSE, EXTRA_PURPOSE_START_VOICE_RECOGNITION);
        startWakefulService(context, i);
    }
    */

    private void handle_sms_actions(Context context, Intent intent, SharedPreferences prefs) {

        String action = intent.getAction();
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            return;
        }

        //test_speech_recognition(context);

        final Bundle bundle = intent.getExtras();

        String senderNum = null;
        String message = null;

        // Extract message and phone number
        try {
            if (bundle != null) {
                final Object[] pdusObj = (Object[]) bundle.get("pdus");
                for (int i = 0; i < pdusObj.length; i++) {
                    SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
                    String phoneNumber = currentMessage.getDisplayOriginatingAddress();

                    senderNum = phoneNumber;
                    message = currentMessage.getMessageBody();
                    Log.d("this", "senderNum: "+ senderNum + "; message: " + message);

                    smsParser parser = new smsParser(currentMessage, thisContext);
                    parser.handleMessage();

                    // handle only one message
                    break;

                } // end for loop
            } // bundle is null
        } catch (Exception e) {
            Log.e("SmsReceiver", "Exception smsReceiver" +e);
        }

        if (message == null) {
            return;
        }

        // Right now we process message only from our hubby
        String paired_phone =
                prefs.getString(context.getString(R.string.hubby_phone_number), "0000");
        if (!PhoneNumberUtils.compare(paired_phone, senderNum)) {
            Log.d("this", "Message from " + senderNum + ". Discarded as not matching with " + paired_phone);
            return;
        }

        String message_to_play = null;
        String hubby = prefs.getString(thisContext.getString(R.string.hubby_name), null);
        if (hubby == null) {
            message_to_play = "Received message";
        } else {
            message_to_play = hubby + " says";
        }

        message_to_play += " " + message;

        // Time to play it
        Intent i = new Intent(context, Jarvis.class);
        i.putExtra(EXTRA_PURPOSE, EXTRA_PURPOSE_SMS_RECEIVED);
        i.putExtra(EXTRA_VALUE, message_to_play);
        startWakefulService(context, i);

        return;
    }

    private void handle_bluetooth_actions(Context context, Intent intent, SharedPreferences prefs) {

        // Find which bluetooth device is there in our shared preferences
        String bt_device = prefs.getString(thisContext.getString(R.string.paired_devices), "0000");
        Log.d("this", "Stored device is: " + bt_device);


        BluetoothDevice bt = null;
        boolean connected = false;
        String action = intent.getAction();
        if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
            //Do something with bluetooth device connection
            bt = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d("this", "Some device connected: " + bt.getName());
            connected = true;

        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
            //Do something with bluetooth device disconnection
            bt = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d("this", "Some device disconnected: " + bt.getName());
        }

        if (bt != null  && bt_device.equals(bt.getName())) {
            // Found a valid intent

            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(AM_IN_CAR, connected);
            ed.commit();

            // Prepare a default greeting message
            String greeting_message = null;
            Intent in = new Intent(context, Jarvis.class);
            if (connected) {
                in.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_CAR_CONNECT);
            } else {
                in.putExtra(MyReceiver.EXTRA_PURPOSE, MyReceiver.EXTRA_PURPOSE_CAR_DISCONNECT);
            }
            startWakefulService(context, in);
        }

    }
}
