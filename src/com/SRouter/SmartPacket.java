package com.SRouter;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Ning Jiang on 9/4/14.
 */

enum PacketType {
    REQUEST,
    DATA
}

public class SmartPacket implements Serializable {
    PacketType myType;
    IPPortPair sourceIPPort;
    ArrayList<IPPortPair> destinationIPPorts = new ArrayList<IPPortPair>();


    public SmartPacket() {}

    public SmartPacket(PacketType type, IPPortPair addr, IPPortPair[] destAddrs)
    {
        myType = type;
        this.sourceIPPort = addr;
        if (destAddrs != null) {
            for (int i = 0; i < destAddrs.length; i++) {
                destinationIPPorts.add(destAddrs[i]);
            }
        }
    }

    public PacketType getType() { return myType; }
    public IPPortPair getSourceIPPort() { return sourceIPPort; }
    public ArrayList<IPPortPair> getDestinationIPPorts() { return this.destinationIPPorts; }
    public void setSourceIPPort(IPPortPair src) {this.sourceIPPort = src;}

    public void setDestinations(ArrayList<IPPortPair> destinations) {
        if (destinations != null) {
            this.destinationIPPorts.clear();
            for (int i = 0; i < destinations.size(); i++) {
                this.destinationIPPorts.add(destinations.get(i));
            }
        }
    }

    public static SmartPacket ReadPacket(ObjectInputStream objis)
    {
        if (objis == null)
            return null;

        try {
            //int numBytes = objis.readInt();
            //if (numBytes < 0)
            //    return null;

            //byte[] data = new byte[numBytes];
            return (SmartPacket)objis.readObject();
        }
        catch (Exception e) {
            objis = null;
            System.out.println("Failed to read data from socket " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
