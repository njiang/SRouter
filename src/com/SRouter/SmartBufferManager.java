package com.SRouter;

import java.util.HashMap;

/**
 * Created by Ning Jiang on 9/4/14.
 */
public class SmartBufferManager {
    private SmartRouter myRouter = null;
    private int bufferCapacity = 500;
    private boolean enabled = false;
    HashMap<Integer, BufferEntry> packetBuffers = new HashMap<Integer, BufferEntry>();
    private HashMap<IPPortPair, Integer> lastOffset = new HashMap<IPPortPair, Integer>();

    public SmartBufferManager(SmartRouter sr, int capacity, boolean enabled)
    {
        myRouter = sr;
        bufferCapacity = capacity;
        this.enabled = enabled;
    }

    public SmartRouter getSmartRouter() { return myRouter; }
    public boolean isEnabled() { return this.enabled; }

    /**
     *
     * @param packet
     * @return false if the packet should be forwarded,
     *         true if the packet should be ignored
     */
    public boolean processPacket(SmartDataPacket packet) {
        if (packet != null) {
            if (!this.enabled)
                return false;

            // Debug purpose
            /*IPPortPair dest = packet.getDestinationIPPorts().get(0);
            if (lastOffset.containsKey(dest)) {
                int latest = lastOffset.get(dest).intValue();
                if (packet.getOffset() == latest + 1)
                    lastOffset.put(dest, new Integer(latest + 1));
                else
                    System.out.println("Out of order packet: " + packet.getOffset() + " " + latest);
            }
            else if (packet.getOffset() != 0) {
                System.out.println("Out of order packet: " + packet.getOffset() + " -1");
            }
            else
                lastOffset.put(dest, new Integer(0)); */

            Integer vid = new Integer(packet.getVideoId());
            if (!packetBuffers.containsKey(vid)) {
                BufferEntry entry = new BufferEntry(this, this.bufferCapacity);
                packetBuffers.put(vid, entry);
            }
            BufferEntry entry = packetBuffers.get(vid);
            return entry.handlePacket(packet);
        }
        return true;
    }


}
