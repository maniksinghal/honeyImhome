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
public class NewsUpdate extends AsyncTask<URL, Void, Void> {

    private List<String> news = null;
    Jarvis jarvis = null;

    public NewsUpdate(Jarvis jvs) {
        jarvis = jvs;
        news = new ArrayList();
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
        Log.d("this", "NewsUpdate: Parsing response");
        boolean start_parsing = false;

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
                if (name.equals("item")) {
                    // Top news is in <title>....</title>, but the <title> tags
                    // should be picked only after <item> has started.
                    start_parsing = true;
                } else if (name.equals("title") && start_parsing) {
                    // <title>.....</title>
                    if (parser.next() == XmlPullParser.TEXT) {
                        String item = parser.getText();
                        Log.d("this", "Got news: " + item);
                        news.add(item);
                    }
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
        Log.d("this", "NewsUpdate: Started...");
        try {
            String addr = "http://toi.timesofindia.indiatimes.com/rssfeedstopstories.cms";
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
        ArrayList<String> list = new ArrayList<>();
        if (!news.isEmpty()) {
            list.add("Top news of the day");

            // First item
            String item = news.remove(0);
            list.add(item);

            while (!news.isEmpty()) {
                list.add(news.remove(0));

            }

            jarvis.processMessagesIntent(list);
        } else {
            message = "Could not contact news service";

            List<String> mlist = new ArrayList();
            mlist.add(MyReceiver.EXTRA_IS_NEWS_UPDATE);
            jarvis.processIntent(message, mlist);
        }


        return;
    }
}