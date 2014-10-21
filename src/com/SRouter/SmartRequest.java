package com.SRouter;

/**
 * Created by Ning Jiang on 9/4/14.
 */
public class SmartRequest extends SmartPacket {
    String command = null;
    private int videoId = 0;

    public SmartRequest(int vid, IPPortPair srcAddr, IPPortPair destAddr, String command)
    {
        this.videoId = vid;
        this.myType = PacketType.REQUEST;
        this.sourceIPPort = srcAddr;
        this.destinationIPPorts.add(destAddr);
        this.command = command;
    }

    public int getVideoId() { return videoId; }
    public String getCommand() { return command; }
}
