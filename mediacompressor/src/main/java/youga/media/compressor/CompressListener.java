package youga.media.compressor;

import java.io.File;

/**
 * author: YougaKingWu@gmail.com
 * created on: 2018/03/08 13:38
 * description:
 */
public interface CompressListener {
    void onStart();

    void onSuccess(File file);

    void onFail();

    void onProgress(float percent);
}
