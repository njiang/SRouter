package com.SMART;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
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
    private ObjectInputStream videoServerInputStream;
    private HashMap<String, Socket> neighborSockets = new HashMap<String, Socket>();
    private SmartBufferManager smartBufferManager;
    private NaiveRouting routingModule;
    private String myIP;

    public SmartRouter(String[] args)
    {
        try {
            myIP = InetAddress.getLocalHost().getHostAddress();
        }
        catch (Exception e) {
            System.out.println("Failed to get local IP address");
        }

        // TODO
        // Read neighboring nodes from configuration file
        if (args.length >= 1)
            routingModule = new NaiveRouting(myIP, args[0]);

        //neighborIPs = new String[1];
        //neighborIPs[0] = "**127.0.0.1"; // prefix ** means it's the video server
        ArrayList<String> neighborIPs = routingModule.getNeighboringRouters(myIP);

        try {
            clientFacingTCPServer = new ClientFacingTCPServer(this, Smart_Client_Facing_Port);
            clientFacingTCPServer.start();

            routerTCPServer = new SmartRouterTCPServer(this, Smart_Node_Port);
            routerTCPServer.start();

            if (routingModule.isNeighboringToServer()) {

                videoServerSocket = new Socket(routingModule.getServerIP(), Server_Port);
                videoServerOutputStream = new ObjectOutputStream(videoServerSocket.getOutputStream());
            }

            for (int i = 0; i < neighborIPs.size(); i++) {
                String addr = neighborIPs.get(i);
                if (!addr.equals(myIP))
                {
                    Socket smartNodeSocket = new Socket(addr, Smart_Node_Port);
                    neighborSockets.put(addr, smartNodeSocket);
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
                try {
                //sleep(5000);
                    if (videoServerInputStream == null && videoServerSocket != null)
                        videoServerInputStream = new ObjectInputStream(videoServerSocket.getInputStream());
                    if (videoServerInputStream != null) {
                        SmartPacket packet = (SmartPacket)videoServerInputStream.readObject();
                        if (packet.getType() == PacketType.DATA) {
                            // Let the client facing server to decide whether the packet should be
                            // forwarded to one of the client apps
                            clientFacingTCPServer.handleDataPacket((SmartDataPacket)packet);

                            // TODO
                            // Process by Smart Buffer Manager
                        }
                    }
                }
                catch (Exception e) {
                    System.out.println("Failed to read packet from video server " + e.getMessage());
                }
            }
        }
        catch (Exception e) {
            System.out.println("Main thread failed " + e.getMessage());
        }
    }

    /**
     * Processes packets received from client apps
     * If it's a request, forward it to the server if the smart router is neighboring to the video server;
     * otherwise forward to the smart router closer to the server based on the routing algorithm
     * @param packet packet received from client apps
     *
     */
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
                        // TODO
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
