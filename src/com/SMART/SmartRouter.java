package com.SMART;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

class NeighboringRouterStarter extends Thread
{
    private String routerIP;
    private int port;
    private SmartRouter smartRouter;

    public NeighboringRouterStarter(SmartRouter sr, String routerIP, int port)
    {
        this.routerIP = routerIP;
        this.port = port;
        this.smartRouter = sr;
    }

    public void run()
    {
        int retries = 0;
        Socket smartNodeSocket = null;
        while (smartNodeSocket == null && retries < 5) {
            try {
                smartNodeSocket = new Socket(routerIP, port);
            }
            catch (Exception e) {
                System.out.println("Failed to connect to neighboring router " + routerIP + " " + e.getMessage());
            }
            if (smartNodeSocket != null)
                break;
            try {
                sleep(5000); //  wait a little bit and connect again
            }
            catch (Exception e) {
                System.out.println("Waiting on connecting to neighboring router error " + e.getMessage());
                break;
            }
            retries++;
        }
        if (smartNodeSocket != null) {
            this.smartRouter.setNeighboringRouter(routerIP, smartNodeSocket);
        }
    }
}

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
    private ConcurrentHashMap<String, Socket> neighborSockets = new ConcurrentHashMap<String, Socket>();
    private ConcurrentHashMap<String, ObjectOutputStream> neighborOutputStreams
            = new ConcurrentHashMap<String, ObjectOutputStream>();
    private SmartBufferManager smartBufferManager;
    private NaiveRouting routingModule;
    private String myIP;
    private boolean retryVideoServerSocket = false;

    public SmartRouter(String[] args)
    {
        try {
            myIP = InetAddress.getLocalHost().getHostAddress();
        }
        catch (Exception e) {
            System.out.println("Failed to get local IP address");
        }

        // Read neighboring nodes from configuration file
        if (args.length >= 1)
            routingModule = new NaiveRouting(myIP, args[0]);

        //neighborIPs = new String[1];
        //neighborIPs[0] = "**127.0.0.1"; // prefix ** means it's the video server
        ArrayList<String> neighborIPs = routingModule.getNeighboringRouters(myIP);

        try {
            clientFacingTCPServer = new ClientFacingTCPServer(this, Smart_Client_Facing_Port);
            clientFacingTCPServer.start();

            // Listen to neighboring routers
            routerTCPServer = new SmartRouterTCPServer(this, Smart_Node_Port);
            routerTCPServer.start();

            if (routingModule.isNeighboringToServer()) {

                videoServerSocket = new Socket(routingModule.getServerIP(), Server_Port);
                videoServerOutputStream = new ObjectOutputStream(videoServerSocket.getOutputStream());
            }

            // Establish connections to neighboring routers
            for (int i = 0; i < neighborIPs.size(); i++) {
                String addr = neighborIPs.get(i);
                if (!addr.equals(myIP))
                {
                    NeighboringRouterStarter neighboringRouterStarter = new NeighboringRouterStarter(this, addr, Smart_Node_Port);
                    neighboringRouterStarter.start();
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void setNeighboringRouter(String IPAddr, Socket socket)
    {
        if (socket != null && IPAddr != null) {
            System.out.println("Set neighboring router info for " + IPAddr);
            if (!neighborSockets.containsKey(IPAddr)) {
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    if (oos != null) {
                        this.neighborSockets.put(IPAddr, socket);
                        this.neighborOutputStreams.put(IPAddr, oos);
                    }
                }
                catch (Exception e) {
                    System.out.println("Failed to add neighboring socket for " + IPAddr);
                }
            }
        }
    }

    public void run()
    {
        try {
            while(true) {
                try {

                    if (videoServerInputStream == null && videoServerSocket != null)
                        videoServerInputStream = new ObjectInputStream(videoServerSocket.getInputStream());
                    if (videoServerInputStream != null) {
                        SmartPacket packet = (SmartPacket)videoServerInputStream.readObject();
                        if (packet.getType() == PacketType.DATA) {
                            // Process by Smart Router
                            this.handlePacket(packet);

                        }
                    }
                    if (videoServerInputStream == null)  {
                        sleep(5000);
                        if (retryVideoServerSocket) {
                            videoServerSocket = new Socket(this.routingModule.getServerIP(), this.Server_Port);
                            videoServerOutputStream = new ObjectOutputStream(videoServerSocket.getOutputStream());
                            System.out.println("Reconnected to the video server!");
                        }
                    }
                }
                catch (Exception e) {
                    System.out.println("Failed to read packet from video server " + e.getMessage());
                    videoServerInputStream = null;
                    videoServerSocket = null;
                    retryVideoServerSocket = true;
                }
            }
        }
        catch (Exception e) {
            System.out.println("Main thread failed " + e.getMessage());
        }
    }

    /**
     * Processes packets received from client apps and neighboring routers
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
                        try {
                            videoServerOutputStream.writeObject(packet);
                        }
                        catch (Exception e0) {
                            System.out.println("Failed to send to video server " + e0.getMessage());
                            this.videoServerInputStream = null;
                            this.videoServerSocket = null;
                            this.retryVideoServerSocket = true;
                        }
                    }
                    else {
                        // Send request packet to the neighboring node that is closest to the video server
                        this.forwardPacket(packet);
                    }
                }
            }
            else if (packet.getType() == PacketType.DATA) {
                // This is a data packet from other routers

                // First let the client facing server to decide whether the packet should be
                // forwarded to one of the connected client apps
                clientFacingTCPServer.handleDataPacket((SmartDataPacket)packet);

                // TODO
                // process by Buffer manager

                // TODO
                // For now forward to the next hop. Once the Buffer manager is implemented,
                // this should be commented out
                this.forwardPacket(packet);
            }
        }
        catch (Exception e) {
            System.out.println("Error handling packet " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void forwardPacket(SmartPacket packet) {
        // We need to extract each destination IP and find all the next hops and then forward
        // the packet to each next hop (note there might be duplications)
        HashMap<String, ArrayList<IPPortPair>> routingDecision =  new HashMap<String, ArrayList<IPPortPair>>();

        if (packet != null && packet.getDestinationIPPorts() != null) {
            for (int i = 0; i < packet.getDestinationIPPorts().size(); i++) {
                IPPortPair dest = packet.getDestinationIPPorts().get(i);
                SmartRoute path = this.routingModule.nextHop(dest.getIPAddress(), null);
                if (path != null && path.getPath().size() > 1)
                {
                    String nextHop = path.getPath().get(1).getId();
                    System.out.println("Next hop for " + dest.getIPAddress() + ": " + nextHop);
                    if (nextHop != null && nextHop.length() > 0) {
                        ArrayList<IPPortPair> decisionlist = null;
                        if (!routingDecision.containsKey(nextHop)) {
                            decisionlist = new ArrayList<IPPortPair>();
                            routingDecision.put(nextHop, decisionlist);
                        }
                        else
                            decisionlist = routingDecision.get(nextHop);
                        decisionlist.add(dest);
                        System.out.println("Next hop " + nextHop + " for <" + dest.getIPAddress() + ", " + dest.getPort() + ">");
                    }
                }
            }

            // Now we forward packets to next hops
            for (String key : routingDecision.keySet()) {
                ArrayList<IPPortPair> dests = routingDecision.get(key);
                packet.setDestinations(dests);
                // forward to the next hop router
                System.out.println("Forwarding to " + key);
                if (neighborOutputStreams.containsKey(key)) {
                    ObjectOutputStream oos = neighborOutputStreams.get(key);
                    if (oos != null) {
                        try {
                            oos.writeObject(packet);
                        }
                        catch (Exception e) {
                            System.out.println("Failed to forward packet to " + key);
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        SmartRouter router = new SmartRouter(args);
        router.start();
    }
}
