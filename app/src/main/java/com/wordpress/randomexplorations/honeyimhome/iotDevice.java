package com.wordpress.randomexplorations.honeyimhome;

/**
 * Created by maniksin on 9/24/16.
 */
public abstract class iotDevice {

    public static final int IOT_DEVICE_STATUS_UNKNOWN = 0;
    public static final int IOT_DEVICE_STATUS_AVAILABLE = 1;
    public static final int IOT_DEVICE_STATUS_REMOVED = 2;
    public static final int IOT_DEVICE_STATUS_BUSY = 3;

    public String deviceId = null;
    public String deviceAddr = null;

    public abstract int refresh_status();



    public iotDevice() {}

}
