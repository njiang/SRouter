package com.SMART;

import com.xuggle.xuggler.*;
import com.xuggle.xuggler.demos.VideoImage;

import java.awt.image.BufferedImage;
import java.io.IOException;
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
public class SmartFLVEncoder
{
    IContainer container = null;
    String encodeFileName = "";
    Socket clientSocket = null;
    ObjectOutputStream socketOutputStream = null;
    SmartRequest requestPacket;

    /**
     * Takes a media container (file) as the first argument, opens it,
     * opens up a Swing window and displays
     * video frames with <i>roughly</i> the right timing.
     *
     * @param filename Must contain the full path of that file that needs to be encoded
     */
    @SuppressWarnings("deprecation")
    public SmartFLVEncoder(SmartRequest request, Socket clientSocket, ObjectOutputStream objos, String filename)
    {
        this.requestPacket = request;
        this.encodeFileName = filename;
        this.clientSocket = clientSocket;

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

    public void startEncoding() throws IOException {

        if (socketOutputStream == null)
            return;

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
                SmartDataPacket dataPacket = new SmartDataPacket(src, dests, buffer, numBytes, packet.getStreamIndex());
                System.out.println("Packet " + count + " Size: " + numBytes + " Stream index " + packet.getStreamIndex());
                socketOutputStream.writeObject(dataPacket);
                socketOutputStream.reset();
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
        closeJavaWindow();

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
