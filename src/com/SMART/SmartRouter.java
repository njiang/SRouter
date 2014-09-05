package com.SMART;

import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;


public class SmartRouter extends Thread {
    private int Server_Port = 7999;
    private int Smart_Client_Facing_Port = 8999;
    private int Smart_Node_Port = 9999;

    private ClientFacingTCPServer clientFacingTCPServer; // tcp server interacts with client app
    private SmartRouterTCPServer routerTCPServer; // tcp server interacts with neighboring SMART nodes

    private String[] neighborIPs;
    private Socket videoServerSocket; // socket connecting to the video server if applicable
    private ObjectOutputStream videoServerOutputStream;
    private HashMap<String, Socket> neighborSockets = new HashMap<String, Socket>();
    private SmartBufferManager smartBufferManager;

    public SmartRouter(String[] args)
    {
        // TODO
        // Read neighboring nodes from configuration file
        neighborIPs = new String[1];
        neighborIPs[0] = "**127.0.0.1"; // prefix ** means it's the video server

        try {
            clientFacingTCPServer = new ClientFacingTCPServer(this, Smart_Client_Facing_Port);
            clientFacingTCPServer.start();

            routerTCPServer = new SmartRouterTCPServer(this, Smart_Node_Port);
            routerTCPServer.start();

            for (int i = 0; i < neighborIPs.length; i++) {
                if (neighborIPs[i].startsWith("**"))
                {
                    String serverIP = neighborIPs[i].substring(2);
                    videoServerSocket = new Socket(serverIP, Server_Port);
                    videoServerOutputStream = new ObjectOutputStream(videoServerSocket.getOutputStream());
                }
                else {
                    Socket smartNodeSocket = new Socket(neighborIPs[i], Smart_Node_Port);
                    neighborSockets.put(neighborIPs[i], smartNodeSocket);
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void run()
    {
        try {
            while(true) {
                sleep(5000);
            }
        }
        catch (Exception e) {
            System.out.println("Main thread failed " + e.getMessage());
        }
    }

    public void handlePacket(SmartPacket packet)
    {
        try {
            if (packet.getType() == PacketType.REQUEST) {
                SmartRequest request = (SmartRequest)packet;
                String command = request.getCommand();
                if (command.toLowerCase().contains("request")) {
                    // This is a video request
                    if (videoServerSocket != null) {
                        if (videoServerSocket != null) {
                            videoServerOutputStream.writeObject(packet);
                        }
                    }
                    else {
                        // Send request packet to the neighboring node that is closest to the video server

                    }
                }
            }
        }
        catch (Exception e) {
            System.out.println("Error handling packet " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SmartRouter router = new SmartRouter(args);
        router.start();
    }
}
