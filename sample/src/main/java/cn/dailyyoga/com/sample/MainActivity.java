package cn.dailyyoga.com.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.File;

import youga.media.compressor.CompressListener;
import youga.media.compressor.MediaCompress;

public class MainActivity extends AppCompatActivity {

    private static final int VIDEO_REQUEST_CODE = 22;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClick(View view) {

        Intent videoIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        videoIntent.setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*");
        startActivityForResult(videoIntent, VIDEO_REQUEST_CODE);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VIDEO_REQUEST_CODE && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            String path = UriUtil.getPath(this, uri);
            File dir = getExternalCacheDir();
            if (dir == null) return;
            MediaCompress.compressVideoLow(path, dir.getAbsolutePath(), new CompressListener() {
                @Override
                public void onStart() {

                }

                @Override
                public void onSuccess(File file) {

                }

                @Override
                public void onFail() {

                }

                @Override
                public void onProgress(float percent) {

                }
            });
        }
    }
}
