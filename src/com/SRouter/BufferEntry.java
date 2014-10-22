package com.SRouter;

import java.util.ArrayList;
import java.util.HashMap;

class MergeEntry
{
    private IPPortPair parent;
    private int mergePoint;

    public MergeEntry(IPPortPair parent, int mergePoint)
    {
        this.parent = parent;
        this.mergePoint = mergePoint;
    }

    public IPPortPair getParent() { return this.parent; }
    public void setParent(IPPortPair parent) { this.parent = parent; }
    public int getMergePoint() { return this.mergePoint; }
    public void setMergePoint(int mergePoint) { this.mergePoint = mergePoint; }
}


/**
 * Created by Ning Jiang on 9/4/14.
 */
public class BufferEntry {
    private HashMap<IPPortPair, Integer> latestPacketList = new HashMap<IPPortPair, Integer>();
    private HashMap<IPPortPair, Integer> mergePoints = new HashMap<IPPortPair, Integer>();
    private HashMap<Integer, SmartDataPacket> packetBuffer;
    private Object syncObj = new Object();
    private int minOffset = -1;
    private int maxOffset = -1;
    private boolean dummyPacketReceived = false;
    private int packetBufferSize = 1000;
    private SmartBufferManager manager;
    private boolean firstIgnore = true;
    private boolean firstMerge = true;

    public BufferEntry(SmartBufferManager manager, int packetBufferSize)
    {
        this.manager = manager;
        // TODO
        // We need to allocate buffer for edge routers to order packets
        //packetBuffer = new HashMap<Integer, SmartDataPacket>(packetBufferSize);
        this.packetBufferSize = packetBufferSize;
    }

    public SmartBufferManager getManager() { return this.manager; }
    public HashMap<Integer, SmartDataPacket> getPacketBuffer() { return this.packetBuffer; }
    public Object getSyncObj() { return syncObj; }
    public int getMinOffset() { return minOffset; }
    public int getMaxOffset() { return maxOffset; }
    public boolean isDummyPacketReceived() { return this.dummyPacketReceived; }

    // Returns true if the merge point is updated so location key should be merged to
    // the current stream of targets
    private boolean updateMergePoint(IPPortPair key, ArrayList<IPPortPair> targets, Integer offset)
    {
        if (offset.intValue() < 0) {
            return false;
        }

        boolean foundParentStream = false;
        for (int i = 0; i < targets.size(); i++) {
            IPPortPair target = targets.get(i);
            if (!key.equals(target) && !mergePoints.containsKey(target)) {
                foundParentStream = true;
                break;
            }
        }
        if (foundParentStream) {
            if (!mergePoints.containsKey(key)) {
                System.out.println("Merge point for " + key.getIPAddress() + " " + key.getPort() + ": " + offset.intValue());
                mergePoints.put(key, offset.intValue());
            }
        }
        return foundParentStream;
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

            ArrayList<IPPortPair> toBeSent = new ArrayList<IPPortPair>(dests);
            if (isDummyPacket) {
               // We need to clean up the latestPacketList
               for (int i = 0; i < dests.size(); i++) {
                   // Clean up the tables for the client of the dummy packet
                   this.latestPacketList.remove(dests.get(0));
                   this.mergePoints.remove(dests.get(0));
               }
            }
            else {
                for (int i  = 0; i < dests.size(); i++) {
                    IPPortPair dest = dests.get(i);
                    // Record the latest packet offset for that dest
                    if (!this.latestPacketList.containsKey(dest) && ioffset == 0)
                        this.latestPacketList.put(dest, 0);
                    else if (this.latestPacketList.containsKey(dest)) {
                        if (this.mergePoints.containsKey(dest)
                            && ioffset >= this.mergePoints.get(dest).intValue())
                        {
                            // We do not modify latest packet count if the packet is beyond
                            // the merge point.  This is to prevent cyclic merging since sometimes
                            // the mergee's packets might go faster then the source. And we just
                            // ignore this dest
                            toBeSent.remove(dest);
                        }
                        else if (ioffset == this.latestPacketList.get(dest).intValue() + 1)
                            this.latestPacketList.put(dest, ioffset);
                        else {
                            // This could because the router receives a merged packet
                            // before it receives the packets prior to the merge point
                            // so it is fine.

                            //System.out.println("Out of order packet " + ioffset
                            //        + " for " + dest.getIPAddress() + " " + dest.getPort()
                            //        + " latest value " + this.latestPacketList.get(dest).intValue());
                        }
                    }
                    else {
                        // this is an out of order packet
                        System.out.println("Received out of order packet " + ioffset
                                + " for " + dest.getIPAddress() + " " + dest.getPort());
                    }
                }

                // If this packet has no destinations to send, we ignore it
                if (toBeSent.size() == 0)
                    return true;

                ArrayList<IPPortPair> cloned = new ArrayList<IPPortPair>(toBeSent);
                for (int i = 0; i < cloned.size(); i++)
                {
                    IPPortPair dest = cloned.get(i);
                    if (mergePoints.containsKey(dest) && ioffset >= mergePoints.get(dest).intValue()) {
                        // We already hit the merge point, we can stop sending
                        if (ioffset == mergePoints.get(dest).intValue())
                            System.out.println("^^^^Removing " + dest.getIPAddress() + " " + dest.getPort() + " at " + ioffset
                                + " for merging redundancy");
                        toBeSent.remove(dest);
                    }
                }

                for (IPPortPair key : this.latestPacketList.keySet()) {
                    int latestsent = this.latestPacketList.get(key).intValue();
                    if (!toBeSent.contains(key)) {
                        if (latestsent < ioffset) {
                            // We can merge with key
                            //System.out.println("Add recipient for merging" + key.getIPAddress()
                            //        + " " + key.getPort() + " at " + ioffset);
                            if (updateMergePoint(key, dests, offset))
                                toBeSent.add(key);
                        }
                    }
                    else {
                        if (ioffset > latestsent) {
                            // we can merge with key
                            //System.out.println("Recipient " + key.getIPAddress() + " " + key.getPort() + " already merged.");
                            updateMergePoint(key, dests, offset);
                        }
                    }
                }

            }

            if (toBeSent.size() == 0) {
                /*System.out.println("Packet " + ioffset + " ignored for ");
                for (int i = 0; i < dests.size(); i++) {
                    System.out.println(dests.get(i).getIPAddress() + " " + dests.get(i).getPort());
                } */
                return true;
            }
            /*if (toBeSent.size() > dests.size()) {
                System.out.println("Packet " + ioffset + " merged ");
                for (int i = 0; i < toBeSent.size(); i++) {
                    System.out.println(toBeSent.get(i).getIPAddress() + " " + toBeSent.get(i).getPort());
                }
            } */
            packet.setDestinations(toBeSent);
            return false;
        }
        return true;
    }
}
