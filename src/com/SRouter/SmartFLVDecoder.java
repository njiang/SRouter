package com.SRouter;

/*******************************************************************************
 * Copyright (c) 2008, 2010 Xuggle Inc.  All rights reserved.
 *
 * This file is part of Xuggle-Xuggler-Main.
 *
 * Xuggle-Xuggler-Main is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xuggle-Xuggler-Main is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Xuggle-Xuggler-Main.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/


import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.*;
import com.xuggle.xuggler.demos.VideoImage;

import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

/**
 * Takes a media container, finds the first video stream,
 * decodes that stream, and then displays the video frames,
 * at the frame-rate specified by the container, on a
 * window.
 * @author aclarke
 *
 */
public class SmartFLVDecoder
{
    IContainer container = null;
    Socket clientSocket = null;
    String decodeFileName = "";
    ObjectInputStream socketInputStream = null;
    private SourceDataLine mLine;
    private long mSystemVideoClockStartTime;

    private long mFirstVideoTimestampInStream;

    /**
     * Takes a media container (file) as the first argument, opens it,
     * opens up a Swing window and displays
     * video frames with <i>roughly</i> the right timing.
     *
     * @param clientSocket Must contain the socket that connects to the SMART router
     */
    @SuppressWarnings("deprecation")
    public SmartFLVDecoder(Socket clientSocket, ObjectInputStream ins, String filename)
    {
        this.decodeFileName = filename;
        this.clientSocket = clientSocket;

        // Let's make sure that we can actually convert video pixel formats.
        if (!IVideoResampler.isSupported(
                IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION))
            throw new RuntimeException("you must install the GPL version" +
                    " of Xuggler (with IVideoResampler support) for " +
                    "this demo to work");

        // Create a Xuggler container object
        container = IContainer.make();
        try {
            socketInputStream = ins; //new DataInputStream(clientSocket.getInputStream());
        }
        catch (Exception e) {
            System.out.println("Failed to create socket input stream " + e.getMessage());
        }
    }

    public void startPlayback() throws IOException {
        // Open up the container
        //IContainerFormat rFormat = IContainerFormat.make();
        //rFormat.setInputFormat("flv");
        //if (container.open(clientSocket.getInputStream(), rFormat) < 0)
        //    throw new IllegalArgumentException("could not open file: ");
        if (socketInputStream == null)
            return;

        // We "cheat" a little here.  We open a flv file in the container first,
        // then we feed the decoders with packets received from the encoder.
        // Make sure both encoder and decoder use the same file!
        if (container.open(decodeFileName, IContainer.Type.READ, null) < 0) {
            System.out.println("failed to open");
        }

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
        if (videoStreamId == -1 && audioStreamId == -1)
            throw new RuntimeException("could not find audio or video stream in container: ");

    /*
     * Now we have found the video stream in this file.  Let's open up our decoder so it can
     * do work.
     */
        if (videoCoder.open() < 0)
            throw new RuntimeException("could not open video decoder for container: "
                    );

        IVideoResampler resampler = null;
        if (videoCoder.getPixelType() != IPixelFormat.Type.BGR24)
        {
            // if this stream is not in BGR24, we're going to need to
            // convert it.  The VideoResampler does that for us.
            resampler = IVideoResampler.make(videoCoder.getWidth(),
                    videoCoder.getHeight(), IPixelFormat.Type.BGR24,
                    videoCoder.getWidth(), videoCoder.getHeight(), videoCoder.getPixelType());
            if (resampler == null)
                throw new RuntimeException("could not create color space " +
                        "resampler for: ");
        }
    /*
     * And once we have that, we draw a window on screen
     */
        openJavaWindow();


        if (audioCoder != null)
        {
            if (audioCoder.open() < 0)
                throw new RuntimeException("could not open audio decoder for container: ");

      /*
       * And once we have that, we ask the Java Sound System to get itself ready.
       */
            try
            {
                openJavaSound(audioCoder);
            }
            catch (LineUnavailableException ex)
            {
                throw new RuntimeException("unable to open sound device on your system when playing back container: ");
            }
        }


    /*
     * Now, we start walking through the container looking at each packet.
     */
        mFirstVideoTimestampInStream = Global.NO_PTS;
        mSystemVideoClockStartTime = 0;
        int numBytes = 0;
        int count = 0;
        int bytesread = 0;
        //IPacket packet = IPacket.make();
        do
        {
            SmartDataPacket dataPacket = null;
            try {
                dataPacket = (SmartDataPacket)socketInputStream.readObject();
            }
            catch (Exception e) {
                System.out.println("Failed to read packet " + e.getMessage());
            }

            if (dataPacket == null)
                break;

            IPacket packet = IPacket.make(IBuffer.make(null, dataPacket.getData(), 0, dataPacket.getLength()));
            System.out.println("Received packet " + count + " " + dataPacket.getLength() + " stream index " + packet.getStreamIndex());
            count++;

            //container.readNextPacket(packet);
            //if (packet == null)
            //    break;


      /*
       * Now we have a packet, let's see if it belongs to our video stream
       */
            if (dataPacket.getStreamIndex() == videoStreamId)
            {
         /*
         * We allocate a new picture to get the data out of Xuggler
         */
                IVideoPicture picture = IVideoPicture.make(videoCoder.getPixelType(),
                        videoCoder.getWidth(), videoCoder.getHeight());

        /*
         * Now, we decode the video, checking for any errors.
         *
         */
                int bytesDecoded = videoCoder.decodeVideo(picture, packet, 0);
                if (bytesDecoded < 0)
                    throw new RuntimeException("got error decoding audio in: ");

        /*
         * Some decoders will consume data in a packet, but will not be able to construct
         * a full video picture yet.  Therefore you should always check if you
         * got a complete picture from the decoder
         */
                if (picture.isComplete())
                {
                    IVideoPicture newPic = picture;
          /*
           * If the resampler is not null, that means we didn't get the video in BGR24 format and
           * need to convert it into BGR24 format.
           */
                    if (resampler != null)
                    {
                        // we must resample
                        newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), picture.getWidth(), picture.getHeight());
                        if (resampler.resample(newPic, picture) < 0)
                            throw new RuntimeException("could not resample video from: ");
                    }
                    if (newPic.getPixelType() != IPixelFormat.Type.BGR24)
                        throw new RuntimeException("could not decode video as BGR 24 bit data in: ");

                    long delay = millisecondsUntilTimeToDisplay(newPic);
                    // if there is no audio stream; go ahead and hold up the main thread.  We'll end
                    // up caching fewer video pictures in memory that way.
                    try
                    {
                        if (delay > 0)
                            Thread.sleep(delay);
                    }
                    catch (InterruptedException e)
                    {
                        return;
                    }

                    // And finally, convert the picture to an image and display it

                    mScreen.setImage(Utils.videoPictureToImage(newPic));
                }
            }
            else if (dataPacket.getStreamIndex() == audioStreamId)
            {
        /*
         * We allocate a set of samples with the same number of channels as the
         * coder tells us is in this buffer.
         *
         * We also pass in a buffer size (1024 in our example), although Xuggler
         * will probably allocate more space than just the 1024 (it's not important why).
         */
                IAudioSamples samples = IAudioSamples.make(1024, audioCoder.getChannels());

        /*
         * A packet can actually contain multiple sets of samples (or frames of samples
         * in audio-decoding speak).  So, we may need to call decode audio multiple
         * times at different offsets in the packet's data.  We capture that here.
         */
                int offset = 0;

        /*
         * Keep going until we've processed all data
         */
                while(offset < packet.getSize())
                {
                    int bytesDecoded = audioCoder.decodeAudio(samples, packet, offset);
                    if (bytesDecoded < 0)
                        throw new RuntimeException("got error decoding audio in: " );
                    offset += bytesDecoded;
          /*
           * Some decoder will consume data in a packet, but will not be able to construct
           * a full set of samples yet.  Therefore you should always check if you
           * got a complete set of samples from the decoder
           */
                    if (samples.isComplete())
                    {
                        // note: this call will block if Java's sound buffers fill up, and we're
                        // okay with that.  That's why we have the video "sleeping" occur
                        // on another thread.
                        playJavaSound(samples);
                    }
                }
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
        while (true);// (numBytes >= 0);
    /*
     * Technically since we're exiting anyway, these will be cleaned up by
     * the garbage collector... but because we're nice people and want
     * to be invited places for Christmas, we're going to show how to clean up.
     */
        /*if (videoCoder != null)
        {
            videoCoder.close();
            videoCoder = null;
        }
        if (container !=null)
        {
            container.close();
            container = null;
        }
        closeJavaWindow(); */

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


    private void openJavaSound(IStreamCoder aAudioCoder) throws LineUnavailableException
    {
        AudioFormat audioFormat = new AudioFormat(aAudioCoder.getSampleRate(),
                (int)IAudioSamples.findSampleBitDepth(aAudioCoder.getSampleFormat()),
                aAudioCoder.getChannels(),
                true, /* xuggler defaults to signed 16 bit samples */
                false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        mLine = (SourceDataLine) AudioSystem.getLine(info);
        /**
         * if that succeeded, try opening the line.
         */
        mLine.open(audioFormat);
        /**
         * And if that succeed, start the line.
         */
        mLine.start();


    }

    private void playJavaSound(IAudioSamples aSamples)
    {
        /**
         * We're just going to dump all the samples into the line.
         */
        byte[] rawBytes = aSamples.getData().getByteArray(0, aSamples.getSize());
        mLine.write(rawBytes, 0, aSamples.getSize());
    }

    private void closeJavaSound()
    {
        if (mLine != null)
        {
      /*
       * Wait for the line to finish playing
       */
            mLine.drain();
      /*
       * Close the line.
       */
            mLine.close();
            mLine=null;
        }
    }

    private long millisecondsUntilTimeToDisplay(IVideoPicture picture)
    {
        /**
         * We could just display the images as quickly as we decode them, but it turns
         * out we can decode a lot faster than you think.
         *
         * So instead, the following code does a poor-man's version of trying to
         * match up the frame-rate requested for each IVideoPicture with the system
         * clock time on your computer.
         *
         * Remember that all Xuggler IAudioSamples and IVideoPicture objects always
         * give timestamps in Microseconds, relative to the first decoded item.  If
         * instead you used the packet timestamps, they can be in different units depending
         * on your IContainer, and IStream and things can get hairy quickly.
         */
        long millisecondsToSleep = 0;
        if (mFirstVideoTimestampInStream == Global.NO_PTS)
        {
            // This is our first time through
            mFirstVideoTimestampInStream = picture.getTimeStamp();
            // get the starting clock time so we can hold up frames
            // until the right time.
            mSystemVideoClockStartTime = System.currentTimeMillis();
            millisecondsToSleep = 0;
        } else {
            long systemClockCurrentTime = System.currentTimeMillis();
            long millisecondsClockTimeSinceStartofVideo = systemClockCurrentTime - mSystemVideoClockStartTime;
            // compute how long for this frame since the first frame in the stream.
            // remember that IVideoPicture and IAudioSamples timestamps are always in MICROSECONDS,
            // so we divide by 1000 to get milliseconds.
            long millisecondsStreamTimeSinceStartOfVideo = (picture.getTimeStamp() - mFirstVideoTimestampInStream)/1000;
            final long millisecondsTolerance = 50; // and we give ourselfs 50 ms of tolerance
            millisecondsToSleep = (millisecondsStreamTimeSinceStartOfVideo -
                    (millisecondsClockTimeSinceStartofVideo+millisecondsTolerance));
        }
        return millisecondsToSleep;
    }
}