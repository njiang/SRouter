package com.SRouter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Ning Jiang on 9/4/14.
 */
public class BufferEntry {
    HashMap<String, String> destinationIPs;
    List packetList;

    public BufferEntry(String[] destinationIPs, int packetBufferSize)
    {
        packetList = Collections.synchronizedList(new ArrayList(packetBufferSize));
        for (int i = 0; i < destinationIPs.length; i++)
            this.destinationIPs.put(destinationIPs[i], destinationIPs[i]);
    }

    public boolean destinationExists(String destinationIP)
    {
        return (destinationIPs.containsKey(destinationIP));
    }

    public void addDestination(String destinationIP)
    {
        if (destinationIP != null && destinationIP.length() > 0)
            this.destinationIPs.put(destinationIP, destinationIP);
    }
}
