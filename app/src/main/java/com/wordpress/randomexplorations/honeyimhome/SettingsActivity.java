package com.wordpress.randomexplorations.honeyimhome;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SettingsActivity extends PreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    SettingsFragment sf = null;


    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);
            this.getPreferenceScreen().setTitle("Settings");
        }

        /*
   * Update list of paired devices in the settings list
    */
        public void update_bt_list() {

            getFragmentManager().executePendingTransactions();
            ListPreference lp = (ListPreference)this.findPreference(getString(R.string.paired_devices));

            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

            List<String> entries = new ArrayList<String>();

            for(BluetoothDevice bt : pairedDevices) {
                entries.add(bt.getName());
                Log.d("this", "Adding " + bt.getName() + ": " + bt.getAddress() + "to the list");
            }

            final CharSequence[] entry_seq = entries.toArray(new CharSequence[entries.size()]);
            final CharSequence[] value_seq = entries.toArray(new CharSequence[entries.size()]);

            lp.setEntries(entry_seq);
            lp.setEntryValues(value_seq);
        }

        public void update_view(Context context) {

            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            ListPreference lp = (ListPreference)this.findPreference(getString(R.string.paired_devices));

            Map<String,?> keys = pref.getAll();

            for(Map.Entry<String,?> entry : keys.entrySet()){
                Preference pr = this.findPreference(entry.getKey());
                if (pr instanceof EditTextPreference) {
                    EditTextPreference etp = (EditTextPreference)pr;
                    pr.setSummary(etp.getText());
                }
            }

            lp.setSummary(pref.getString(getString(R.string.paired_devices), "ERROR!!"));
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_settings);

        // Display the fragment as the main content.
        sf = new SettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, sf)
                .commit();

        sf.update_bt_list();
        sf.update_view(this.getApplicationContext());
    }

    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        Preference pref = this.findPreference(key);
        if (pref instanceof EditTextPreference) {
            EditTextPreference etp = (EditTextPreference) pref;
            pref.setSummary(etp.getText());
        }
    }
}
