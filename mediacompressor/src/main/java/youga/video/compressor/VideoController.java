package youga.video.compressor;

import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.support.annotation.RequiresApi;

/**
 * @author: YougaKingWu@gmail.com
 * @created on: 2018/11/14 14:57
 * @description:
 */
public class VideoController {

    static final int COMPRESS_QUALITY_HIGH = 1;
    static final int COMPRESS_QUALITY_MEDIUM = 2;
    static final int COMPRESS_QUALITY_LOW = 3;


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


        return false;
    }


    interface CompressProgressListener {
        void onProgress(float percent);
    }
}
