package com.SRouter;

import java.io.Serializable;

/**
 * Created by Ning Jiang on 9/4/14.
 */
public class IPPortPair implements Serializable, Comparable<IPPortPair> {
    String IPAddress;
    int port;

    public IPPortPair(String IP, int port)
    {
        this.IPAddress = IP;
        this.port = port;
    }

    public String getIPAddress() { return IPAddress; }
    public int getPort() { return port; }

    public boolean equals(Object other)
    {
        IPPortPair otherpair = (IPPortPair)other;
        if (otherpair != null) {
            if (this.IPAddress.equalsIgnoreCase(otherpair.getIPAddress()) && this.port == otherpair.getPort())
                return true;
        }
        return false;
    }

    public int hashCode()
    {
        return IPAddress.hashCode() + port;
    }

    public int compareTo(IPPortPair target) {
        if (this.equals(target))
            return 0;

        int result = this.IPAddress.compareTo(target.getIPAddress());
        if (result == 0)
            result = this.port - target.port;
        return result;
    }
}
