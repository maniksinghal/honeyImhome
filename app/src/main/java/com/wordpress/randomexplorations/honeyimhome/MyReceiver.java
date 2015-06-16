package com.wordpress.randomexplorations.honeyimhome;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;

public class MyReceiver extends BroadcastReceiver {
    public MyReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        // Find which bluetooth device is there in our shared preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String bt_device = prefs.getString("paired_devices", "0000");
        Log.d("this", "Stored device is: " + bt_device);

        String message = null;
        BluetoothDevice bt = null;
        String action = intent.getAction();
        if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
            //Do something with bluetooth device connection
            bt = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            message = prefs.getString("connect_message", "connected");
            Log.d("this", "Some device connected: " + bt.getName());

        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
            //Do something with bluetooth device disconnection
            bt = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            message = prefs.getString("disconnect_message", "disconnected");
            Log.d("this", "Some device disconnected: " + bt.getName());
        }

        if (bt != null && message != null && bt_device.equals(bt.getName())) {
            // Found a valid intent

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
