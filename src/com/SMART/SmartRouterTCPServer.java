package com.SMART;

import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

class SmartRouterPacketHandler extends Thread
{
    SmartRouterTCPServer tcpServer;
    Socket clientSocket;

    public SmartRouterPacketHandler(SmartRouterTCPServer server, Socket clientSocket)
    {
        this.tcpServer = server;
        this.clientSocket = clientSocket;
    }

    public void run()
    {
        try {
            if (this.clientSocket != null) {
                DataInputStream dis = new DataInputStream(this.clientSocket.getInputStream());
                ObjectInputStream objis = new ObjectInputStream(this.clientSocket.getInputStream());
                while (true) {
                    SmartPacket packet = SmartPacket.ReadPacket(objis);
                    // Received a packet from neighboring router,
                    // we pass the packet to Smart Router to handle
                    this.tcpServer.getSmartRouter().handlePacket(packet);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Failed to process client facing socket " + e.getMessage());
            e.printStackTrace();
        }
    }
}

/**
 * Created by Ning Jiang on 9/4/14.
 * The TCP server that interacts with other Smart routers
 */
public class SmartRouterTCPServer extends Thread {
    ServerSocket serverSocket;
    SmartRouter smartRouter;
    HashMap<String, Socket> socketMap = new HashMap<String, Socket>();

    public SmartRouterTCPServer(SmartRouter sr, int port)
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
                socketMap.put(clientSocket.getInetAddress().getHostAddress(), clientSocket);
                SmartRouterPacketHandler clientPacketHandler = new SmartRouterPacketHandler(this, clientSocket);
                clientPacketHandler.start();
            }
        }
        catch (Exception e) {
            System.out.println("Error processing client facing server socket " + e.getMessage());
            e.printStackTrace();
        }
    }
}
