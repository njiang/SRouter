package com.SRouter;

import java.util.HashMap;

/**
 * Created by Ning Jiang on 9/4/14.
 */
public class SmartBufferManager {
    SmartRouter myRouter = null;
    //
    HashMap<String, SmartPacket> packetBuffer = new HashMap<String, SmartPacket>();

    public SmartBufferManager(SmartRouter sr)
    {
        myRouter = sr;
    }

    public void processPacket(SmartPacket packet) {

    }
}
