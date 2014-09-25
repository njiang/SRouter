package com.SRouter;

import com.xuggle.xuggler.IContainer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

class PacketHandler extends Thread {
    private TCPServer tcpServer;
    private Socket clientSocket;
    private String rootFilePath = ".\\";

    public PacketHandler(Socket clientSocket, TCPServer tcpServer)
    {
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

                                SmartFLVEncoder encoder = new SmartFLVEncoder(request, clientSocket, objos, this.rootFilePath + filename);
                                encoder.startEncoding();
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

    public TCPServer(String rootFilePath, int port)
    {
        this.rootFilePath = rootFilePath;

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
                new PacketHandler(serverSocket.accept(), this).start();
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
       tcpServer = new TCPServer(rootFilePath, Server_Port);
       tcpServer.start();
    }

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
