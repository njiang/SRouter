package com.SMART;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;


class ClientPacketHandler extends Thread
{
    ClientFacingTCPServer tcpServer;
    Socket clientSocket;

    public ClientPacketHandler(ClientFacingTCPServer server, Socket clientSocket)
    {
        this.tcpServer = server;
        this.clientSocket = clientSocket;
    }

    public void run()
    {
        try {
            if (this.clientSocket != null) {
                //BufferedReader in = new BufferedReader(
                //        new InputStreamReader(
                //                this.clientSocket.getInputStream()));
                ObjectInputStream objis = new ObjectInputStream(this.clientSocket.getInputStream());
                SmartPacket packet = SmartPacket.ReadPacket(objis);
                this.tcpServer.getSmartRouter().handlePacket(packet);

                /*if (packet.getType() == PacketType.REQUEST) {
                    SmartRequest request = (SmartRequest)packet;
                    String command = request.getCommand();
                    if (command != null) {
                        System.out.println("Command: " + command);
                        if (command.contains("Request")) {
                            // Got a video request, we pass it to the next SMART node, or the server

                        }
                    }
                } */
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
 * This is a tcp server running on a SMART router that interacts with client applications.
 * It accepts requests and passes data packets to the clients.
 *
 */
public class ClientFacingTCPServer extends Thread
{
    ServerSocket serverSocket;
    SmartRouter smartRouter;
    HashMap<IPPortPair, Socket> socketMap = new HashMap<IPPortPair, Socket>();

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
                IPPortPair pair = new IPPortPair(clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());
                socketMap.put(pair, clientSocket);
                ClientPacketHandler clientPacketHandler = new ClientPacketHandler(this, clientSocket);
                clientPacketHandler.start();
            }
        }
        catch (Exception e) {
            System.out.println("Error processing client facing server socket " + e.getMessage());
            e.printStackTrace();
        }
    }
}
