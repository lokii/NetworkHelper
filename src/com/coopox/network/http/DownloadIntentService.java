package com.coopox.network.http;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: lokii
 * Date: 15/2/1
 */
public class DownloadIntentService extends IntentService implements HttpDownloader.DownloadListener, HttpDownloader.WorkFlowController {
    private static final String TAG = "DownloadService";

    private volatile boolean mCancelCurrentTask;

    public DownloadIntentService() {
        super("DownloadService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null != intent && intent.getBooleanExtra(IPCConstants.EXTRA_CANCEL, false)) {
            mCancelCurrentTask = true;
        }
        // TODO: send MSG_WAITING to client.
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (null != intent) {
            String outputPath = intent.getStringExtra(IPCConstants.EXTRA_OUTPUT_PATH);
            String urlToDownload = intent.getDataString();
            ResultReceiver receiver = intent.getParcelableExtra(IPCConstants.EXTRA_RECEIVER);
            if (null != outputPath && null != urlToDownload && null != receiver) {
                HttpDownloader.downloadFile(outputPath, urlToDownload, this, this);
            }
        }
    }

    @Override
    public void onDownloadStart(String url, String outputPath, Object data) {
        if (data instanceof ResultReceiver) {
            ResultReceiver receiver = (ResultReceiver) data;
            Bundle bundle = createCommonBundle(url, outputPath);
            receiver.send(IPCConstants.MSG_STARTED, bundle);
        }
    }

    @Override
    public void onUpdateProgress(String url, String outputPath, int progress, Object data) {
        if (data instanceof ResultReceiver) {
            ResultReceiver receiver = (ResultReceiver) data;
            Bundle bundle = createCommonBundle(url, outputPath);
            bundle.putInt(IPCConstants.KEY_PROGRESS, progress);
            receiver.send(IPCConstants.MSG_UPDATE_PROGRESS, bundle);
        }
    }

    @Override
    public void onDownloadSuccess(String url, String outputPath, Object data) {
        if (data instanceof ResultReceiver) {
            ResultReceiver receiver = (ResultReceiver) data;
            Bundle bundle = createCommonBundle(url, outputPath);
            receiver.send(IPCConstants.MSG_SUCCESS, bundle);
        }
    }

    @Override
    public void onDownloadCancelled(String url, String outputPath, Object data) {
        if (data instanceof ResultReceiver) {
            ResultReceiver receiver = (ResultReceiver) data;
            Bundle bundle = createCommonBundle(url, outputPath);
            receiver.send(IPCConstants.MSG_CANCELLED, bundle);
        }
    }

    @Override
    public void onDownloadFailed(String url, String outputPath, int errCode, Object data) {
        if (data instanceof ResultReceiver) {
            ResultReceiver receiver = (ResultReceiver) data;
            Bundle bundle = createCommonBundle(url, outputPath);
            bundle.putInt(IPCConstants.KEY_ERROR_CODE, errCode);
            receiver.send(IPCConstants.MSG_FAILED, bundle);
        }
    }

    private Bundle createCommonBundle(String url, String outputPath) {
        Bundle data = new Bundle();
        data.putString(IPCConstants.KEY_URL, url);
        data.putString(IPCConstants.KEY_OUTPUT, outputPath);
        return data;
    }

    @Override
    public boolean isCancelled(String url, String outputPath) {
        return mCancelCurrentTask;
    }
}
