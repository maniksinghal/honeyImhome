package com.wordpress.randomexplorations.honeyimhome;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;

/**
 * Created by maniksin on 9/24/16.
 */
public class smartomeSocket extends iotDevice {

    private static final int SMARTOME_SOCKET_PORT = 6002;

    public smartomeSocket(String id, String addr) {
        deviceId = id;
        deviceAddr = addr;
    }

    public int refresh_status() {
        try {
            Socket sock = new Socket(deviceAddr, SMARTOME_SOCKET_PORT);
            PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
            InputStream in = sock.getInputStream();

            // Send status request
            String status_cmd = "##0041{\"app_cmd\":\"12\",\"imei\":\"" + deviceId + "\",\"SubDev\":\"00\",\"seq\":\"26\"}&&";
            Log.d("this", "Smartome_socket: Sending status cmd: " + status_cmd);
            out.println(status_cmd);

            // Read response, looking for:
            //##00a4{"wifi_cmd":"12","SubDev":"00","onoff":[{"on":"0","on1":"0","dtm":"0","ntm":"","on2":"0","sk":"0"}],"vol":"0","cur":"0","pow":"0","eng":"0","ver":"1.1.5","suc":"0"}&&
            byte[] buf = new byte[6];
            in.read(buf);

            String response_code = new String(buf, Charset.forName("UTF-8"));

            Log.d("this", "Smartome_socket: Got status response code: " + response_code);
            if (response_code.contains("##00a4")) {
                // Read valid response, read rest of the packet
                buf = new byte[166];
                in.read(buf);

                String response_payload = new String(buf, Charset.forName("UTF-8"));
                Log.d("this", "Smartome socket: status response payload: " + response_payload);
                return iotDevice.IOT_DEVICE_STATUS_AVAILABLE;
            }

            in.close();
            out.close();
            sock.close();

            return iotDevice.IOT_DEVICE_STATUS_REMOVED;

        } catch (Exception e) {
            Log.d("this", "Exception during smartomeSocket refresh_status: " + e.getMessage());
            return iotDevice.IOT_DEVICE_STATUS_REMOVED;
        }

    }

}
