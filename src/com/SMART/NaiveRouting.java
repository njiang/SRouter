package com.SMART;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

/**
 * Created by Ning Jiang on 9/5/14.
 */
public class NaiveRouting implements ISmartRouting {
    RoutingTable routingTable;
    String myIP;

    public NaiveRouting(String myIP, String configFile)
    {
        // Read the network graph from a configuration file
        this.myIP = myIP;
        routingTable = new RoutingTable();
        try {
            FileReader fileReader = new FileReader(configFile);
            BufferedReader reader = new BufferedReader(fileReader);
            do {
                String line = reader.readLine();
                if (line == null)
                    break;

                String adjancencies = reader.readLine();
                if (adjancencies == null)
                    break;

                String[] splitted = adjancencies.split(" ");
                for (int i = 0; i < splitted.length; i++)
                    routingTable.addEdge(line, splitted[i]);
            }
            while (true);

            fileReader.close();
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
        return null;
    }
}
