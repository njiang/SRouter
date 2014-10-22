package com.SRouter;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;


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
                if (packet == null) {
                    // something is wrong with the client, we remove it from the hashmap
                    System.out.println("##### Client TCP Socket failed to read from " + this.clientIPPortPair.getIPAddress() + " "
                            + this.clientIPPortPair.getPort());
                    this.tcpServer.removeClientSocket(this.clientIPPortPair);
                }
                else  {
                    // process the packet received from client apps
                    if (packet.getType() == PacketType.REQUEST) {
                        // In the deployment scenario of EC2 nodes as router and local computer as player,
                        // we need to set the destination info to the public IP address of the client.
                        // This is because client might only have private IP addresses behind NAT,
                        // and we need to set the destination IP of the SmartRequest to its public address
                        packet.setSourceIPPort(this.clientIPPortPair);
                        System.out.println("Set request source to " + this.clientIPPortPair.getIPAddress());

                    }
                    this.tcpServer.getSmartRouter().handlePacket(packet);
                }
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
    ConcurrentHashMap<IPPortPair, ObjectOutputStream> socketMap = new ConcurrentHashMap<IPPortPair, ObjectOutputStream>();

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
                    socketAddr = SmartRouter.getMyIP();
                System.out.println("====== Received Connection from Client IP address: " + socketAddr);
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

    public boolean isClient(IPPortPair dest) {
        if (dest != null)
        {
            return this.socketMap.containsKey(dest);
        }
        return false;
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
            ArrayList<IPPortPair> processed = new ArrayList<IPPortPair>();
            if (dests != null) {
                for (int i = 0; i < dests.size(); i++) {
                    IPPortPair dest = dests.get(i);
                    //System.out.println("Client facing tcp server handling packet for " + dest.getIPAddress()
                    //        + " " + dest.getPort());
                    if (socketMap.containsKey(dest)) {
                        processed.add(dest);
                        if (packet.getOffset() >= 0) {
                            // We do not forward dummy packets to clients
                            ObjectOutputStream objos = socketMap.get(dest);
                            if (objos != null) {
                                try {
                                    this.smartRouter.increasePacketForwarded(1);
                                    objos.writeObject(packet);
                                    objos.reset();
                                    //System.out.println("Forwarded packet to client app " + dest.getIPAddress() + " " + dest.getPort());
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
                    else {
                        //System.out.println("====No socket exists for " + dest.getIPAddress() + " " + dest.getPort() + "=====");
                    }
                }

                // Remove the destination addresses that have already been processed
                for (int i = 0; i < processed.size(); i++) {
                    IPPortPair dest = processed.get(i);
                    dests.remove(dest);
                }
            }
        }
    }
}
