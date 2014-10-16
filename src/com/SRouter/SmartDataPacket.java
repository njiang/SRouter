package com.SRouter;

/**
 * Created by Ning Jiang on 9/5/14.
 */
public class SmartDataPacket extends SmartPacket {
    private int offset; // offset of the packet in the stream
    private byte[] data;
    private int length;
    private int streamIndex;

    public SmartDataPacket(IPPortPair srcAddr, IPPortPair[] destinationAddrs, byte[] data, int length, int sindex, int offset)
    {
        super(PacketType.DATA, srcAddr, destinationAddrs);
        this.offset = offset;
        this.data = data;
        this.length = length;
        this.streamIndex = sindex;
    }

    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }
    public byte[] getData() { return data; }
    public int getLength() { return length; }
    public int getStreamIndex() { return streamIndex; }
}
