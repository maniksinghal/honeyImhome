package com.wordpress.randomexplorations.honeyimhome;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;

public class MyReceiver extends WakefulBroadcastReceiver {

    public static final String MESSAGE_TO_PLAY = "com.wordpress.randomexplorations.honeyimhome.play_message";
    public static final String AM_IN_CAR = "com.wordpress.randomexplorations.honeyimhome.am_in_car";


    public MyReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String action = intent.getAction();

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ||
                BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            handle_bluetooth_actions(context, intent, prefs);
        } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            handle_sms_actions(context, intent, prefs);
        }
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
        String paired_phone = prefs.getString("phone_number", "0000");
        if (!PhoneNumberUtils.compare(paired_phone, senderNum)) {
            Log.d("this", "Message from " + senderNum + ". Discarded as not matching with " + paired_phone);
            return;
        }

        boolean am_in_car = prefs.getBoolean(AM_IN_CAR, false);
        if (!am_in_car) {
            // Not in car, can read message by myself.
            return;
        }

        // Time to play it
        Intent i = new Intent(context, MessagePlayService.class);
        i.putExtra(MESSAGE_TO_PLAY, message);
        startWakefulService(context, i);

        return;
    }

    private void handle_bluetooth_actions(Context context, Intent intent, SharedPreferences prefs) {

        // Find which bluetooth device is there in our shared preferences
        String bt_device = prefs.getString("paired_devices", "0000");
        Log.d("this", "Stored device is: " + bt_device);

        String message = null;
        BluetoothDevice bt = null;
        boolean connected = false;
        String action = intent.getAction();
        if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
            //Do something with bluetooth device connection
            bt = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            message = prefs.getString("connect_message", "connected");
            Log.d("this", "Some device connected: " + bt.getName());
            connected = true;

        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
            //Do something with bluetooth device disconnection
            bt = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            message = prefs.getString("disconnect_message", "disconnected");
            Log.d("this", "Some device disconnected: " + bt.getName());
        }

        if (bt != null && message != null && bt_device.equals(bt.getName())) {
            // Found a valid intent

            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(AM_IN_CAR, connected);
            ed.commit();

            // Check if its correct time of day to respond to these events
            Calendar cal = Calendar.getInstance();
            int hours = cal.get(Calendar.HOUR_OF_DAY);
            int day = cal.get(Calendar.DAY_OF_WEEK);
            switch(day) {
                case Calendar.SUNDAY:
                case Calendar.SATURDAY:
                    // Weekends.. ignore
                    Log.d("this", "Its a weekend.. false alarm");
                    return;
            }
            if (hours < 16) {
                // who leaves office before 4pm??
                Log.d("this", "Its not evening.. " + hours + "...false alarm");
                return;
            }

            Log.d("this", "Its " + hours + " on " + day + " day");

            String phone = prefs.getString("phone_number", "0000");
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone, null, message, null, null);
            Log.d("this", "Sent SMS for message: '" + message + "' to " + phone + ".");
        }

    }
}
