package com.SMART;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Ning Jiang on 9/5/14.
 */
public class RoutingTable {
    HashMap<String, ArrayList<String>> routingTable = new HashMap<String, ArrayList<String>>();
    String serverIP;
    boolean neighborToServer = false;

    public RoutingTable()
    {

    }

    public String getServerIP() { return serverIP; }

    public void addEdge(String src, String dest)
    {
        if (src != null && dest != null && !src.isEmpty() && !dest.isEmpty()) {
            String addr = dest;
            if (dest.startsWith("**")) {
                this.serverIP = dest.substring(2);
                addr = this.serverIP;
            }
            if (!routingTable.containsKey(src)) {
                ArrayList<String> edges = new ArrayList<String>();
                edges.add(addr);
                routingTable.put(src, edges);
            }
            else {
                ArrayList<String> edges = routingTable.get(src);
                if (!edges.contains(dest))
                    edges.add(addr);
            }
        }
    }

    public boolean isNeighboringNode(String src, String dest) {
        return (routingTable.containsKey(src) && routingTable.get(src).contains(dest));
    }

    public ArrayList<String> getNeighboringNodes(String src)
    {
        if (routingTable.containsKey(src))
            return routingTable.get(src);
        return null;
    }
}
