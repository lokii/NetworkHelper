package com.coopox.network.http;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 * Created with IntelliJ IDEA.
 * User: lokii
 * Date: 15/2/1
 */
public class DownloadIntentService extends IntentService implements HttpDownloader.DownloadListener, HttpDownloader.WorkFlowController {
    private static final String TAG = "DownloadService";
    private static final int ID = 0x10ad;

    private volatile boolean mCancelCurrentTask;
    private NotificationCompat.Builder mNotifyBuilder;
    private NotificationManager mNotifyManager;

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
        if (null != intent) {
            if (intent.getBooleanExtra(IPCConstants.EXTRA_CANCEL, false)) {
                mCancelCurrentTask = true;
            }
        }
        boolean foreground = intent.getBooleanExtra(IPCConstants.EXTRA_FOREGROUND, true);
        if (foreground) {
            mNotifyBuilder = new NotificationCompat.Builder(this)
                    .setContentTitle("正在下载")
                    .setContentText(intent.getDataString())
                    .setSmallIcon(android.R.drawable.stat_sys_download);
            startForeground(ID, mNotifyBuilder.build());
            mNotifyManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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
                HttpDownloader.downloadFile(urlToDownload, outputPath, receiver,
                        this, this);
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
            if (null != mNotifyBuilder && null != mNotifyManager) {
                mNotifyBuilder.setProgress(100, progress, progress > 100);
                mNotifyManager.notify(ID, mNotifyBuilder.build());
            }
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
