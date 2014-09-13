package com.SRouter;

/**
 * Created by Ning Jiang on 9/5/14.
 */
public interface ISmartRouting {
    // Return the next hop given a destination IP,
    SmartRoute nextHop(String destinationIP, SmartRoutingContext context);
}
