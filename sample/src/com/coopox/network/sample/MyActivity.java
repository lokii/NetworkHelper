package com.coopox.network.sample;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import com.coopox.network.http.DownloadService;

import java.io.File;

public class MyActivity extends Activity implements View.OnClickListener {

    private ProgressBar mProgressBar;
    private EditText mEditText;
    private Handler mResponseHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Log.d("handleMessage", "Download Message: " + msg.what);
            switch (msg.what) {
                case DownloadService.MSG_PROGRESS_UPDATE:
                    Log.d("handleMessage", "Downloaded " + msg.arg2 + "%");
                    mProgressBar.setProgress(msg.arg2);
                    break;
                case DownloadService.MSG_CANCELLED:
                    mProgressBar.setProgress(0);
                    break;
            }
        }
    };

    private class DownloadReceiver extends ResultReceiver {
        public DownloadReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            Log.d("handleMessage", "Download Message: " + resultCode);
            switch (resultCode) {
                case DownloadService.MSG_UPDATE_PROGRESS:
                    Number progress = (Number) resultData.get(DownloadService.KEY_PROGRESS);
                    Log.d("handleMessage", "Downloaded " + progress + "%");
                    mProgressBar.setProgress(progress.intValue());
                    break;
                case DownloadService.MSG_CANCELLED:
                    mProgressBar.setProgress(0);
                    break;
            }
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mProgressBar = (ProgressBar) findViewById(R.id.progress);
        mEditText = (EditText) findViewById(R.id.url_input);
        Button start = (Button) findViewById(R.id.start);
        start.setOnClickListener(this);
        Button stop = (Button) findViewById(R.id.stop);
        stop.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        String url = mEditText.getText().toString();
        switch (v.getId()) {
            case R.id.start:
                if (URLUtil.isHttpUrl(url)) {
                    Intent intent = new Intent(this, DownloadService.class);
                    intent.setData(Uri.parse(url));
                    File dir = getExternalCacheDir();
                    File output = new File(dir, "test.tmp");
                    intent.putExtra(DownloadService.EXTRA_OUTPUT_PATH, output.getPath());
                    intent.putExtra(DownloadService.EXTRA_RECEIVER, new DownloadReceiver(new Handler()));
                    startService(intent);
                }
                break;
            case R.id.stop:
                if (URLUtil.isHttpUrl(url)) {
                    Intent intent = new Intent(this, DownloadService.class);
                    intent.setData(Uri.parse(url));
                    intent.putExtra(DownloadService.EXTRA_CANCEL, true);
                    File dir = getExternalCacheDir();
                    File output = new File(dir, "test.tmp");
                    intent.putExtra(DownloadService.EXTRA_OUTPUT_PATH, output.getPath());
                    intent.putExtra(DownloadService.EXTRA_RECEIVER, new DownloadReceiver(new Handler()));
                    startService(intent);
                }
                break;
        }
    }
}
