package youga.media.compressor;

import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.io.File;

/**
 * author: YougaKingWu@gmail.com
 * created on: 2018/03/02 17:45
 * description:
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class MediaCompress {

    private static final String TAG = MediaCompress.class.getSimpleName();



    public static VideoCompressTask compressVideoLow(String srcPath, String destDir, CompressListener listener) {
        VideoCompressTask task =  new VideoCompressTask(listener, MediaController.COMPRESS_QUALITY_LOW);
        task.execute(srcPath, destDir);
        return task;
    }


    private static class VideoCompressTask extends AsyncTask<String, Float, File> {
        private CompressListener mListener;
        private int mQuality;

        public VideoCompressTask(CompressListener listener, int quality) {
            mListener = listener;
            mQuality = quality;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mListener != null) {
                mListener.onStart();
            }
        }

        @Override
        protected File doInBackground(String... paths) {
            return MediaController.getInstance().convertVideo(paths[0], paths[1], mQuality, new CompressProgressListener() {
                @Override
                public void onProgress(float percent) {
                    publishProgress(percent);
                }
            });
        }

        @Override
        protected void onProgressUpdate(Float... percent) {
            super.onProgressUpdate(percent);
            if (mListener != null) {
                mListener.onProgress(percent[0]);
            }
        }

        @Override
        protected void onPostExecute(File file) {
            super.onPostExecute(file);
            if (mListener != null) {
                if (file != null) {
                    mListener.onSuccess(file);
                } else {
                    mListener.onFail();
                }
            }
        }
    }
}
