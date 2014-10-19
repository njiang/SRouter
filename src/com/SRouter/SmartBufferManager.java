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

    public boolean processRequest(SmartRequest request) {
        if (request != null) {
            Integer vid = request.getVideoId();
            if (this.packetBuffers.containsKey(vid)) {
                System.out.println("Packet buffer checking for servicing video...");
                BufferEntry bufferEntry = this.packetBuffers.get(vid);
                return bufferEntry.handleRequest(request);
            }
            else
                return false;
        }
        return true;
    }
}
