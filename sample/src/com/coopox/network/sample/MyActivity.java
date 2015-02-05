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
import android.widget.Toast;
import com.coopox.network.http.DownloadService;
import com.coopox.network.http.IPCConstants;

import java.io.File;

public class MyActivity extends Activity implements View.OnClickListener {

    private ProgressBar mProgressBar;
    private EditText mEditText;

    private class DownloadReceiver extends ResultReceiver {
        public DownloadReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            Log.d("handleMessage", "Download Message: " + resultCode);
            if (IPCConstants.MSG_UPDATE_PROGRESS != resultCode) {
                Toast.makeText(MyActivity.this, "Download Message Code " + resultCode, Toast.LENGTH_SHORT).show();
            }
            switch (resultCode) {
                case IPCConstants.MSG_UPDATE_PROGRESS:
                    Number progress = (Number) resultData.get(IPCConstants.KEY_PROGRESS);
                    Log.d("handleMessage", "Downloaded " + progress + "%");
                    mProgressBar.setProgress(progress.intValue());
                    break;
                case IPCConstants.MSG_CANCELLED:
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
                    String fileName = URLUtil.guessFileName(url, null, null);
//                    File output = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    File output = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
                    intent.putExtra(IPCConstants.EXTRA_OUTPUT_PATH, output.getPath());
                    intent.putExtra(IPCConstants.EXTRA_RECEIVER, new DownloadReceiver(new Handler()));
                    startService(intent);
                }
                break;
            case R.id.stop:
                if (URLUtil.isHttpUrl(url)) {
                    Intent intent = new Intent(this, DownloadService.class);
                    intent.setData(Uri.parse(url));
                    intent.putExtra(IPCConstants.EXTRA_CANCEL, true);
                    String fileName = URLUtil.guessFileName(url, null, null);
//                    File output = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    File output = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
                    intent.putExtra(IPCConstants.EXTRA_OUTPUT_PATH, output.getPath());
                    intent.putExtra(IPCConstants.EXTRA_RECEIVER, new DownloadReceiver(new Handler()));
                    startService(intent);
                }
                break;
        }
    }
}
