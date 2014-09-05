package com.SMART;

/**
 * Created by Ning Jiang on 9/5/14.
 */
public class SmartDataPacket extends SmartPacket {
    byte[] data;
    int length;

    public SmartDataPacket(IPPortPair srcAddr, IPPortPair[] destinationAddrs, byte[] data, int length)
    {
        super(PacketType.DATA, srcAddr, destinationAddrs);
        this.data = data;
        this.length = length;
    }

    public byte[] getData() { return data; }
    public int getLength() { return length; }
}
