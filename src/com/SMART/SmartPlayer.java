/**
 * Created by Ning Jiang on 9/2/14.
 */


package com.SMART;

import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.*;
import com.xuggle.xuggler.demos.VideoImage;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.net.Socket;


public class SmartPlayer {
    private String neighboringRouterIP = "127.0.0.1";
    private int SMART_Client_Router_Port = 8999;
    private int SMART_Server_Port = 7999;
    private String videoServerIP = "127.0.0.1";
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

    private static VideoImage mScreen = null;

    private static void updateJavaWindow(BufferedImage javaImage)
    {
        mScreen.setImage(javaImage);
    }

    /**
     * Opens a Swing window on screen.
     */
    private static void openJavaWindow()
    {
        mScreen = new VideoImage();
    }

    /**
     * Forces the swing thread to terminate; I'm sure there is a right
     * way to do this in swing, but this works too.
     */
    private static void closeJavaWindow()
    {
        System.exit(0);
    }


    private void handlePackets(Socket clientSocket) {
        IContainer container = IContainer.make();
        IContainer outContainer = IContainer.make();

        // we attempt to open up the container
        try {
            IContainerFormat rFormat = IContainerFormat.make();
            rFormat.setInputFormat("flv");
            int result = container.open(clientSocket.getInputStream(), rFormat, true, false);
            // check if the operation was successful
            if (result < 0)
                throw new RuntimeException("Failed to open media file");
            int numStreams = container.getNumStreams();

            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
            boolean hasData = true;
            do {
                try {
                    int size = dis.readInt();
                    byte[] data = new byte[size];
                    dis.read(data);
                    IPacket InPacket = IPacket.make(IBuffer.make(null, data, 0, data.length));
                    InPacket.setKeyPacket(true);
                    InPacket.setComplete(true, size);
                    //retval = outContainer.writePacket(InPacket);

                }
                catch (Exception re) {
                    hasData = false;
                }
            }
            while (hasData);


            /*IMediaDebugListener debugListener = ToolFactory.makeDebugListener();
            IMediaReader reader = ToolFactory.makeReader(container);
            IMediaViewer mediaViewer = ToolFactory.makeViewer(IMediaViewer.Mode.AUDIO_VIDEO, true);

            reader.addListener(mediaViewer);
            reader.addListener(debugListener);

            // read out the contents of the media file, and sit back and watch
            while (reader.readPacket() == null)
                do {} while(false);      */
        }
        catch (Exception e) {
            System.out.println("Failed to handle packet " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void requestVideo(String fileName) {
        // Establish a channel with the immediate edge SMART router
        try {
            Socket clientSocket = new Socket(videoServerIP, SMART_Server_Port);
            String command = "Request " + "2012.flv" + "\n";
            byte[] sendData = command.getBytes();
            DataOutputStream os = new DataOutputStream(clientSocket.getOutputStream());
            os.writeBytes(command);
            os.flush();

            //handlePackets(clientSocket);
            SmartFLVDecoder decoder = new SmartFLVDecoder(clientSocket, (videoFilePath + fileName));
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
