package com.SMART;

import com.xuggle.xuggler.IContainer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
            OutputStream out = this.clientSocket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                this.clientSocket.getInputStream()));
            String command;
            if ((command = in.readLine()) != null) {
                System.out.println("Command: " + command);
                if (command.contains("Request")) {
                    IContainer container = null;
                    String[] splitted = command.split(" ");
                    String filename = splitted[1];

                    /*container = IContainer.make();

                    if (container.open(this.rootFilePath + filename, IContainer.Type.READ, null) < 0) {
                        System.out.println("failed to open");
                        return;
                    } */

                    SmartFLVEncoder encoder = new SmartFLVEncoder(clientSocket, dos, this.rootFilePath + filename);
                    encoder.startEncoding();

                    /*IPacket dataPacket = IPacket.make();
                    while(container.readNextPacket(dataPacket) >= 0)
                    {
                        int numBytes = dataPacket.getSize();
                        byte[] buffer = dataPacket.getData().getByteArray(0, numBytes);
                        //dos.writeInt(numBytes);
                        dos.write(buffer, 0, numBytes);
                    } */
                    this.clientSocket.close();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class TCPServer
{
    private ServerSocket serverSocket = null;
    private int myPort = 8999;
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

    public void startServer() {
        while(true)
        {
            try {
                new PacketHandler(serverSocket.accept(), this).start();
            }
            catch (Exception e) {
                System.out.println("Failed to handle udp packets " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

/**
 * Created by Ning Jiang on 9/2/14.
 */
public class SmartServer {
    private TCPServer tcpServer;
    private int Server_Port = 7999;
    private String rootFilePath = "c:\\Temp\\";
    public SmartServer(String[] args)
    {
       if (args.length > 0) {
           // TODO
           // read configuration file
       }
       tcpServer = new TCPServer(rootFilePath, Server_Port);
       tcpServer.startServer();
    }

    public static void main(String[] args) {
       SmartServer smartServer = new SmartServer(args);
    }
}
