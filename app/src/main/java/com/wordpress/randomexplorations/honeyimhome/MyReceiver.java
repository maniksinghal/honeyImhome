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
    public static final String EXTRA_IS_WEATHER_UPDATE =
            "com.wordpress.randomexplorations.honeyimhome.weather_update";
    public static final String EXTRA_IS_NEWS_UPDATE =
            "com.wordpress.randomexplorations.honeyimhome.news_update";

    // Intent does not have a wakelock
    // Used by internally generated intents in Jarvis
    public static final String EXTRA_NON_WAKEFUL = "com.wordpress.randomexplorations.honeyimhome.non_wakeful";

    // Wifi management
    public static final String EXTRA_LAST_WIFI_NAME = "com.wordpress.randomexplorations.honeyimhome.last_wifi_name";
    // boolean flag to check if wifi was last connected or not
    public static final String EXTRA_LAST_WIFI_CONNECTED = "com.wordpress.randomexplorations.honeyimhome.last_wifi_state";
    public static final String EXTRA_LAST_WIFI_EVENT_TIMESTAMP =
            "com.wordpress.randomexplorations.honeyimhome.last_wifi_event_timestamp";


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

    public static final String AM_IN_CAR = "com.wordpress.randomexplorations.honeyimhome.am_in_car";

    private Context thisContext = null;


    public MyReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        thisContext = context;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String action = intent.getAction();

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ||
                BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            handle_bluetooth_actions(context, intent, prefs);
        } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            handle_sms_actions(context, intent, prefs);
        } else if (WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION.equals(action)) {
            handle_wifi_actions(context, intent, prefs);
        }
    }

    private void handle_wifi_actions(Context context, Intent intent, SharedPreferences prefs) {

        Calendar cal = Calendar.getInstance();
        long time_in_mins = cal.get(Calendar.DAY_OF_MONTH) * 24 * 60 +
                cal.get(Calendar.HOUR_OF_DAY) * 60 +
                cal.get(Calendar.MINUTE);

        boolean connected = intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false);
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

            //String ssid = info.getSSID();
            String ssid = null;
            try {
                // Wait upto 10 seconds
                int i;
                for (i = 0; i < 20; i++) {
                    Thread.sleep(500);
                    WifiManager wManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
                    ssid = wManager.getConnectionInfo().getSSID();
                    if (ssid != null && !ssid.equals("0x") && !ssid.isEmpty()) {
                        break;
                    }
                }

                if (i == 20) {
                    // What the hell!!
                    ssid = "Timed-out retrieving SSID";
                }

                Log.d("this", "Waited for " + i*500 + " milliseconds to get: " + ssid);

            } catch (Exception e) {
                Log.d("this", "Broadcast receiver thread could not sleep for long!! " + e.getMessage());
            }



            // ssid is returned in quotes if it can be decoded as UTF-8, "office" instead of office
            // Remove the quotes if present
            if (ssid.startsWith("\"") && ssid.endsWith("\"")){
                ssid = ssid.substring(1, ssid.length()-1);
            }

            ed.putString(EXTRA_LAST_WIFI_NAME, ssid);
            ed.commit();
            Log.d("this", "Some wifi enabled now: " + ssid);
        }

        // Right now there is no need of passing this event to Jarvis.

    }

    private void handle_sms_actions(Context context, Intent intent, SharedPreferences prefs) {

        String action = intent.getAction();
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            return;
        }

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
                    message = currentMessage.getDisplayMessageBody();
                    Log.d("this", "senderNum: "+ senderNum + "; message: " + message);

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
        i.putExtra(EXTRA_PURPOSE, EXTRA_PURPOSE_MESSAGE_TO_PLAY);
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
