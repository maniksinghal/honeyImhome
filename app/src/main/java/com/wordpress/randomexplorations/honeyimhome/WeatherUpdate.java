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
import java.util.List;

/**
 * Created by maniksin on 6/20/15.
 */
public class WeatherUpdate extends AsyncTask<URL, Void, Void> {

    private String description = null;
    private String temperature = null;
    private String date = null;
    Jarvis jarvis = null;
    private String yahoo_woeid = null;

    public WeatherUpdate(Jarvis jvs, String woeid) {
        jarvis = jvs;
        yahoo_woeid = woeid;
    }

    /*
    * <a val=2>This is what we get</a>
    * <b>
    *     <c>okie dokie</c>
    * </b>
    *
    * parser.next() shall read following in seq
    * START_TAG(a), TEXT(a), END-TAG(a), START_TAG(b)
    * START_TAC(c), TEXT(c), END-TAG(c), END=TAG(b)
    * http://developer.android.com/training/basics/network-ops/xml.html
    *
     */
    private void parseResponse(InputStream in) {

        XmlPullParser parser = Xml.newPullParser();
        Log.d("this", "WeatherUpdate: Parsing response");

        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

            // Most probably pointing to first nesting tag
            parser.require(XmlPullParser.START_TAG, null, "rss");
            int depth = 0;
            while (parser.next() != XmlPullParser.END_TAG || depth > 0) {

                int event = parser.getEventType();
                if (event == XmlPullParser.END_TAG) {
                    depth--;
                    continue;
                } else if (event != XmlPullParser.START_TAG) {
                    continue;
                }
                depth++;

                String name = parser.getName();
                //Log.d("this", "Got start-tag: " + name);
                if (name.equals("yweather:condition")) {
                    description = parser.getAttributeValue(null, "text");
                    temperature = parser.getAttributeValue(null, "temp");
                    date = parser.getAttributeValue(null, "date");
                    Log.d("this", "Got descr: " + description + " temp: " + temperature
                      + ", date: " + date);
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
        Log.d("this", "WeatherUpdate: Started...");
        try {
            //29228812
            String addr = "http://weather.yahooapis.com/forecastrss?w=" + yahoo_woeid + "&u=c";
            Log.d("this", "Weatherupdate: Built url: " + addr);
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
        if (description != null) {
            // We were able to fetch weather
            message = "Weather today is " + description + " with temperature " + temperature
                    + " degree celcius";
        } else {
            message = "Could not contact weather service";
        }

        List<String> list = new ArrayList();
        list.add(MyReceiver.EXTRA_IS_WEATHER_UPDATE);
        jarvis.processIntent(message, list);

        return;
    }
}
