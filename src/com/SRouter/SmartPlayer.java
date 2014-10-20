/**
 * Created by Ning Jiang on 9/2/14.
 */


package com.SRouter;

import sun.net.util.IPAddressUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Enumeration;


public class SmartPlayer {
    private String neighboringRouterIP = "127.0.0.1";
    private int SMART_Client_Router_Port = 8999;
    private int SMART_Server_Port = 7999;
    private String videoServerIP = "127.0.0.1";
    private String myIP = "127.0.0.1";
    private String videoFileName = "2012.flv";
    private String videoFilePath = "c:\\temp\\";

    public SmartPlayer(String[] args) {
        if (args.length > 0) {
            String configFilePath = args[0];
            int linenum = 0;
            try {
                FileReader freader = new FileReader(configFilePath);
                BufferedReader reader = new BufferedReader(freader);
                do {
                    String line = reader.readLine();
                    if (line == null)
                        break;
                    if (!line.isEmpty()) {
                        if (linenum == 0)
                            neighboringRouterIP = line;
                        else if (linenum == 1)
                            videoFilePath = line;
                        else if (linenum == 2)
                            videoServerIP = line;
                        else
                            break;
                        linenum++;
                    }
                }
                while (true);
                freader.close();
            }
            catch (Exception e) {
                System.out.println("Failed to open configuration file " + e.getMessage());
            }
        }
    }

    public void requestVideo(String fileName) {
        try {
            String myIP = getMyIP();
            Socket clientSocket = new Socket(neighboringRouterIP, SMART_Client_Router_Port);
            ObjectInputStream ins = new ObjectInputStream(clientSocket.getInputStream());
            String command = "Request " + "2012.flv";
            IPPortPair srcAddr = new IPPortPair(myIP, clientSocket.getLocalPort());
            IPPortPair destAddr = new IPPortPair(videoServerIP, 0);  // port is not used anyway
            SmartRequest request = new SmartRequest(srcAddr, destAddr, command);
            ObjectOutputStream objos = new ObjectOutputStream(clientSocket.getOutputStream());
            objos.writeObject(request);

            SmartFLVDecoder decoder = new SmartFLVDecoder(clientSocket, ins, (videoFilePath + fileName));
            decoder.startPlayback();
        }
        catch (Exception e) {
            System.out.println("Error handling playback " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getMyIP()
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



    public static void main(String[] args) {
        SmartPlayer smartPlayer = new SmartPlayer(args);
        smartPlayer.requestVideo("2012.flv");
    }
}
