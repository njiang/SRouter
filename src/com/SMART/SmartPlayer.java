/**
 * Created by Ning Jiang on 9/2/14.
 */


package com.SMART;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


public class SmartPlayer {
    private String neighboringRouterIP = "127.0.0.1";
    private int SMART_Client_Router_Port = 8999;
    private int SMART_Server_Port = 7999;
    private String videoServerIP = "127.0.0.1";
    private String myIP = "127.0.0.1";
    private String videoFileName = "2012.flv";
    private String videoFilePath = "C:\\Temp\\";

    public SmartPlayer(String[] args) {
        if (args.length > 0) {
            String configFilePath = args[0];
            try {
                FileInputStream configFileStream = new FileInputStream(configFilePath);
            }
            catch (Exception e) {
                System.out.println("Failed to open configuration file " + e.getMessage());
            }
        }
    }

    public void requestVideo(String fileName) {
        // Establish a channel with the immediate edge SMART router
        try {
            Socket clientSocket = new Socket(neighboringRouterIP, SMART_Client_Router_Port); // (videoServerIP, SMART_Server_Port);
            DataInputStream ins = new DataInputStream(clientSocket.getInputStream());
            String command = "Request " + "2012.flv" + "\n";
            //byte[] sendData = command.getBytes();
            //DataOutputStream os = new DataOutputStream(clientSocket.getOutputStream());
            //os.writeBytes(command);
            //os.flush();
            IPPortPair srcAddr = new IPPortPair(clientSocket.getLocalAddress().getHostAddress(), clientSocket.getLocalPort());
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


    public static void main(String[] args) {
        SmartPlayer smartPlayer = new SmartPlayer(args);
        smartPlayer.requestVideo("2012-1.flv");
    }
}
