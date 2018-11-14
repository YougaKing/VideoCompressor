//package youga.media.compressor;
//
//import android.media.MediaCodec;
//import android.media.MediaExtractor;
//import android.media.MediaFormat;
//import android.media.MediaMuxer;
//import android.os.Build;
//import android.support.annotation.RequiresApi;
//import android.util.Log;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//
///**
// * author: YougaKingWu@gmail.com
// * created on: 2018/03/02 17:48
// * description:
// */
//@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
//public class VideoController {
//    private static final String TAG = "VideoController";
//
//    static final int COMPRESS_QUALITY_HIGH = 1;
//    static final int COMPRESS_QUALITY_MEDIUM = 2;
//    static final int COMPRESS_QUALITY_LOW = 3;
//    private static final String MIME_TYPE = "video/avc";
//    private final static int PROCESSOR_TYPE_OTHER = 0;
//    private final static int PROCESSOR_TYPE_QCOM = 1;
//    private final static int PROCESSOR_TYPE_INTEL = 2;
//    private final static int PROCESSOR_TYPE_MTK = 3;
//    private final static int PROCESSOR_TYPE_SEC = 4;
//    private final static int PROCESSOR_TYPE_TI = 5;
//
//    private static volatile VideoController mInstance;
//
//
//    public static VideoController getInstance() {
//        if (mInstance == null) {
//            synchronized (VideoController.class) {
//                if (mInstance == null) {
//                    mInstance = new VideoController();
//                }
//            }
//        }
//        return mInstance;
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
//    public File convertVideo(final String sourcePath, String destDir, int quality, CompressProgressListener listener) {
//
//        try {
//            File videoFile = new File(destDir, "videoFile.mp4");
//            File audioFile = new File(destDir, "audioFile.mp3");
//            File combineVideoFile = new File(destDir, "combineVideoFile.mp4");
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
//                extractVideo(sourcePath, videoFile.getAbsolutePath());
//                extractAudio(sourcePath, audioFile.getAbsolutePath());
//                combineVideo(videoFile, audioFile, combineVideoFile);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//
//    /**
//     * 提取视频
//     *
//     * @param sourceVideoPath 原始视频文件
//     * @exception Exception 出错
//     */
//    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
//    public static void extractVideo(String sourceVideoPath, String outVideoPath) throws Exception {
//        MediaExtractor sourceMediaExtractor = new MediaExtractor();
//        sourceMediaExtractor.setDataSource(sourceVideoPath);
//        int numTracks = sourceMediaExtractor.getTrackCount();
//        int sourceVideoTrackIndex = -1; // 原始视频文件视频轨道参数
//        for (int i = 0; i < numTracks; ++i) {
//            MediaFormat format = sourceMediaExtractor.getTrackFormat(i);
//            String mime = format.getString(MediaFormat.KEY_MIME);
//            Log.d(TAG, "MediaFormat: " + mime);
//            if (mime.startsWith("video/")) {
//                sourceVideoTrackIndex = i;
//                sourceMediaExtractor.selectTrack(sourceVideoTrackIndex);
//                Log.d(TAG, "selectTrack index=" + i + "; format: " + mime);
//                break;
//            }
//        }
//
//        MediaMuxer outputMediaMuxer = new MediaMuxer(outVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//        outputMediaMuxer.addTrack(sourceMediaExtractor.getTrackFormat(sourceVideoTrackIndex));
//        outputMediaMuxer.start();
//
//        ByteBuffer inputBuffer = ByteBuffer.allocate(1024 * 1024 * 2); // 分配的内存要尽量大一些
//        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//        int sampleSize;
//        while ((sampleSize = sourceMediaExtractor.readSampleData(inputBuffer, 0)) >= 0) {
//            long presentationTimeUs = sourceMediaExtractor.getSampleTime();
//            info.offset = 0;
//            info.size = sampleSize;
//            info.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
//            info.presentationTimeUs = presentationTimeUs;
//            outputMediaMuxer.writeSampleData(sourceVideoTrackIndex, inputBuffer, info);
//            sourceMediaExtractor.advance();
//        }
//
//        outputMediaMuxer.stop();
//        outputMediaMuxer.release();    // 停止并释放 MediaMuxer
//        sourceMediaExtractor.release();
//    }
//
//
//    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
//    public void extractAudio(String sourceVideoPath, String sourceMP4Path) throws IOException {
//        MediaExtractor sourceMediaExtractor = new MediaExtractor();
//        sourceMediaExtractor.setDataSource(sourceVideoPath);
//
//        int audioIndex = -1;
//        int trackCount = sourceMediaExtractor.getTrackCount();
//        for (int i = 0; i < trackCount; i++) {
//            MediaFormat trackFormat = sourceMediaExtractor.getTrackFormat(i);
//            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
//            Log.d(TAG, "MediaFormat: " + mime);
//            if (mime.startsWith("audio/")) {
//                audioIndex = i;
//                sourceMediaExtractor.selectTrack(audioIndex);
//                Log.d(TAG, "selectTrack index=" + i + "; format: " + mime);
//            }
//        }
//
//
//        MediaMuxer mediaMuxer = new MediaMuxer(sourceMP4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//        int writeAudioIndex = mediaMuxer.addTrack(sourceMediaExtractor.getTrackFormat(audioIndex));
//        mediaMuxer.start();
//
//        ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
//        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//        long stampTime = 0;
//        //获取帧之间的间隔时间
//        sourceMediaExtractor.readSampleData(byteBuffer, 0);
//        if (sourceMediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
//            sourceMediaExtractor.advance();
//        }
//        sourceMediaExtractor.readSampleData(byteBuffer, 0);
//        long secondTime = sourceMediaExtractor.getSampleTime();
//        sourceMediaExtractor.advance();
//        sourceMediaExtractor.readSampleData(byteBuffer, 0);
//        long thirdTime = sourceMediaExtractor.getSampleTime();
//        stampTime = Math.abs(thirdTime - secondTime);
//        Log.e("fuck", stampTime + "");
//
//        sourceMediaExtractor.unselectTrack(audioIndex);
//        sourceMediaExtractor.selectTrack(audioIndex);
//
//        while (true) {
//            int readSampleSize = sourceMediaExtractor.readSampleData(byteBuffer, 0);
//            if (readSampleSize < 0) {
//                break;
//            }
//            sourceMediaExtractor.advance();
//
//            bufferInfo.size = readSampleSize;
//            bufferInfo.flags = sourceMediaExtractor.getSampleFlags();
//            bufferInfo.offset = 0;
//            bufferInfo.presentationTimeUs += stampTime;
//
//            mediaMuxer.writeSampleData(writeAudioIndex, byteBuffer, bufferInfo);
//        }
//        mediaMuxer.stop();
//        mediaMuxer.release();
//        sourceMediaExtractor.release();
//    }
//
//
//    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
//    private void combineVideo(File videoFile, File audioFile, File combineVideoFile) {
//        try {
//            MediaExtractor videoExtractor = new MediaExtractor();
//            videoExtractor.setDataSource(videoFile.getAbsolutePath());
//            MediaFormat videoFormat = null;
//            int videoTrackIndex = -1;
//            int videoTrackCount = videoExtractor.getTrackCount();
//            for (int i = 0; i < videoTrackCount; i++) {
//                videoFormat = videoExtractor.getTrackFormat(i);
//                String mimeType = videoFormat.getString(MediaFormat.KEY_MIME);
//                if (mimeType.startsWith("video/")) {
//                    videoTrackIndex = i;
//                    break;
//                }
//            }
//
//            MediaExtractor audioExtractor = new MediaExtractor();
//            audioExtractor.setDataSource(audioFile.getAbsolutePath());
//            MediaFormat audioFormat = null;
//            int audioTrackIndex = -1;
//            int audioTrackCount = audioExtractor.getTrackCount();
//            for (int i = 0; i < audioTrackCount; i++) {
//                audioFormat = audioExtractor.getTrackFormat(i);
//                String mimeType = audioFormat.getString(MediaFormat.KEY_MIME);
//                if (mimeType.startsWith("audio/")) {
//                    audioTrackIndex = i;
//                    break;
//                }
//            }
//
//            videoExtractor.selectTrack(videoTrackIndex);
//            audioExtractor.selectTrack(audioTrackIndex);
//
//            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
//            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
//
//            MediaMuxer mediaMuxer = new MediaMuxer(combineVideoFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//            int writeVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
//            int writeAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
//            mediaMuxer.start();
//
//            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
//            long sampleTime = 0;
//            videoExtractor.readSampleData(byteBuffer, 0);
//            if (videoExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
//                videoExtractor.advance();
//            }
//            videoExtractor.readSampleData(byteBuffer, 0);
//            long secondTime = videoExtractor.getSampleTime();
//            videoExtractor.advance();
//            long thirdTime = videoExtractor.getSampleTime();
//            sampleTime = Math.abs(thirdTime - secondTime);
//            videoExtractor.unselectTrack(videoTrackIndex);
//            videoExtractor.selectTrack(videoTrackIndex);
//
//            while (true) {
//                int readVideoSampleSize = videoExtractor.readSampleData(byteBuffer, 0);
//                if (readVideoSampleSize < 0) {
//                    break;
//                }
//                videoBufferInfo.size = readVideoSampleSize;
//                videoBufferInfo.presentationTimeUs += sampleTime;
//                videoBufferInfo.offset = 0;
//                videoBufferInfo.flags = videoExtractor.getSampleFlags();
//                mediaMuxer.writeSampleData(writeVideoTrackIndex, byteBuffer, videoBufferInfo);
//                videoExtractor.advance();
//            }
//
//            while (true) {
//                int readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0);
//                if (readAudioSampleSize < 0) {
//                    break;
//                }
//
//                audioBufferInfo.size = readAudioSampleSize;
//                audioBufferInfo.presentationTimeUs += sampleTime;
//                audioBufferInfo.offset = 0;
//                audioBufferInfo.flags = videoExtractor.getSampleFlags();
//                mediaMuxer.writeSampleData(writeAudioTrackIndex, byteBuffer, audioBufferInfo);
//                audioExtractor.advance();
//            }
//
//            mediaMuxer.stop();
//            mediaMuxer.release();
//            videoExtractor.release();
//            audioExtractor.release();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    interface CompressProgressListener {
//        void onProgress(float percent);
//    }
//}
