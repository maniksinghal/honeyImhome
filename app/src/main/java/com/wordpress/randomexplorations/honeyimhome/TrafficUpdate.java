package com.wordpress.randomexplorations.honeyimhome;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by maniksin on 6/20/15.
 */
public class TrafficUpdate extends AsyncTask<URL, Void, Void> {

    private String travel_time = null;
    Jarvis jarvis = null;
    private String destination_coord = null;
    private String origin_coord = null;
    private String destination_name = null;
    private String api_key = "AIzaSyAInDdnDVxHOiJFzsnlN5z_UZZ_n04g7sw";

    public TrafficUpdate(Jarvis jvs, String orig, String dest, String dest_name) {
        jarvis = jvs;

        // Lat,long values
        origin_coord = orig;
        destination_coord = dest;
        destination_name = dest_name;
    }

    public TrafficUpdate(Jarvis jvs, String orig, String dest, String dest_name, String api) {
        jarvis = jvs;

        // Lat,long values
        origin_coord = orig;
        destination_coord = dest;
        api_key = api;
        destination_name = dest_name;
    }

    /*
    * SAMPLE
    * <DistanceMatrixResponse>
    *<status>OK</status>
    *<origin_address>
    *Indra Sadan Apartment, 83/3, Goshala Rd, Garudachar Palya, Mahadevapura, Bengaluru, Karnataka 560048, India
    *</origin_address>
    *<destination_address>
    *BLOCK 12, Marathahalli - Sarjapur Outer Ring Rd, Kaverappa Layout, Kadubeesanahalli, Bengaluru, Karnataka 560103, India
    *</destination_address>
    *<row>
    *<element>
    *<status>OK</status>
    *<duration>
    *<value>1004</value>
    *<text>17 mins</text>
    *</duration>
    *<distance>
    *<value>6783</value>
    *<text>4.2 mi</text>
    *</distance>
    *<duration_in_traffic>
    *<value>1067</value>
    *<text>18 mins</text>   <=== THIS IS WHAT WE NEED
    *</duration_in_traffic>
    *</element>
    *</row>
    *</DistanceMatrixResponse>
    *
    * parser.next() shall read following in seq
    * ...
    * START_TAG(duration_in_traffic)
    * START_TAG(value) TEXT(1067) END_TAG(value)
    * START_TAG(text) TEXT(18 mins END_TAG(text)
    * END_TAG(duration in traffic)
    * http://developer.android.com/training/basics/network-ops/xml.html
    *
     */
    private void parseResponse(InputStream in) {

        XmlPullParser parser = Xml.newPullParser();
        Log.d("this", "TrafficUpdate: Parsing response");
        boolean duration_in_traffic_found = false;
        boolean next_text_is_time = false;

        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

            // Most probably pointing to first nesting tag
            //parser.require(XmlPullParser.START_TAG, null, "rss");
            int depth = 0;
            while (parser.next() != XmlPullParser.END_TAG || depth > 0) {

                int event = parser.getEventType();
                if (event == XmlPullParser.END_TAG) {
                    depth--;
                    continue;
                } else if (event == XmlPullParser.START_TAG) {
                    depth++;
                }
                //Log.d("this", "Got event: " + event + "while parsing Traffic update response");

                String name = parser.getName();

                if (event == XmlPullParser.START_TAG) {
                    if (name.equals("duration_in_traffic")) {
                        duration_in_traffic_found = true;
                        Log.d("this", "Found <duration_in_traffic> tag");
                    } else if (name.equals("text") && duration_in_traffic_found) {
                        next_text_is_time = true;
                        duration_in_traffic_found = false;
                        Log.d("this", "Found <text> tag inside <duration_in_traffic> tag");
                    }
                } else if (event == XmlPullParser.TEXT && next_text_is_time) {
                    travel_time = parser.getText();
                    next_text_is_time = false;
                    break;
                }
            }
            in.close();
        } catch (Exception e) {
            Log.d("this", "parseResponse exception: " + e.getMessage());
        } finally {

        }

    }

    protected Void doInBackground(URL... urls) {

        HttpURLConnection urlConnection = null;
        Log.d("this", "TrafficUpdate: Started...");
        try {
            //29228812
            String addr = "https://maps.googleapis.com/maps/api/distancematrix/xml?units=imperial&origins=";
            addr += origin_coord + "&destinations=" + destination_coord + "&key=" + api_key;
            addr += "&mode=driving&departure_time=now";
            Log.d("this", "TrafficUpdate: Built url: " + addr);
            URL url = new URL(addr);
            urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            parseResponse(in);
        } catch (Exception e) {
            Log.d("this", "Exception: " + e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        return null;
    }

    protected void onPostExecute(Void result) {

        String message = null;
        Calendar inst;
        if (travel_time != null) {
            // We were able to fetch traffic information
            // Travel_time is in format "<num> mins"
            int minutes = Integer.parseInt(travel_time.split(" ")[0]);
            inst = Calendar.getInstance();
            inst.add(Calendar.MINUTE, minutes);
            message = "We should reach " + destination_name +
                    " approximately by  " + inst.get(Calendar.HOUR) + " " +
                    inst.get(Calendar.MINUTE);
        } else {
            message = "Traffic update is not available.";
        }

        List<String> list = new ArrayList();
        list.add(MyReceiver.EXTRA_IS_TRAFFIC_UPDATE);
        jarvis.processIntent(message, list);

        return;
    }
}
