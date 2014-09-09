package com.SMART;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;


class ClientPacketHandler extends Thread
{
    ClientFacingTCPServer tcpServer;
    Socket clientSocket;
    IPPortPair clientIPPortPair;

    public ClientPacketHandler(ClientFacingTCPServer server, Socket clientSocket, IPPortPair ipinfo)
    {
        this.tcpServer = server;
        this.clientSocket = clientSocket;
        this.clientIPPortPair = ipinfo;
    }

    public void run()
    {
        try {
            if (this.clientSocket != null) {
                ObjectInputStream objis = new ObjectInputStream(this.clientSocket.getInputStream());
                SmartPacket packet = SmartPacket.ReadPacket(objis);
                if (packet == null)
                    // something is wrong with the client, we remove it from the hashmap
                    this.tcpServer.removeClientSocket(this.clientIPPortPair);
                else
                    // process the packet received from client apps
                    this.tcpServer.getSmartRouter().handlePacket(packet);
            }
        }
        catch (Exception e) {
            System.out.println("Failed to process client facing socket " + e.getMessage());
            e.printStackTrace();
            this.tcpServer.removeClientSocket(this.clientIPPortPair);
        }
    }
}



/**
 * Created by Ning Jiang on 9/4/14.
 * This is a tcp server running on a SMART router that interacts with client applications.
 * It accepts requests and passes data packets to the clients.
 *
 */
public class ClientFacingTCPServer extends Thread
{
    ServerSocket serverSocket;
    SmartRouter smartRouter;
    HashMap<IPPortPair, ObjectOutputStream> socketMap = new HashMap<IPPortPair, ObjectOutputStream>();

    public ClientFacingTCPServer(SmartRouter sr, int port)
    {
        this.smartRouter = sr;
        try {
            serverSocket = new ServerSocket(port);
        }
        catch (Exception e) {
            System.out.println("Failed to create client facing server socket " + e.getMessage());
            e.printStackTrace();
        }
    }

    public SmartRouter getSmartRouter() { return smartRouter; }

    public void run()
    {
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                String socketAddr = clientSocket.getInetAddress().getHostAddress();
                // If client is on the same machine, we use host address instead
                // since we use host address in routing table
                if (socketAddr.equals("127.0.0.1"))
                    socketAddr = InetAddress.getLocalHost().getHostAddress();
                IPPortPair pair = new IPPortPair(socketAddr, clientSocket.getPort());
                ObjectOutputStream objos = new ObjectOutputStream(clientSocket.getOutputStream());
                // Save the client socket for later reference
                socketMap.put(pair, objos);
                ClientPacketHandler clientPacketHandler = new ClientPacketHandler(this, clientSocket, pair);
                clientPacketHandler.start();
            }
        }
        catch (Exception e) {
            System.out.println("Error processing client facing server socket " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void removeClientSocket(IPPortPair pair)
    {
        if (socketMap.containsKey(pair))
            socketMap.remove(pair);
    }

    /**
     * Handles data packets received from either the video server or other routers
     * @param packet
     */
    public void handleDataPacket(SmartDataPacket packet) {
        if (packet != null) {
            ArrayList<IPPortPair> dests = packet.getDestinationIPPorts();
            if (dests != null) {
                for (int i = 0; i < dests.size(); i++) {
                    IPPortPair dest = dests.get(i);
                    if (socketMap.containsKey(dest)) {
                        ObjectOutputStream objos = socketMap.get(dest);
                        if (objos != null) {
                            try {
                                objos.writeObject(packet);
                                //objos.reset();
                                System.out.println("Forwarded packet to client app " + dest.getIPAddress() + " " + dest.getPort());
                            }
                            catch (Exception e) {
                                System.out.println("Failed to process packet for " + dest.getIPAddress() + " " + dest.getPort() + " " + e.getMessage());
                                e.printStackTrace();
                                if (this.socketMap.containsKey(dest))
                                    this.socketMap.remove(dest);
                            }
                        }
                    }
                }
            }
        }
    }
}
