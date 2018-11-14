package youga.media.compressor;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import youga.media.compressor.core.InputSurface;
import youga.media.compressor.core.OutputSurface;

/**
 * author: YougaKingWu@gmail.com
 * created on: 2018/03/08 13:37
 * description:
 */
public class MediaController {
    public final static String MIME_TYPE = "video/avc";
    private static final String TAG = MediaController.class.getSimpleName();
    static final int COMPRESS_QUALITY_HIGH = 1;
    static final int COMPRESS_QUALITY_MEDIUM = 2;
    static final int COMPRESS_QUALITY_LOW = 3;

    private MediaController() {
    }

    private static volatile MediaController mInstance;

    public static MediaController getInstance() {
        if (mInstance == null) {
            synchronized (MediaController.class) {
                if (mInstance == null) {
                    mInstance = new MediaController();
                }
            }
        }
        return mInstance;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public File convertVideo(final String sourcePath, String destDir, int quality, CompressProgressListener listener) {

        try {
            File videoFile = new File(destDir, "videoFile.mp4");
            File audioFile = new File(destDir, "audioFile.m4a");
            File combineVideoFile = new File(destDir, "combineVideoFile.mp4");

//            splitMp4(sourcePath, videoFile.getAbsolutePath());
//            splitM4a(sourcePath, audioFile.getAbsolutePath());
//            muxM4AMp4(audioFile.getAbsolutePath(), videoFile.getAbsolutePath(), combineVideoFile.getAbsolutePath());

            convertVideo(videoFile.getAbsolutePath(), combineVideoFile.getAbsolutePath(), quality);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public File convertVideo(String sourcePath, String combineVideoPath, int quality) throws IOException {

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(sourcePath);
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        long duration = Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;


        int rotationValue = Integer.valueOf(rotation);
        int originalWidth = Integer.valueOf(width);
        int originalHeight = Integer.valueOf(height);

        int resultWidth;
        int resultHeight;
        int bitrate;
        switch (quality) {
            default:
            case COMPRESS_QUALITY_HIGH:
                resultWidth = originalWidth * 2 / 3;
                resultHeight = originalHeight * 2 / 3;
                bitrate = resultWidth * resultHeight * 30;
                break;
            case COMPRESS_QUALITY_MEDIUM:
                resultWidth = originalWidth / 2;
                resultHeight = originalHeight / 2;
                bitrate = resultWidth * resultHeight * 10;
                break;
            case COMPRESS_QUALITY_LOW:
                resultWidth = originalWidth / 2;
                resultHeight = originalHeight / 2;
                bitrate = (resultWidth / 2) * (resultHeight / 2) * 10;
                break;
        }


        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(sourcePath);

        int sourceVideoTrackIndex = MediaUtil.selectVideoTrack(extractor);
        extractor.selectTrack(sourceVideoTrackIndex);


        MediaCodecInfo codecInfo = MediaUtil.selectCodec(MIME_TYPE);
        int colorFormat = MediaUtil.selectColorFormat(codecInfo, MIME_TYPE);

        MediaFormat inputFormat = extractor.getTrackFormat(sourceVideoTrackIndex);

        MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
        outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        outputFormat.setInteger("stride", resultWidth + 32);
        outputFormat.setInteger("slice-height", resultHeight);


        MediaCodec encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        InputSurface inputSurface = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
        }
        encoder.start();


        MediaCodec decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        OutputSurface outputSurface;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            outputSurface = new OutputSurface();
        } else {
            outputSurface = new OutputSurface(resultWidth, resultHeight, 0);
        }
        decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
        decoder.start();


        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;
        final int TIMEOUT_USEC = 2500;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();

        while (!outputDone) {
            inputDone = isInputDone(extractor, sourceVideoTrackIndex, decoder, inputDone, TIMEOUT_USEC, decoderInputBuffers);


            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;

            while (decoderOutputAvailable || encoderOutputAvailable) {

                int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    encoderOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encoderOutputBuffers = encoder.getOutputBuffers();
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
//                    if (sourceVideoTrackIndex == -5) {
//                        sourceVideoTrackIndex = mediaMuxer.addTrack(newFormat, false);
//                    }
                } else if (encoderStatus < 0) {
                    throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else {
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                    }
                    if (info.size > 1) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
//                            if (mediaMuxer.writeSampleData(sourceVideoTrackIndex, encodedData, info, false)) {
//                                didWriteData(false, false);
//                            }
                        } else if (sourceVideoTrackIndex == -5) {
                            byte[] csd = new byte[info.size];
                            encodedData.limit(info.offset + info.size);
                            encodedData.position(info.offset);
                            encodedData.get(csd);
                            ByteBuffer sps = null;
                            ByteBuffer pps = null;
                            for (int a = info.size - 1; a >= 0; a--) {
                                if (a > 3) {
                                    if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                        sps = ByteBuffer.allocate(a - 3);
                                        pps = ByteBuffer.allocate(info.size - (a - 3));
                                        sps.put(csd, 0, a - 3).position(0);
                                        pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                            MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                            if (sps != null && pps != null) {
                                newFormat.setByteBuffer("csd-0", sps);
                                newFormat.setByteBuffer("csd-1", pps);
                            }
//                            videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                        }
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                }

                if (!decoderDone) {
                    int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = decoder.getOutputFormat();
                        Log.e("tmessages", "newFormat = " + newFormat);
                    } else if (decoderStatus < 0) {
                        throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                    } else {
                        boolean doRender;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            doRender = info.size != 0;
                        } else {
                            doRender = info.size != 0 || info.presentationTimeUs != 0;
                        }
                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {
                            boolean errorWait = false;
                            try {
                                outputSurface.awaitNewImage();
                            } catch (Exception e) {
                                errorWait = true;
                                Log.e("tmessages", e.getMessage());
                            }
                            if (!errorWait) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                    outputSurface.drawImage(false);
                                    inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
//                                    if (listener != null) {
//                                        listener.onProgress((float) info.presentationTimeUs / (float) duration * 100);
//                                    }
                                    inputSurface.swapBuffers();
                                } else {
                                    int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                    if (inputBufIndex >= 0) {
                                        outputSurface.drawImage(true);
                                        ByteBuffer rgbBuf = outputSurface.getFrame();
                                        ByteBuffer yuvBuf = encoderInputBuffers[inputBufIndex];
                                        yuvBuf.clear();
//                                        convertVideoFrame(rgbBuf, yuvBuf, colorFormat, resultWidth, resultHeight, padding, swapUV);
//                                        encoder.queueInputBuffer(inputBufIndex, 0, bufferSize, info.presentationTimeUs, 0);
                                    } else {
                                        Log.e("tmessages", "input buffer not available");
                                    }
                                }
                            }
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderOutputAvailable = false;
                            Log.e("tmessages", "decoder stream end");
                            if (Build.VERSION.SDK_INT >= 18) {
                                encoder.signalEndOfInputStream();
                            } else {
                                int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                if (inputBufIndex >= 0) {
                                    encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                }
                            }
                        }
                    }
                }

            }
        }

        extractor.unselectTrack(sourceVideoTrackIndex);


        outputSurface.release();
        if (inputSurface != null) inputSurface.release();
        decoder.stop();
        decoder.release();
        encoder.stop();
        encoder.release();
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private boolean isInputDone(MediaExtractor extractor, int sourceVideoTrackIndex, MediaCodec decoder, boolean inputDone, int TIMEOUT_USEC, ByteBuffer[] decoderInputBuffers) {
        if (!inputDone) {
            int index = extractor.getSampleTrackIndex();
            if (index == sourceVideoTrackIndex) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            } else if (index == -1) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                }
            }
        }
        return inputDone;
    }


    /**
     * 将 Mp4 的音频和视频分离
     *
     * @param mp4Path    .mp4
     * @param outMp4Path .mp4
     */
    public static void splitMp4(String mp4Path, String outMp4Path) throws IOException {
        Movie videoMovie = MovieCreator.build(mp4Path);
        Track videoTracks = null;// 获取视频的单纯视频部分
        for (Track videoMovieTrack : videoMovie.getTracks()) {
            if ("vide".equals(videoMovieTrack.getHandler())) {
                videoTracks = videoMovieTrack;
            }
        }

        Movie resultMovie = new Movie();
        resultMovie.addTrack(videoTracks);// 视频部分

        Container out = new DefaultMp4Builder().build(resultMovie);
        FileOutputStream fos = new FileOutputStream(new File(outMp4Path));
        out.writeContainer(fos.getChannel());
        fos.close();
    }

    /**
     * 将 Mp4 的音频和视频分离
     *
     * @param mp4Path    .mp4
     * @param outM4aPath .m4a
     */
    public static void splitM4a(String mp4Path, String outM4aPath) throws IOException {
        Movie audioMovie = MovieCreator.build(mp4Path);
        Track audioTracks = null;// 获取视频的单纯音频部分
        for (Track audioMovieTrack : audioMovie.getTracks()) {
            if ("soun".equals(audioMovieTrack.getHandler())) {
                audioTracks = audioMovieTrack;
            }
        }

        Movie resultMovie = new Movie();
        resultMovie.addTrack(audioTracks);// 音频部分

        Container out = new DefaultMp4Builder().build(resultMovie);
        FileOutputStream fos = new FileOutputStream(new File(outM4aPath));
        out.writeContainer(fos.getChannel());
        fos.close();
    }


    /**
     * 将 M4A 和 MP4 进行混合[替换了视频的音轨]
     *
     * @param m4aPath .m4a[同样可以使用.mp4]
     * @param mp4Path .mp4
     * @param outPath .mp4
     */
    public static void muxM4AMp4(String m4aPath, String mp4Path, String outPath) throws IOException {
        Movie audioMovie = MovieCreator.build(m4aPath);
        Track audioTracks = null;// 获取视频的单纯音频部分
        for (Track audioMovieTrack : audioMovie.getTracks()) {
            if ("soun".equals(audioMovieTrack.getHandler())) {
                audioTracks = audioMovieTrack;
            }
        }

        Movie videoMovie = MovieCreator.build(mp4Path);
        Track videoTracks = null;// 获取视频的单纯视频部分
        for (Track videoMovieTrack : videoMovie.getTracks()) {
            if ("vide".equals(videoMovieTrack.getHandler())) {
                videoTracks = videoMovieTrack;
            }
        }

        Movie resultMovie = new Movie();
        resultMovie.addTrack(videoTracks);// 视频部分
        resultMovie.addTrack(audioTracks);// 音频部分

        Container out = new DefaultMp4Builder().build(resultMovie);
        FileOutputStream fos = new FileOutputStream(new File(outPath));
        out.writeContainer(fos.getChannel());
        fos.close();
    }

}
