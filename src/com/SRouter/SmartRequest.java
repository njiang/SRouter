package com.SRouter;

/**
 * Created by Ning Jiang on 9/4/14.
 */
public class SmartRequest extends SmartPacket {
    String command = null;

    public SmartRequest(IPPortPair srcAddr, IPPortPair destAddr, String command)
    {
        this.myType = PacketType.REQUEST;
        this.sourceIPPort = srcAddr;
        this.destinationIPPorts.add(destAddr);
        this.command = command;
    }

    public String getCommand() { return command; }
}
