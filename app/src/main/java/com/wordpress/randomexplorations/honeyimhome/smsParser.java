package com.wordpress.randomexplorations.honeyimhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by maniksin on 8/23/15.
 */
public class smsParser {
    private SmsMessage message;
    private XmlResourceParser parser;
    private Context context;

    private final int FINDING_SENDER = 1;
    private final int FINDING_MESSAGE = 2;
    private final int FINDING_OTHERS = 3;

    smsParser(SmsMessage msg, Context ctx) {
        message = msg;
        parser = null;
        context = ctx;

        try {
            parser = ctx.getResources().getAssets().openXmlResourceParser("res/xml/sms_config2.xml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String find_attribute(String attr_name) {
        int attr_count = parser.getAttributeCount();
        int i = 0;

        for (i = 0; i < attr_count; i++) {
            if (attr_name.equals(parser.getAttributeName(i))) {
                return parser.getAttributeValue(i);
            }
        }

        return null;
    }

    public void handleMessage() {
        String message_sender = message.getDisplayOriginatingAddress();
        String message_body = message.getDisplayMessageBody();
        handleMessage_internal(message_sender, message_body);
    }

    public void handleMessage_debug(String sender, String body) {
        handleMessage_internal(sender, body);
    }

    private void handleMessage_internal(String message_sender, String message_body) {

        String log = null;
        String transaction = null;
        String tag = null;
        int action = FINDING_SENDER;
        DateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        Date today= new Date();


        if (parser == null) {
            Log.d("this", "Null parser!!");
            return;
        }

        Log.d("this", "Parsing xml...");

        try {
            parser.next();
            int event_type = parser.getEventType();

            while (event_type != XmlPullParser.END_DOCUMENT) {

                if (action == FINDING_SENDER) {
                    if (event_type == XmlPullParser.START_TAG) {
                        tag = parser.getName();
                        if (tag.equals("Sender")) {
                            String name = find_attribute("name");
                            if (name == null) {
                                Log.d("this", "Could not find Sender name in XML!!");
                                break;
                            }

                            if (message_sender.matches(name)) {
                                // Name matched, now to find the message body
                                Log.d("this", "Matched sender name with: " + name);
                                action = FINDING_MESSAGE;
                            }

                        }

                    }
                } else if (action == FINDING_MESSAGE) {
                    if (event_type == XmlPullParser.START_TAG) {
                        tag = parser.getName();
                        if (tag.equals("message")) {
                            String message_text = find_attribute("text");
                            if (message_text == null) {
                                Log.d("this", "Could not find message text for Sender: " +
                                    message_sender);
                                break;
                            }

                            if (message_body.matches(message_text)) {
                                // Sender and message match, collect the other essentials
                                Log.d("this", "Found message: " + message_text);
                                action = FINDING_OTHERS;
                            }

                        }
                    } else if (event_type == XmlPullParser.END_TAG) {
                        tag = parser.getName();
                        if (tag.equals("Sender")) {
                            // Sender Tag ended witout finding matching message
                            Log.d("this", "Could not find matching message for sender: "
                              + message_sender);
                            break;
                        }
                    }
                } else if (action == FINDING_OTHERS) {
                    if (event_type == XmlPullParser.START_TAG) {
                        tag = parser.getName();
                        if (tag.equals("transaction")) {
                            transaction = find_attribute("type");
                            if (transaction == null) {
                                Log.d("this", "Could not find transaction type in XML for sender: " +
                                message_sender + "body: " + message_body);
                                break;
                            }
                        } else if (tag.equals("log")) {
                            String log_action = find_attribute("action");
                            if (log_action != null && log_action.equals("message_text")) {
                                log = message_body;
                            } else {
                                log = find_attribute("text");
                                if (log == null) {
                                    Log.d("this", "Could not find log in XML for sender: " +
                                            message_sender + "body: " + message_body);
                                    break;
                                }
                                String pattern = "#DATE";
                                log = log.replaceAll(pattern, df.format(today));
                            }
                        }
                    } else if (event_type == XmlPullParser.END_TAG) {
                        Log.d("this", "Got end tag");
                        tag = parser.getName();
                        if (tag.equals("message")) {
                            // End of message tag, we hopefully collected all 'others'
                            break;
                        }
                        Log.d("this", "End of end tag");
                    }
                }

                event_type = parser.next();

            }
        } catch (Exception e) {
            Log.d("this", "Some exception occured: " + e.getMessage());
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor ed = prefs.edit();

        if (action == FINDING_OTHERS && log != null && transaction != null) {
            Log.d("this", "Parsing successful: " + log);
            ed.putString(transaction, log);
        } else {
            // Some other message, store for debug
            Log.d("this", "Received unrelated message from: " + message_sender);
            ed.putString("unrelated_message", "Received from: " + message_sender + " message: '"
                    + message_body + "' on " + df.format(today));
        }

        ed.commit();
    }

}
