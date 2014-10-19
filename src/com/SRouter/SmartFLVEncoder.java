package com.SRouter;

import com.xuggle.xuggler.*;
import com.xuggle.xuggler.demos.VideoImage;

import java.awt.image.BufferedImage;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Takes a media container, finds the first video stream,
 * decodes that stream, and then displays the video frames,
 * at the frame-rate specified by the container, on a
 * window.
 * @author aclarke
 *
 */
public class SmartFLVEncoder extends Thread
{
    IContainer container = null;
    String encodeFileName = "";
    Socket clientSocket = null;
    ObjectOutputStream socketOutputStream = null;
    SmartRequest requestPacket;
    SmartServer server;
    private int videoId = 0;

    /**
     * Takes a media container (file) as the first argument, opens it,
     * opens up a Swing window and displays
     * video frames with <i>roughly</i> the right timing.
     *
     * @param filename Must contain the full path of that file that needs to be encoded
     */
    @SuppressWarnings("deprecation")
    public SmartFLVEncoder(SmartServer server, SmartRequest request, Socket clientSocket, ObjectOutputStream objos, String filename)

    {
        this.requestPacket = request;
        this.encodeFileName = filename;
        this.clientSocket = clientSocket;
        this.server = server;

        // Let's make sure that we can actually convert video pixel formats.
        if (!IVideoResampler.isSupported(
                IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION))
            throw new RuntimeException("you must install the GPL version" +
                    " of Xuggler (with IVideoResampler support) for " +
                    "this demo to work");

        // Create a Xuggler container object
        container = IContainer.make();

        if (container.open(encodeFileName, IContainer.Type.READ, null) < 0) {
            System.out.println("failed to open");
        }

        try {
            socketOutputStream = objos; //new DataOutputStream(this.clientSocket.getOutputStream());
        }
        catch (Exception e) {
            System.out.println("Failed to get output from socket " + e.getMessage());
        }
    }

    public void run() {

        if (socketOutputStream == null)
            return;

        if (this.server.isPacketBufferEmpty()) {
            System.out.println("Start streaming...");
            // Open up the container
            // query how many streams the call to open found
            int numStreams = container.getNumStreams();

            // and iterate through the streams to find the first video stream
            int videoStreamId = -1;
            IStreamCoder videoCoder = null;
            int audioStreamId = -1;
            IStreamCoder audioCoder = null;
            for(int i = 0; i < numStreams; i++)
            {
                // Find the stream object
                IStream stream = container.getStream(i);
                // Get the pre-configured decoder that can decode this stream;
                IStreamCoder coder = stream.getStreamCoder();

                if (videoStreamId == -1 && coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO)
                {
                    videoStreamId = i;
                    videoCoder = coder;
                }
                else if (audioStreamId == -1 && coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO)
                {
                    audioStreamId = i;
                    audioCoder = coder;
                }
            }
            if (videoStreamId == -1)
                throw new RuntimeException("could not find video stream in container: "
                );

        /*
         * Now, we start walking through the container looking at each packet.
         */
            IPacket packet = IPacket.make();
            long firstTimestampInStream = Global.NO_PTS;
            long systemClockStartTime = 0;
            int count = 0;
            while(container.readNextPacket(packet) >= 0)
            {
          /*
           * Now we have a packet, let's see if it belongs to our video stream
           */
                if (packet.getStreamIndex() == videoStreamId || packet.getStreamIndex() == audioStreamId)
                {
                    int numBytes = packet.getSize();
                    byte[] buffer = packet.getData().getByteArray(0, numBytes);
                    //socketOutputStream.writeInt(numBytes);
                    //socketOutputStream.write(buffer, 0, numBytes); */
                    IPPortPair src = new IPPortPair(this.clientSocket.getLocalAddress().getHostAddress(), this.clientSocket.getLocalPort());
                    IPPortPair dest = requestPacket.getSourceIPPort();  // <==== dest is the IP, port pair of the client app
                    IPPortPair[] dests = new IPPortPair[1];
                    dests[0] = dest;

                    // somehow the streamindex is not preserved during data transmission, we record it in the SmartDataPacket
                    SmartDataPacket dataPacket = new SmartDataPacket(this.videoId, src, dests, buffer, numBytes, packet.getStreamIndex(), count);
                    System.out.println("Packet " + count + " Size: " + numBytes + " Stream index " + packet.getStreamIndex() + " for " + dest.getIPAddress() + " " + dest.getPort());
                    // Insert packet to the buffer to service later requests
                    this.server.insertPacket(count, dataPacket);
                    try {
                        synchronized (this.server.syncObj) {
                            socketOutputStream.writeObject(dataPacket);
                            socketOutputStream.reset();
                        }
                    }
                    catch (Exception e) {
                        System.out.println("Failed to write to socket " + e.getMessage());
                        break;
                    }
                    count++;
                }
                else
                {
            /*
             * This packet isn't part of our video stream, so we just
             * silently drop it.
             */
                    do {} while(false);
                }

            }

            //this.socketOutputStream.close();
            // Insert a dummy packet to the buffer to mark the end of stream
            System.out.println("****** Streaming finished, inserting dummy packet " + count);
            IPPortPair dest = requestPacket.getSourceIPPort();  // <==== dest is the IP, port pair of the client app
            IPPortPair[] dests = new IPPortPair[1];
            dests[0] = dest;
            IPPortPair src = new IPPortPair(this.clientSocket.getLocalAddress().getHostAddress(), this.clientSocket.getLocalPort());
            SmartDataPacket dummyPacket = new SmartDataPacket(this.videoId, src, dests, null, 0, 0, -1);
            this.server.insertPacket(count, dummyPacket);

            try {
                synchronized (this.server.syncObj) {
                    socketOutputStream.writeObject(dummyPacket);
                    socketOutputStream.reset();
                    System.out.println("Dummy packet sent!");
                }
            }
            catch (Exception e) {
                System.out.println("Failed to write to socket to send dummy packet " + e.getMessage());
            }
        /*
         * Technically since we're exiting anyway, these will be cleaned up by
         * the garbage collector... but because we're nice people and want
         * to be invited places for Christmas, we're going to show how to clean up.
         */
            if (videoCoder != null)
            {
                videoCoder.close();
                videoCoder = null;
            }
            if (container !=null)
            {
                container.close();
                container = null;
            }
            //closeJavaWindow();
        }
        else {
            // packet buffer is being filled (or already have been filled)
            System.out.println("Fetching packets from buffer");
            int count = 0;
            do {
                SmartDataPacket packet = this.server.getPacket(count);
                if (packet != null) {
                    if (packet.getOffset() < 0) {
                        // dummy packet
                        System.out.println("Dummy packet encountered. Streaming finished.");
                        try {
                            synchronized (this.server.syncObj) {
                                IPPortPair dest = requestPacket.getSourceIPPort();  // <==== dest is the IP, port pair of the client app
                                IPPortPair[] dests = new IPPortPair[1];
                                dests[0] = dest;
                                packet.setDestinations(dests);
                                socketOutputStream.writeObject(packet);
                                socketOutputStream.reset();
                                System.out.println("Dummy packet sent!");
                            }
                        }
                        catch (Exception e) {
                            System.out.println("Failed to write to socket to send dummy packet " + e.getMessage());
                        }

                        break;
                    }
                    IPPortPair src = new IPPortPair(this.clientSocket.getLocalAddress().getHostAddress(), this.clientSocket.getLocalPort());
                    IPPortPair dest = requestPacket.getSourceIPPort();  // <==== dest is the IP, port pair of the client app
                    IPPortPair[] dests = new IPPortPair[1];
                    dests[0] = dest;
                    packet.setDestinations(dests);
                    System.out.println("Packet " + packet.getOffset() + " size " + packet.getLength() + " fetched for " + dest.getIPAddress() + " " + dest.getPort());
                    try {
                        synchronized (this.server.syncObj) {
                            socketOutputStream.writeObject(packet);
                            socketOutputStream.reset();
                        }
                        count++;
                    }
                    catch (Exception e) {
                        System.out.println("Failed to write to socket " + e.getMessage());
                        e.printStackTrace();
                        break;
                    }
                }
                else {
                    try {
                        sleep(20);
                    }
                    catch (Exception e) {
                        System.out.println("Thread sleep error " + e.getMessage());
                        break;
                    }
                }
            }
            while (true);
            //this.socketOutputStream.close();
            if (container !=null)
            {
                container.close();
                container = null;
            }
        }

    }

    /**
     * The window we'll draw the video on.
     *
     */
    private VideoImage mScreen = null;

    private void updateJavaWindow(BufferedImage javaImage)
    {
        mScreen.setImage(javaImage);
    }

    /**
     * Opens a Swing window on screen.
     */
    private void openJavaWindow()
    {
        mScreen = new VideoImage();
    }

    /**
     * Forces the swing thread to terminate; I'm sure there is a right
     * way to do this in swing, but this works too.
     */
    private void closeJavaWindow()
    {
        System.exit(0);
    }
}
