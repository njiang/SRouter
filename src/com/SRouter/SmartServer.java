package com.SRouter;

import com.xuggle.xuggler.IContainer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

class PacketHandler extends Thread {
    SmartServer server;
    private TCPServer tcpServer;
    private Socket clientSocket;
    private String rootFilePath = ".\\";

    public PacketHandler(SmartServer server, Socket clientSocket, TCPServer tcpServer)
    {
        this.server = server;
        this.tcpServer = tcpServer;
        this.clientSocket = clientSocket;
        this.rootFilePath = tcpServer.getRootFilePath();
    }


    public void run()
    {
        try {
            /*OutputStream out = this.clientSocket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                this.clientSocket.getInputStream()));  */
            ObjectOutputStream objos = new ObjectOutputStream(this.clientSocket.getOutputStream());
            ObjectInputStream objis = new ObjectInputStream(this.clientSocket.getInputStream());
            while (true) {
                SmartPacket packet = SmartPacket.ReadPacket(objis);
                if (packet != null) {
                    if (packet.getType() == PacketType.REQUEST) {
                        SmartRequest request = (SmartRequest)packet;
                        String command = request.getCommand();
                        if (command != null) {
                            System.out.println("Command: " + command + " from " + request.getDestinationIPPorts().get(0).getIPAddress());
                            if (command.contains("Request")) {
                                IContainer container = null;
                                String[] splitted = command.split(" ");
                                String filename = splitted[1];

                                // Encoder is running as a thread
                                SmartFLVEncoder encoder = new SmartFLVEncoder(this.server, request, clientSocket, objos, this.rootFilePath + filename);
                                encoder.start();
                            }
                        }
                    }
                }
                else {
                    // The connection between the video server and this particular neighboring router is lost
                    // we quit this thread
                    break;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class TCPServer extends Thread
{
    private ServerSocket serverSocket = null;
    private int myPort = 7999;
    private String[] neighboringRouters;
    private String rootFilePath = ".";
    private SmartServer server;

    public TCPServer(SmartServer server, String rootFilePath, int port)
    {
        this.rootFilePath = rootFilePath;
        this.server = server;

        if (port >= 0)
            myPort = port;
        try {
            serverSocket = new ServerSocket(myPort);
        }
        catch (Exception e) {
            System.out.println("Failed to open server socket " + e.getMessage());
        }
    }

    public ServerSocket getServerSocket() { return this.serverSocket; }
    public String getRootFilePath() { return this.rootFilePath; }

    public void run() {
        while(true)
        {
            try {
                new PacketHandler(this.server, serverSocket.accept(), this).start();
            }
            catch (Exception e) {
                System.out.println("Failed to accept connections " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}



/**
 * Created by Ning Jiang on 9/2/14.
 */
public class SmartServer extends Thread {
    private TCPServer tcpServer;
    private int Server_Port = 7999;
    private String rootFilePath = "c:\\Temp\\";

    // caches streamed packets to service other requests
    private ConcurrentHashMap<Integer, SmartDataPacket> packetBuffer = new ConcurrentHashMap<Integer, SmartDataPacket>();
    Object syncObj = new Object();  //used to synchronize writing to the objectoutputstream of the router

    public SmartServer(String[] args)
    {
       if (args.length > 0) {
           // TODO
           // read configuration file
           try {
               FileReader fileReader = new FileReader(args[0]);
               BufferedReader reader = new BufferedReader(fileReader);
               int linenum = 0;
               do {
                   String line = reader.readLine(); // source node
                   if (line == null)
                       break;
                   if (linenum == 0) {
                       System.out.println("Video root file " + line);
                       rootFilePath = line;
                   }
                   linenum++;
               }
               while(true);
               fileReader.close();
           }
           catch (Exception e) {
               System.out.println("Failed to open configuration file " + e.getMessage());
           }
       }
       tcpServer = new TCPServer(this, rootFilePath, Server_Port);
       tcpServer.start();
    }

    public void insertPacket(int index, SmartDataPacket packet)
    {
        if (packet != null) {
            Integer idx = new Integer(index);
            this.packetBuffer.put(idx, packet);
        }
    }

    public boolean isPacketBufferEmpty() { return this.packetBuffer.isEmpty(); }

    public SmartDataPacket getPacket(int index)
    {
        Integer idx = new Integer(index);
        if (this.packetBuffer.containsKey(idx)) {
            return this.packetBuffer.get(idx);
        }
        return null;
    }

    public void cleanPacketBuffer() { this.packetBuffer.clear(); }

    public void run()
    {
        while (true) {
            try {
                sleep(10000);
            }
            catch (Exception e) {
                System.out.println("Failed to sleep on main thread " + e.getMessage());
                break;
            }
        }
    }

    public static void main(String[] args) {
        SmartServer smartServer = new SmartServer(args);
        smartServer.start();
    }
}
