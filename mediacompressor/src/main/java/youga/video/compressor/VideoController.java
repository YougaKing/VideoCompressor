package youga.video.compressor;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author: YougaKingWu@gmail.com
 * @created on: 2018/11/14 14:57
 * @description:
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class VideoController {

    static final int COMPRESS_QUALITY_HIGH = 1;
    static final int COMPRESS_QUALITY_MEDIUM = 2;
    static final int COMPRESS_QUALITY_LOW = 3;

    private final static String MIME_TYPE = "video/avc";
    private final static int PROCESSOR_TYPE_OTHER = 0;
    private final static int PROCESSOR_TYPE_QCOM = 1;
    private final static int PROCESSOR_TYPE_INTEL = 2;
    private final static int PROCESSOR_TYPE_MTK = 3;
    private final static int PROCESSOR_TYPE_SEC = 4;
    private final static int PROCESSOR_TYPE_TI = 5;

    private static volatile VideoController Instance = null;
    private String mSourcePath;

    public static VideoController getInstance() {
        VideoController localInstance = Instance;
        if (localInstance == null) {
            synchronized (VideoController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new VideoController();
                }
            }
        }
        return localInstance;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public boolean convertVideo(final String sourcePath, String destinationPath, int quality, CompressProgressListener listener) {

        mSourcePath = sourcePath;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(mSourcePath);
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        long duration = Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;


        long startTime = -1;
        long endTime = -1;

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

        int rotateRender = 0;

        File cacheFile = new File(destinationPath);

        File inputFile = new File(mSourcePath);
        if (!inputFile.canRead()) {
            return false;
        }
        long videoStartTime = startTime;

        long time = System.currentTimeMillis();


        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        MediaExtractor extractor = new MediaExtractor();
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        try {
            extractor.setDataSource(inputFile.toString());
            int videoIndex = selectTrack(extractor, false);

            if (videoIndex >= 0) {
                int colorFormat;
                int swapUV = 0;
                int processorType = PROCESSOR_TYPE_OTHER;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
                } else {
                    MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
                    colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
                    if (colorFormat == 0) {
                        throw new RuntimeException("no supported color format");
                    }
                    String codecName = codecInfo.getName();
                    if (codecName.contains("OMX.qcom.")) {
                        processorType = PROCESSOR_TYPE_QCOM;
                        if (Build.VERSION.SDK_INT == 16) {
                            if (manufacturer.equals("lge") || manufacturer.equals("nokia")) {
                                swapUV = 1;
                            }
                        }
                    } else if (codecName.contains("OMX.Intel.")) {
                        processorType = PROCESSOR_TYPE_INTEL;
                    } else if (codecName.equals("OMX.MTK.VIDEO.ENCODER.AVC")) {
                        processorType = PROCESSOR_TYPE_MTK;
                    } else if (codecName.equals("OMX.SEC.AVC.Encoder")) {
                        processorType = PROCESSOR_TYPE_SEC;
                        swapUV = 1;
                    } else if (codecName.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                        processorType = PROCESSOR_TYPE_TI;
                    }
                }

                int resultHeightAligned = resultHeight;
                int padding = 0;
                int bufferSize = resultWidth * resultHeight * 3 / 2;
                if (processorType == PROCESSOR_TYPE_OTHER) {
                    if (resultHeight % 16 != 0) {
                        resultHeightAligned += (16 - (resultHeight % 16));
                        padding = resultWidth * (resultHeightAligned - resultHeight);
                        bufferSize += padding * 5 / 4;
                    }
                } else if (processorType == PROCESSOR_TYPE_QCOM) {
                    if (!manufacturer.toLowerCase().equals("lge")) {
                        int uvoffset = (resultWidth * resultHeight + 2047) & ~2047;
                        padding = uvoffset - (resultWidth * resultHeight);
                        bufferSize += padding;
                    }
                } else if (processorType == PROCESSOR_TYPE_TI) {
                    //resultHeightAligned = 368;
                    //bufferSize = resultWidth * resultHeightAligned * 3 / 2;
                    //resultHeightAligned += (16 - (resultHeight % 16));
                    //padding = resultWidth * (resultHeightAligned - resultHeight);
                    //bufferSize += padding * 5 / 4;
                } else if (processorType == PROCESSOR_TYPE_MTK) {
                    if (manufacturer.equals("baidu")) {
                        resultHeightAligned += (16 - (resultHeight % 16));
                        padding = resultWidth * (resultHeightAligned - resultHeight);
                        bufferSize += padding * 5 / 4;
                    }
                }

                extractor.selectTrack(videoIndex);
                if (startTime > 0) {
                    extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                } else {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                }
                MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);

                MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
                outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    outputFormat.setInteger("stride", resultWidth + 32);
                    outputFormat.setInteger("slice-height", resultHeight);
                }

                MediaCodec encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

                InputSurface inputSurface;
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
                    outputSurface = new OutputSurface(resultWidth, resultHeight, rotateRender);
                }
                decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
                decoder.start();

                final int TIMEOUT_USEC = 2500;
                ByteBuffer[] decoderInputBuffers = null;
                ByteBuffer[] encoderOutputBuffers = null;
                ByteBuffer[] encoderInputBuffers = null;
                if (Build.VERSION.SDK_INT < 21) {
                    decoderInputBuffers = decoder.getInputBuffers();
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (Build.VERSION.SDK_INT < 18) {
                        encoderInputBuffers = encoder.getInputBuffers();
                    }
                }

                boolean outputDone = false;
                boolean inputDone = false;
                boolean decoderDone = false;

                while (!outputDone) {
                    if (!inputDone) {
                        boolean eof = false;
                        int index = extractor.getSampleTrackIndex();
                        if (index == videoIndex) {
                            int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                            if (inputBufIndex >= 0) {
                                ByteBuffer inputBuf;
                                if (Build.VERSION.SDK_INT < 21) {
                                    inputBuf = decoderInputBuffers[inputBufIndex];
                                } else {
                                    inputBuf = decoder.getInputBuffer(inputBufIndex);
                                }
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
                            eof = true;
                        }
                        if (eof) {
                            int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                            if (inputBufIndex >= 0) {
                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            }
                        }
                    }

                    boolean decoderOutputAvailable = !decoderDone;
                    boolean encoderOutputAvailable = true;

                    while (decoderOutputAvailable || encoderOutputAvailable) {


                    }
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }


        return false;
    }


    private int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo lastCodecInfo = null;
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    lastCodecInfo = codecInfo;
                    if (!lastCodecInfo.getName().equals("OMX.SEC.avc.enc")) {
                        return lastCodecInfo;
                    } else if (lastCodecInfo.getName().equals("OMX.SEC.AVC.Encoder")) {
                        return lastCodecInfo;
                    }
                }
            }
        }
        return lastCodecInfo;
    }


    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int lastColorFormat = 0;
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                lastColorFormat = colorFormat;
                if (!(codecInfo.getName().equals("OMX.SEC.AVC.Encoder") && colorFormat == 19)) {
                    return colorFormat;
                }
            }
        }
        return lastColorFormat;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    interface CompressProgressListener {
        void onProgress(float percent);
    }
}
