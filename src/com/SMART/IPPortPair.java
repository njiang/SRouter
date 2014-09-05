package com.SMART;

import java.io.Serializable;

/**
 * Created by Ning Jiang on 9/4/14.
 */
public class IPPortPair implements Serializable {
    String IPAddress;
    int port;

    public IPPortPair(String IP, int port)
    {
        this.IPAddress = IP;
        this.port = port;
    }

    public String getIPAddress() { return IPAddress; }
    public int getPort() { return port; }
}
