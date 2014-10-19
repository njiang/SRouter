package com.SRouter;

import java.util.ArrayList;
import java.util.HashMap;

class PacketSender extends Thread
{
    private BufferEntry bufferEntry;
    private int sendIndex = -1;

    public PacketSender(BufferEntry bufferEntry)
    {
        this.bufferEntry = bufferEntry;
    }

    public void run()
    {
        while (true) {
            try {
                sleep(50);
                synchronized (this.bufferEntry.getSyncObj()) {
                    HashMap<Integer, SmartDataPacket> packetBuffer = this.bufferEntry.getPacketBuffer();
                    if (packetBuffer.size() > 0) {
                        ArrayList<SendListEntry> sendList = this.bufferEntry.getSendList();
                        SendListEntry finishedentry = null;
                        for (int i = 0; i < sendList.size(); i++) {
                            SendListEntry sentry = sendList.get(i);
                            if (!sentry.isFinished()) {
                                int count = 0;
                                do {
                                    int currindex = sentry.getCurrentIndex();
                                    Integer key = new Integer(currindex);
                                    //System.out.print("About to send packet " + currindex);
                                    if (packetBuffer.containsKey(key)) {
                                        SmartDataPacket packet = packetBuffer.get(key);
                                        packet.setDestinations(sentry.getDestinations());
                                        //System.out.println("Send packet " + currindex + " for sender " + i);
                                        this.bufferEntry.getManager().getSmartRouter().forwardPacket(packet);
                                        sentry.update(bufferEntry.getMaxOffset(), bufferEntry.isDummyPacketReceived());
                                        count++;
                                    }
                                    else {
                                        //System.out.println("Packet " + currindex + " not found in the buffer!!!");
                                        break;
                                    }
                                }
                                while (count < 10);
                            }
                            else {
                                System.out.println("sending finished for ");
                                finishedentry = sentry;
                                for (int j = 0; j < sentry.getDestinations().size(); j++) {
                                    IPPortPair dest = sentry.getDestinations().get(j);
                                    System.out.println(dest.getIPAddress() + " " + dest.getPort());
                                }
                            }
                        }
                        sendList.remove(finishedentry);
                    }
                }
            }
            catch (Exception e) {
                System.out.println("Failed to send packet " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

class SendListEntry
{
    private ArrayList<IPPortPair> destinations;
    private int currentIndex = -1;
    private boolean finished = false;

    public SendListEntry(ArrayList<IPPortPair> dests, int startPoint)
    {
        this.destinations = new ArrayList<IPPortPair>(dests);
        this.currentIndex = startPoint;
    }

    public ArrayList<IPPortPair> getDestinations() { return this.destinations; }
    public int getCurrentIndex() { return this.currentIndex; }
    public boolean isFinished() { return finished; }

    public void update(int max, boolean dummyPacketReceived)
    {
        if (currentIndex >= max && dummyPacketReceived) {
            // Next we send the dummy packet
            System.out.println("Sender ready to send dummy packet...");
            currentIndex = -1;
        }
        else if (currentIndex == -1)
            this.finished = true;
        else
            currentIndex++;
    }
}

/**
 * Created by Ning Jiang on 9/4/14.
 */
public class BufferEntry {
    // A list of destinations that are waiting to join the merged stream
    private HashMap<Integer, ArrayList<IPPortPair>> waitList = new HashMap<Integer, ArrayList<IPPortPair>>();
    // A list of destinations that are being serviced by the merged stream
    private ArrayList<SendListEntry> sendList = new ArrayList<SendListEntry>();
    private HashMap<Integer, SmartDataPacket> packetBuffer;
    private Object syncObj = new Object();
    private int minOffset = -1;
    private int maxOffset = -1;
    private boolean dummyPacketReceived = false;
    private int packetBufferSize = 1000;
    private SmartBufferManager manager;
    private PacketSender packetSender;

    public BufferEntry(SmartBufferManager manager, int packetBufferSize)
    {
        this.manager = manager;
        packetBuffer = new HashMap<Integer, SmartDataPacket>(packetBufferSize);
        this.packetBufferSize = packetBufferSize;
        this.packetSender = new PacketSender(this);
        this.packetSender.start();
    }

    public SmartBufferManager getManager() { return this.manager; }
    public HashMap<Integer, SmartDataPacket> getPacketBuffer() { return this.packetBuffer; }
    public Object getSyncObj() { return syncObj; }
    public int getMinOffset() { return minOffset; }
    public int getMaxOffset() { return maxOffset; }
    public boolean isDummyPacketReceived() { return this.dummyPacketReceived; }
    public ArrayList<SendListEntry> getSendList() { return this.sendList; }


    private void handlePacketDestinations(int ioffset, ArrayList<IPPortPair> dests) {
        ArrayList<IPPortPair> unfounddests = new ArrayList<IPPortPair>();
        for (int i = 0; i < dests.size(); i++) {
            IPPortPair dest = dests.get(i);
            boolean found = false;
            for (int j = 0; j < this.sendList.size(); j++) {
                SendListEntry sentry = this.sendList.get(j);
                if (sentry.getDestinations().contains(dest))
                {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println(dest.getIPAddress() + " " + dest.getPort() + " not found in send list!");
                unfounddests.add(dest);
            }
        }
        if (unfounddests.size() > 0) {
            System.out.println("Creating a new send item for packet " + ioffset + " with " + unfounddests.size() + " destinations");
            SendListEntry sentry = new SendListEntry(unfounddests, ioffset);
            this.sendList.add(sentry);
        }
    }

    private boolean shouldIgnore(ArrayList<IPPortPair> dests)
    {
        if (dests != null && dests.size() > 0) {
            for (int i = 0; i < dests.size(); i++) {
                IPPortPair dest = dests.get(i);
                boolean found = false;
                for (int j = 0; j < this.sendList.size(); j++) {
                    SendListEntry sentry = this.sendList.get(j);
                    if (sentry.getDestinations().contains(dest)) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    return false;
            }
        }
        return true;
    }


    /**
     *
     * @param packet
     * @return false if the packet should be forwarded,
     *         true if the packet should be ignored
     */
    public boolean handlePacket(SmartDataPacket packet)
    {
        if (packet != null && packet.getDestinationIPPorts().size() > 0) {
            Integer offset = new Integer(packet.getOffset());
            int ioffset = packet.getOffset();
            ArrayList<IPPortPair> dests = packet.getDestinationIPPorts();
            boolean isDummyPacket = (packet.getOffset() == -1) ? true : false;
            if (isDummyPacket) {
                System.out.println("Dummy packet received!!!!!");
                this.dummyPacketReceived = true;
            }
            synchronized (syncObj) {
                //System.out.println("packet offset: " + ioffset + " min buffer index: " + this.minOffset
                //                    + " " + "max buffer index: " + this.maxOffset);
                if (ioffset >= 0 && ioffset < this.minOffset) {
                    if (shouldIgnore(dests)) {
                        return true;
                    }
                    Integer key = new Integer(this.minOffset);
                    Integer keyplusone = new Integer(ioffset + 1);
                    if (this.packetBuffer.containsKey(keyplusone)) {
                        // Now we can start sending packets for the dests waiting on (key + 1) in the waitList
                        SendListEntry sentry = null;
                        if (this.waitList.containsKey(keyplusone)) {
                            ArrayList<IPPortPair> existingDests = this.waitList.get(keyplusone);
                            for (int i = 0; i < dests.size(); i++) {
                                IPPortPair dest = dests.get(i);
                                if (!existingDests.contains(dest))
                                    existingDests.add(dest);
                            }
                            sentry = new SendListEntry(existingDests, ioffset + 1);
                        }
                        else {
                            sentry = new SendListEntry(dests, ioffset + 1);
                        }
                        System.out.println("Starting to send packets for " + keyplusone);
                        this.sendList.add(sentry);
                    }
                    else if (this.waitList.containsKey(key)) {
                        ArrayList<IPPortPair> list = this.waitList.get(key);
                        for (int i = 0; i < dests.size(); i++) {
                            IPPortPair dest = dests.get(i);
                            if (!list.contains(dest))
                            {
                                System.out.println("Add " + dest.getIPAddress() + " " + dest.getPort() + " to wait list " + key);
                                list.add(dest);
                            }
                        }
                    }
                    else {
                        System.out.println("Create wait list entry for " + key);
                        ArrayList<IPPortPair> list = new ArrayList<IPPortPair>();
                        list.addAll(dests);
                        this.waitList.put(key, list);
                    }
                    // let router forward the packet
                    return false;
                }

                // Below packet index is greater than minoffset
                if (this.packetBuffer.containsKey(offset)) {
                    // Packet already buffered, we add the destinations to the sendList
                    //System.out.println("****** Packet " + offset + " exists in buffer!");
                    handlePacketDestinations(ioffset, dests);
                    // Don't need to forward the packet
                    return true;
                }
                // Below is when the packet is not in the buffer
                else if (this.packetBuffer.size() >= this.packetBufferSize) {
                    // we need to remove one packet from the header of the buffer
                    //System.out.println("Need to replace packet " + this.minOffset);
                    this.packetBuffer.remove(new Integer(minOffset));
                    // Update the minoffset
                    do {
                        this.minOffset++;
                    }
                    while (!this.packetBuffer.containsKey(this.minOffset) && this.minOffset < this.maxOffset);
                    this.packetBuffer.put(new Integer(packet.getOffset()), packet);
                    if (!isDummyPacket && ioffset > this.maxOffset)
                        this.maxOffset = ioffset;
                    //System.out.println("minoffset: " + this.minOffset + " maxoffset: " + this.maxOffset);
                    handlePacketDestinations(ioffset, dests);
                    return true;
                }
                // Below is when packet buffer is not full
                else if (packetBuffer.size() == 0) {
                    if (isDummyPacket) {
                        // Save the dummy packet
                        System.out.println("Packet buffer is empty, dummy packet saved");
                        // keep forwarding the packet to clean up the send list along the route
                        return false;
                    }
                    else if (sendList.size() == 0) {
                        System.out.println("Packet buffer and send list are both empty");
                        for (int i = 0; i < dests.size(); i++) {
                            System.out.println(dests.get(i).getIPAddress() + " " + dests.get(i).getPort());
                        }
                        SendListEntry sentry = new SendListEntry(dests, ioffset);
                        sendList.add(sentry);
                        packetBuffer.put(offset, packet);
                        this.minOffset = this.maxOffset = ioffset;
                        System.out.println("minoffset: " + this.minOffset + " maxoffset: " + this.maxOffset);
                        return true;
                    }
                    else {
                        System.out.println("Packet buffer is empty, but send list is not...");
                        this.handlePacketDestinations(ioffset, dests);
                        return true;
                    }
                }
                else {
                    //System.out.println("Packet buffer is not empty and packet not buffered...");
                    packetBuffer.put(offset, packet);
                    if (ioffset > this.maxOffset)
                        this.maxOffset = ioffset;
                    //System.out.println("minoffset: " + this.minOffset + " maxoffset: " + this.maxOffset);
                    this.handlePacketDestinations(ioffset, dests);
                    return true;
                }
            }
        }
        return true;
    }

    public boolean handleRequest(SmartRequest request)
    {
        System.out.println("Handling request from " + request.getSourceIPPort().getIPAddress() + " " + request.getSourceIPPort().getPort());
        boolean result = false;
        synchronized (this.syncObj) {
            if (this.minOffset == 0) {
                ArrayList<IPPortPair> dests = new ArrayList<IPPortPair>();
                dests.add(request.getSourceIPPort());
                handlePacketDestinations(0, dests);
                result = true;
            }
        }
        return result;
    }
}
