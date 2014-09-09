package com.SMART;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by Ning Jiang on 9/5/14.
 */
public class NaiveRouting implements ISmartRouting {
    RoutingTable routingTable;
    String myIP;
    Vertex self;

    public NaiveRouting(String myIP, String configFile)
    {
        // Read the network graph from a configuration file
        this.myIP = myIP;
        routingTable = new RoutingTable();
        try {
            FileReader fileReader = new FileReader(configFile);
            BufferedReader reader = new BufferedReader(fileReader);
            do {
                String line = reader.readLine(); // source node
                if (line == null)
                    break;

                Vertex src = new Vertex(line, line);
                if (line.equals(myIP))
                    self = src;

                String adjancencies = reader.readLine(); // neighboring nodes delimited by space
                if (adjancencies == null)
                    break;

                String[] splitted = adjancencies.split(" ");
                for (int i = 0; i < splitted.length; i++) {
                    String addr = splitted[i];
                    if (splitted[i].startsWith("**")) {
                        addr = splitted[i].substring(2);
                        routingTable.setServerIP(addr);
                    }

                    Vertex dest = new Vertex(addr, addr);
                    // In Naive routing edges are undirected with unit weight
                    if (!src.equals(dest)) {
                        routingTable.addEdge(src, dest, 1);
                        routingTable.addEdge(dest, src, 1);
                    }
                }
            }
            while (true);

            fileReader.close();
            // Now we execute the dijkstra's algorithm
            //self = new Vertex("172.17.6.99", "172.17.6.99");
            routingTable.runDijkstra(self);

            // Test
            Vertex server = new Vertex(routingTable.getServerIP(), routingTable.getServerIP());
            LinkedList<Vertex> path = routingTable.getShortestPath(server);
            if (path != null) {
                for (Vertex v: path) {
                    System.out.print(v.getId() + "->");
                }
            }
            System.out.println();

            Vertex node1 = new Vertex("172.17.6.55", "172.17.6.55");
            path = routingTable.getShortestPath(node1);
            if (path != null) {
                for (Vertex v: path) {
                    System.out.print(v.getId() + "->");
                }
            }
            System.out.println();

            Vertex node2 = new Vertex("172.17.6.88", "172.17.6.88");
            path = routingTable.getShortestPath(node2);
            if (path != null) {
                for (Vertex v: path) {
                    System.out.print(v.getId() + "->");
                }
            }
            System.out.println();

            Vertex node3 = new Vertex("172.17.6.99", "172.17.6.99");
            path = routingTable.getShortestPath(node3);
            if (path != null) {
                for (Vertex v: path) {
                    System.out.print(v.getId() + "->");
                }
            }
        }
        catch (Exception e) {
            System.out.println("Failed to read configuration file " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isNeighboringToServer()
    {
        return routingTable.isNeighboringNode(myIP, routingTable.getServerIP());
    }

    public ArrayList<String> getNeighboringRouters(String src)
    {
        return routingTable.getNeighboringNodes(src);
    }

    public String getServerIP() { return routingTable.getServerIP();}

    @Override
    public SmartRoute nextHop(String destinationIP, SmartRoutingContext context) {
        Vertex vertex = new Vertex(destinationIP, destinationIP);
        LinkedList<Vertex> path = this.routingTable.getShortestPath(vertex);
        if (path == null)
            return null;

        SmartRoute route = new SmartRoute(path);
        return route;
    }
}
