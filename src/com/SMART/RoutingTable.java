package com.SMART;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by Ning Jiang on 9/5/14.
 */
public class RoutingTable {
    // A hashmap of all the nodes (including the video server)
    HashMap<String, Vertex> nodes = new HashMap<String, Vertex>();
    HashMap<String, ArrayList<String>> edges = new HashMap<String, ArrayList<String>>();

    Graph routingTable = new Graph();
    String serverIP;
    DijkstraAlgorithm dijkstraAlgorithm;

    public RoutingTable()
    {

    }

    public void setServerIP(String serverIP) { this.serverIP = serverIP; }
    public String getServerIP() { return serverIP; }

    // Edge is directed
    public void addEdge(Vertex src, Vertex dest, int weight)
    {
        if (src != null && dest != null) {
            if (!nodes.containsKey(src.getId())) {
                nodes.put(src.getId(), src);
                routingTable.addVertex(src);
            }
            if (!nodes.containsKey(dest.getId())) {
                nodes.put(dest.getId(), dest);
                routingTable.addVertex(dest);
            }
            Edge e = new Edge((src.getId() + "->" + dest.getId()), src, dest, weight);
            routingTable.addEdge(e);
            if (edges.containsKey(src.getId())) {
                ArrayList<String> list = edges.get(src.getId());
                if (!list.contains(dest.getId()))
                    list.add(dest.getId());
            }
            else {
                ArrayList<String> list = new ArrayList<String>();
                list.add(dest.getId());
                edges.put(src.getId(), list);
            }
        }
    }

    public void runDijkstra(Vertex v) {
        if (v != null) {
            if (dijkstraAlgorithm == null) {
                dijkstraAlgorithm = new DijkstraAlgorithm(this.routingTable);
            }
            dijkstraAlgorithm.execute(v);
        }
    }

    public LinkedList<Vertex> getShortestPath(Vertex v) {
        // Note that the first node of the path is always
        // the source node itself
        if (v != null && dijkstraAlgorithm != null)
            return dijkstraAlgorithm.getPath(v);
        return null;
    }

    public boolean isNeighboringNode(String src, String dest) {
        return (src.equals(dest) || (edges.containsKey(src) && edges.get(src).contains(dest)));
    }

    public ArrayList<String> getNeighboringNodes(String src)
    {
        if (edges.containsKey(src))
            return edges.get(src);
        return null;
    }
}
