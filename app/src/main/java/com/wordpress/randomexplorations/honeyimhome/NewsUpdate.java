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
        String item = null;

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
                Log.d("this", "Parsing next News element.., depth: " + depth);

                /*
                * Expecting following format
                * ...
                * <item>
                *     <title>News title</title>
                *     ...
                *     <description>News description</description> => description may be a link
                * </item>
                 */
                String name = parser.getName();
                //Log.d("this", "Got start-tag: " + name);
                if (name.equals("item")) {
                    // Top news is in <title>....</title>, but the <title> tags
                    // should be picked only after <item> has started.
                    start_parsing = true;
                } else if (name.equals("title") && start_parsing) {
                    // <title>.....</title>
                    if (parser.next() == XmlPullParser.TEXT) {
                        item = parser.getText();
                        Log.d("this", "Got news: " + item);

                        // Convert
                        // Family equally guilty, imprison with corrupt officer: Court
                        // to
                        // Family equally guilty, imprison with corrupt officer, SAYS Court
                        item = item.replace(":", ", says ");

                        news.add(item);
                    }
                } else if (name.equals("description") && start_parsing) {
                    int next_event = parser.next();
                    if (next_event == XmlPullParser.TEXT) {
                        String desc = parser.getText();
                        if (desc.contains("http")) {
                            // Description is not textual, but a link, ignore it
                        } else {
                            news.add(desc);
                        }
                    } else if (next_event == XmlPullParser.END_TAG) {
                        depth--;
                    }
                }
            }
            in.close();
        } catch (Exception e) {
            Log.d("this", "parseResponse exception: " + e.getMessage());
        } finally {

        }

    }

    @Override
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
                Log.d("this", "Checking and disconnecting URL connection");
                urlConnection.disconnect();
            }
            Log.d("this", "Disconnected url connection");
        }

        return null;
    }

    @Override
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

            Log.d("this", "Playing news list");

            jarvis.processMessagesIntent(list);
        } else {
            message = "Could not contact news service";

            List<String> mlist = new ArrayList();
            mlist.add(MyReceiver.EXTRA_IS_NEWS_UPDATE);

            Log.d("this", "News-fetch: " + message);
            jarvis.processIntent(message, mlist);
        }


        return;
    }
}