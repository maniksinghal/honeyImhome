package com.wordpress.randomexplorations.honeyimhome;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Created by maniksin on 5/6/16.
 * Voice Command Interpreter
 */
public class VoCI {
    private Context context = null;

    public boolean match_found = false;
    public String action = null;
    public String arg1 = null;
    public String arg2 = null;
    public int groupCount = 0;
    public String user = null;

    public static final String VOCI_ACTION_INVALID = "VOCI_ACTION_INVALID";
    public static final String VOCI_ACTION_PLAY = "VOCI_ACTION_PLAY";
    public static final String VOCI_REPEAT_LAST_MESSAGE = "VOCI_REPEAT_LAST_MESSAGE";
    public static final String VOCI_SMS_TO_HUBBY = "VOCI_SMS_TO_HUBBY";
    public static final String VOCI_REPEAT_SENT_MESSAGE = "VOCI_REPEAT_SENT_MESSAGE";
    public static final String VOCI_FETCH_WEATHER = "VOCI_FETCH_WEATHER";
    public static final String VOCI_REPEAT_REJECTED_REQUEST = "VOCI_REPEAT_REJECTED_REQUEST";
    public static final String VOCI_END_SESSION = "VOCI_END_SESSION";
    public static final String VOCI_KEEP_LISTENING = "VOCI_KEEP_LISTENING";


    public VoCI(Context ctx, String name) {
        context = ctx;
        user = name;
        reset();
    }

    public void reset() {
        match_found = false;
        action = VOCI_ACTION_INVALID;
        arg1 = null;
        arg2 = null;
        groupCount = 0;
    }

    private boolean match(String command, String pattern) {
        pattern = pattern.replaceAll("#user", user);
        Pattern myPat = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher myMatcher = myPat.matcher(command);
        if (myMatcher.matches()) {
            groupCount = myMatcher.groupCount();
            if (groupCount >= 1) { arg1 = myMatcher.group(1); }
            if (groupCount >= 2) { arg2 = myMatcher.group(2); }
            return true;
        } else {
            return false;
        }
    }

    /*
    * Core logic here
     */
    public String execute(String command) {
        XmlResourceParser parser;
        String group_name = null;
        boolean got_match_tag = false;

        try {
            parser = context.getResources().getAssets().openXmlResourceParser("res/xml/voice_commands.xml");

            parser.next();
            int event_type = parser.getEventType();
            while (event_type != XmlResourceParser.END_DOCUMENT) {

                if (event_type == XmlResourceParser.START_TAG) {
                    String tag = parser.getName();
                    if (tag.equals("group")) {
                        /* New group started */
                        group_name = parser.getAttributeValue(null, "name");
                        if (group_name == null) {
                            Log.d("this", "Could not find name attribute in group tag");
                            return VOCI_ACTION_INVALID;
                        }
                    } else if (tag.equals("match")) {
                        got_match_tag = true;
                    } else if (tag.equals("action")) {
                        action = parser.getAttributeValue(null, "name");
                    }
                } else if (event_type == XmlResourceParser.END_TAG) {
                    String tag = parser.getName();
                    if (tag.equals("group")) {
                        /* Group ended */
                        group_name = null;
                        if (match_found) {
                            break;  // We found match in this group. No need to process further
                        }
                        action = null;
                    } else if (tag.equals("match")) {
                        got_match_tag = false;
                    }
                } else if (event_type == XmlResourceParser.TEXT) {
                    if (got_match_tag && group_name != null) {
                        /* We have a valid pattern to match */
                        String pattern = parser.getText();
                        if (match(command, pattern)) {
                            match_found = true;
                        }
                    }
                }

                event_type = parser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return VOCI_ACTION_INVALID;
        }

        /* Check if we found a match */
        if (group_name != null || !match_found || action == null) {
            /* Match not found or the xml ended abruptly */
            return VOCI_ACTION_INVALID;
        }

        return action;
    }
}
