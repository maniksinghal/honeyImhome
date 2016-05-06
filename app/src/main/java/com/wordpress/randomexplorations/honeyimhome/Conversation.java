package com.wordpress.randomexplorations.honeyimhome;

/**
 * Created by maniksin on 5/6/16.
 * This class tracks the conversation between the user and
 * Jarvis through voice commands
 */
public class Conversation {

    private int state = CONVERSATION_NOT_RUNNING;

    public static final int CONVERSATION_NOT_RUNNING = 0;
    public static final int CONVERSATION_TYPE_OPEN = 1;  // no state as such
    public static final int CONVERSATION_TYPE_RECEIVED_SMS = 2;

}
