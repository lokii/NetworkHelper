package com.coopox.network.sample;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.coopox.network.http.DownloadClient;

import java.io.File;

public class MyActivity extends Activity implements View.OnClickListener,
DownloadClient.DownloadListener {

    private ProgressBar mProgressBar;
    private EditText mEditText;
    private DownloadClient mDownloadClient;

    @Override
    public void onDownloadStart(String url, String outputPath, Object data) {
        Toast.makeText(this,
                String.format("Start download %s", url),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpdateProgress(String url, String outputPath, int progress, Object data) {
        Log.d("handleMessage", "Downloaded " + progress + "%");
        mProgressBar.setProgress(progress);
    }

    @Override
    public void onDownloadSuccess(String url, String outputPath, Object data) {
        Toast.makeText(this,
                String.format("Download %s", url),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDownloadCancelled(String url, String outputPath, Object data) {
        mProgressBar.setProgress(0);
        Toast.makeText(this,
                String.format("Cancel download %s", url),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDownloadFailed(String url, String outputPath, int errCode, Object data) {
        mProgressBar.setProgress(0);
        Toast.makeText(this,
                String.format("Error(%d) when download %s", errCode, url),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDownloadWaiting(String url, String outputPath, Object data) {
        Toast.makeText(this,
                String.format("Waiting for download %s", url),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mDownloadClient = new DownloadClient(this);
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
            {
                String fileName = URLUtil.guessFileName(url, null, null);
                File output = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
                mDownloadClient.startDownloadInParallel(url, output.getPath(), this);
            }
                break;
            case R.id.stop:
            {
                String fileName = URLUtil.guessFileName(url, null, null);
                File output = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
                mDownloadClient.stopDownloadInParallel(url, output.getPath(), this);
            }
                break;
        }
    }
}
