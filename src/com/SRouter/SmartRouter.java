package com.SRouter;

import sun.net.util.IPAddressUtil;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
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
        Socket smartNodeSocket = null;
        try {
            // This thread constantly verifies whether a connection to a neighboring router is established.
            // If not, it tries to connect
            while (true) {
                if (!this.smartRouter.routerStarted(this.routerIP)) {
                    try {
                        smartNodeSocket = new Socket(routerIP, port);
                    }
                    catch (Exception e) {
                        System.out.println("Failed to connect to neighboring router " + routerIP + " " + e.getMessage());
                    }

                    if (smartNodeSocket != null) {
                        this.smartRouter.setNeighboringRouter(routerIP, smartNodeSocket);
                    }
                }
                sleep(5000);
            }
        }
        catch (Exception e) {
            System.out.println("Neighboring router starting thread failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

// This class manages connection with a neighboring video server
// If the connection is lost, it tries to reconnect
class VideoServerConnectionManager extends Thread
{
    private String serverIP;
    private int port;
    private SmartRouter smartRouter;
    ObjectInputStream vsis;

    public VideoServerConnectionManager(SmartRouter sr, String serverIP, int port)
    {
        this.serverIP = serverIP;
        this.port = port;
        this.smartRouter = sr;
    }

    public void run()
    {
        try {
            while (true) {
               if (!smartRouter.videoServerConnected(this.serverIP)) {
                   try {
                       System.out.println("Router connecting to video server " + this.serverIP);
                       Socket videoServerSocket = new Socket(this.serverIP, this.port);
                       ObjectOutputStream vsos = new ObjectOutputStream(videoServerSocket.getOutputStream());
                       vsis = new ObjectInputStream(videoServerSocket.getInputStream());
                       smartRouter.setVideoServerInfo(this.serverIP, videoServerSocket, vsos, vsis);
                       System.out.println("Successfully connected to video server " + this.serverIP);
                   }
                   catch (Exception e) {
                       //System.out.println("Failed to connect to video server " + this.serverIP + ": " + e.getMessage());
                   }
               }

               if (vsis != null) {
                   try {
                       SmartPacket packet = (SmartPacket)vsis.readObject();
                       if (packet.getType() == PacketType.DATA) {
                           // Process by Smart Router
                           this.smartRouter.handlePacket(packet);
                       }
                   }
                   catch (Exception e) {
                       System.out.println("Failed to read packet from server " + this.serverIP + " " + e.getMessage());
                       this.smartRouter.clearNeighboringVideoServer(this.serverIP);
                   }
               }
               else
                   sleep(5000);
            }
        }
        catch (Exception e) {
            System.out.println("Video server connection manager for " + this.serverIP + " failed: " + e.getMessage());
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

    // Sockets and io streams to neighboring video servers
    private ConcurrentHashMap<String, Socket> videoServerSockets = new ConcurrentHashMap<String, Socket>(); // socket connecting to the video server if applicable
    private ConcurrentHashMap<String, ObjectOutputStream> videoServerOutputStreams = new ConcurrentHashMap<String, ObjectOutputStream>();
    private ConcurrentHashMap<String, ObjectInputStream> videoServerInputStreams = new ConcurrentHashMap<String, ObjectInputStream>();

    // Sockets and output streams with neighboring routers
    private ConcurrentHashMap<String, Socket> neighborSockets = new ConcurrentHashMap<String, Socket>();
    private ConcurrentHashMap<String, ObjectOutputStream> neighborOutputStreams
            = new ConcurrentHashMap<String, ObjectOutputStream>();

    private SmartBufferManager smartBufferManager;
    private NaiveRouting routingModule;
    private String myIP;
    private int totalPacketsReceived = 0;
    private int totalPacketsSavedBySMART = 0;
    private int totalPacketsForwarded = 0;
    private boolean retryVideoServerSocket = false;

    public SmartRouter(String[] args)
    {
        try {
            myIP = getMyIP();
            System.out.println("**** Router IP: " + myIP + " ******");
        }
        catch (Exception e) {
            System.out.println("Failed to get local IP address");
        }

        // Read neighboring nodes from configuration file
        if (args.length >= 1)
            routingModule = new NaiveRouting(myIP, args[0]);

        this.smartBufferManager = new SmartBufferManager(this, routingModule.getBufferCapacity(), routingModule.getSmartEnabled());

        //neighborIPs = new String[1];
        //neighborIPs[0] = "**127.0.0.1"; // prefix ** means it's the video server
        ArrayList<String> neighborIPs = routingModule.getNeighboringRouters(myIP);

        try {
            clientFacingTCPServer = new ClientFacingTCPServer(this, Smart_Client_Facing_Port);
            clientFacingTCPServer.start();

            // Listen to neighboring routers
            routerTCPServer = new SmartRouterTCPServer(this, Smart_Node_Port);
            routerTCPServer.start();

            ArrayList<String> neighboringServers = routingModule.getServerIPs();
            if (neighboringServers != null) {
                for (String serverIP : neighboringServers) {

                    VideoServerConnectionManager vscm = new VideoServerConnectionManager(this, serverIP, Server_Port);
                    vscm.start();
                }
            }

            // Establish connections to neighboring routers
            if (neighborIPs != null) {
                for (int i = 0; i < neighborIPs.size(); i++) {
                    String addr = neighborIPs.get(i);
                    if (!addr.equals(myIP))
                    {
                        NeighboringRouterStarter neighboringRouterStarter = new NeighboringRouterStarter(this, addr, Smart_Node_Port);
                        neighboringRouterStarter.start();
                    }
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void increasePacketForwarded(int count) { this.totalPacketsForwarded += count; }

    public static String getMyIP()
    {

        try {
            String myIP = InetAddress.getLocalHost().getHostAddress();
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                if (nic.isLoopback()) {
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (IPAddressUtil.isIPv4LiteralAddress(addr.getHostAddress())) {
                            myIP = addr.getHostAddress();
                            break;
                        }
                    }
                }
                else {
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (IPAddressUtil.isIPv4LiteralAddress(addr.getHostAddress()))
                            return addr.getHostAddress();
                    }
                }
            }
            return myIP;
        }
        catch (Exception e) {
            System.out.println("Failed to get IP address " + e.getMessage());
        }
        return "127.0.0.1";
    }

    public boolean videoServerConnected(String IPAddr) {
        return (this.videoServerSockets.containsKey(IPAddr));
    }

    public void clearNeighboringVideoServer(String IPAddr) {
        if (this.videoServerSockets.containsKey(IPAddr)) {
            this.videoServerSockets.remove(IPAddr);
            this.videoServerOutputStreams.remove(IPAddr);
            this.videoServerInputStreams.remove(IPAddr);
        }
    }

    public boolean routerStarted(String IPAddr) {
        return (this.neighborOutputStreams.containsKey(IPAddr));
    }

    public void clearNeighboringRouter(String IPAddr)
    {
        if (this.neighborOutputStreams.containsKey(IPAddr)) {
            this.neighborOutputStreams.remove(IPAddr);
            this.neighborSockets.remove(IPAddr);
        }
    }

    public void setVideoServerInfo(String serverIP, Socket socket, ObjectOutputStream os, ObjectInputStream ois)
    {
        if (serverIP != null && socket != null && os != null && ois != null) {
            this.videoServerSockets.put(serverIP, socket);
            this.videoServerInputStreams.put(serverIP, ois);
            this.videoServerOutputStreams.put(serverIP, os);
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
                    sleep(5000);
                }
                catch (Exception e) {
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
            if (packet == null)
                return;

            if (packet.getType() == PacketType.REQUEST) {
                SmartRequest request = (SmartRequest)packet;
                String command = request.getCommand();
                if (command.toLowerCase().contains("request")) {
                    // This is a video request
                    IPPortPair dest = request.getDestinationIPPorts().get(0);
                    System.out.println("Received request to server " + dest.getIPAddress());
                    if (this.videoServerConnected(dest.getIPAddress())) {
                        try {
                            ObjectOutputStream oos = this.videoServerOutputStreams.get(dest.getIPAddress());
                            if (oos != null)
                                oos.writeObject(packet);
                        }
                        catch (Exception e0) {
                            System.out.println("Failed to send to video server " + e0.getMessage());
                            this.clearNeighboringVideoServer(dest.getIPAddress());
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
                totalPacketsReceived++;

                // process by Buffer manager
                if (!this.smartBufferManager.processPacket((SmartDataPacket)packet)) {
                    // First let the client facing server to decide whether the packet should be
                    // forwarded to one of the connected client apps
                    clientFacingTCPServer.handleDataPacket((SmartDataPacket)packet);
                    // Forward to neighboring routers
                    this.forwardPacket(packet);
                }
                else
                    this.totalPacketsSavedBySMART++;

                if (((SmartDataPacket)packet).getOffset() < 0)
                {
                    System.out.println("============ Statistics ================");
                    System.out.println("Total packets received so far: " + this.totalPacketsReceived);
                    System.out.println("Total packets forwarded: " + this.totalPacketsForwarded);
                    System.out.println("Total packets saved by SMART: " + this.totalPacketsSavedBySMART);
                }
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
                    //System.out.println("Next hop for " + dest.getIPAddress() + ": " + nextHop);
                    if (nextHop != null && nextHop.length() > 0) {
                        ArrayList<IPPortPair> decisionlist = null;
                        if (!routingDecision.containsKey(nextHop)) {
                            decisionlist = new ArrayList<IPPortPair>();
                            routingDecision.put(nextHop, decisionlist);
                        }
                        else
                            decisionlist = routingDecision.get(nextHop);
                        decisionlist.add(dest);
                        //System.out.println("Next hop " + nextHop + " for <" + dest.getIPAddress() + ", " + dest.getPort() + ">");
                    }
                }
            }

            // Now we forward packets to next hops
            for (String key : routingDecision.keySet()) {
                ArrayList<IPPortPair> dests = routingDecision.get(key);
                packet.setDestinations(dests);
                // forward to the next hop router
                //System.out.println("Forwarding to " + key);
                if (neighborOutputStreams.containsKey(key)) {
                    ObjectOutputStream oos = neighborOutputStreams.get(key);
                    if (oos != null) {
                        try {
                            this.totalPacketsForwarded++;
                            oos.writeObject(packet);
                            oos.reset();
                        }
                        catch (Exception e) {
                            System.out.println("Failed to forward packet to " + key);
                            // We need to clear the hashmap
                            this.clearNeighboringRouter(key);
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
