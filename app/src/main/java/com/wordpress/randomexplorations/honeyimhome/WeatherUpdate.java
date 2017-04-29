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
    Jarvis jarvis = null;
    MainActivity main = null;
    private String weather_id = null;
    private boolean called_from_jarvis = true;

    public WeatherUpdate(Jarvis jvs, String woeid) {
        jarvis = jvs;
        weather_id = woeid;
    }

    public WeatherUpdate(MainActivity ctx, String woeid) {
        called_from_jarvis = false;
        weather_id = woeid;
        main = ctx;
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
            //parser.require(XmlPullParser.START_TAG, null, "rss");
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
                if (name.equals("temperature")) {
                    description = parser.getAttributeValue(null, "text");

                    // Fetch temperature and remove fractional part
                    temperature = parser.getAttributeValue(null, "value").split(".")[0];
                    Log.d("this", "Got temperature: " + temperature);
                } else if (name.equals("weather")) {
                    description = parser.getAttributeValue(null, "value");
                    Log.d("this", "Got weather: " + description);
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
            String addr = "http://api.openweathermap.org/data/2.5/weather?units=metric" +
                    "&id=" + weather_id + "&mode=xml&APPID=0a5b7712530e6ce9c58bccef680a7fe1";
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
        String display = null;
        if (description != null) {
            // We were able to fetch weather
            message = "Weather today is " + description + " with temperature " + temperature
                    + " degree celcius";
            display = description + " (" + temperature + "\u00B0C)";
        } else {
            message = "Could not contact weather service";
            display = "Weather: <Network Error>";
        }

        if (called_from_jarvis) {
            List<String> list = new ArrayList();
            list.add(MyReceiver.EXTRA_IS_WEATHER_UPDATE);
            jarvis.processIntent(message, list);
        } else {
            // Called from Main activity
            main.updateWeather(display);
        }


        return;
    }
}
