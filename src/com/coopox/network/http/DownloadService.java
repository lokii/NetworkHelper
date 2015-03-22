package com.coopox.network.http;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * User: lokii
 * Date: 15/2/1
 */
public class DownloadService extends Service {
    private static final String TAG = "DownloadService";

    private static final int MAX_THREADS = 3;
    private static int ID = 0x10ada11;
    private NotificationCompat.Builder mNotifyBuilder;
    private NotificationManager mNotifyManager;

    class DownloadTask {
        public DownloadTask(String url, String output, ResultReceiver receiver) {
            mUrl = url;
            mOutput = output;
            mReceiver = receiver;
            mNotifyID = ID++;
        }

        public String keyGen() {
            return mUrl + mOutput;
        }

        public final String mUrl;
        public final String mOutput;
        public final ResultReceiver mReceiver;
        public DownloadFileRunnable mRunnable;
        public final int mNotifyID;
    }

    private Hashtable<String, DownloadTask> mTasks = new Hashtable<String, DownloadTask>();
    private ExecutorService mThreadPool = Executors.newFixedThreadPool(MAX_THREADS);

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
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null != intent) {
            String url = intent.getDataString();
            String outputPath = intent.getStringExtra(IPCConstants.EXTRA_OUTPUT_PATH);
            ResultReceiver receiver = intent.getParcelableExtra(IPCConstants.EXTRA_RECEIVER);

            boolean cancel = intent.getBooleanExtra(IPCConstants.EXTRA_CANCEL, false);
            boolean foreground = intent.getBooleanExtra(IPCConstants.EXTRA_FOREGROUND, true);

            if (!TextUtils.isEmpty(url) && !TextUtils.isEmpty(outputPath)) {
                String key = url + outputPath;
                if (cancel) {
                    if (mTasks.containsKey(key)) {
                        stopDownload(mTasks.get(key));
                    }
                    cancelAndStopSelf();
                } else if (null != receiver) {
                    DownloadTask task = mTasks.get(key);
                    if (null == task) {
                        task = new DownloadTask(url, outputPath, receiver);
                        startDownload(task);
                    } else {
                        receiver.send(IPCConstants.MSG_QUEUE_UP,
                                createCommonBundle(url, outputPath));
                        Log.d(TAG, String.format("Task %s was already started", url));
                    }

                    if (foreground) {
                        mNotifyBuilder = new NotificationCompat.Builder(this)
                                .setContentTitle("Downloading")
                                .setContentText(url)
                                .setSmallIcon(android.R.drawable.stat_sys_download);
                        startForeground(task.mNotifyID, mNotifyBuilder.build());
                        mNotifyManager =
                                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    }
                } else {
                    Log.e(TAG, "Invalid params for download task!");
                }
            } else {
                Log.e(TAG, "Invalid params for download task!");
            }
        }

        return START_NOT_STICKY;
    }

    private void cancelAndStopSelf() {
        if (mTasks.isEmpty()) {
            stopSelf();
        }
    }

    private void startDownload(DownloadTask task) {
        if (null != task) {
            task.mRunnable = new DownloadFileRunnable(this, task);
            mTasks.put(task.keyGen(), task);

            mThreadPool.execute(task.mRunnable);
            task.mReceiver.send(IPCConstants.MSG_WAITING,
                    createCommonBundle(task.mUrl, task.mOutput));
        }
    }

    private void stopDownload(DownloadTask task) {
        if (null != task) {
            if (null != task.mRunnable) {
                // Stop when it's Downloading, it will remove self at end of download
                task.mRunnable.stop();

                // If task is not running, then remove it directly.
                if (!task.mRunnable.isRunning()) {
                    mTasks.remove(task.keyGen());
                    task.mReceiver.send(IPCConstants.MSG_CANCELLED,
                            createCommonBundle(task.mUrl, task.mOutput));
                }
            }
        }
    }

    private static Bundle createCommonBundle(String url, String outputPath) {
        Bundle data = new Bundle();
        data.putString(IPCConstants.KEY_URL, url);
        data.putString(IPCConstants.KEY_OUTPUT, outputPath);
        return data;
    }

    static class DownloadFileRunnable implements Runnable,
            HttpDownloader.DownloadListener, HttpDownloader.WorkFlowController {

        private final DownloadTask mTask;
        private final WeakReference<Context> mContextRef;
        private volatile boolean mStoped;
        private volatile boolean mIsRunning;

        public DownloadFileRunnable(Context context, DownloadTask task) {
            mContextRef = new WeakReference<Context>(context);
            mTask = task;
        }

        public void stop() {
            mStoped = true;
        }

        public boolean isRunning() {
            return mIsRunning;
        }

        @Override
        public void run() {
            if (!mStoped) {
                mIsRunning = true;
                HttpDownloader.downloadFile(mTask.mUrl, mTask.mOutput, this, this);
                mIsRunning = false;

                clearTask();
                checkWhetherStopService();
            } else {
                mTask.mReceiver.send(IPCConstants.MSG_CANCELLED,
                        createCommonBundle(mTask.mUrl, mTask.mOutput));
                clearTask();
                checkWhetherStopService();
            }
        }

        private DownloadTask clearTask() {
            // Download finish, remove the task from queue.
            Context context = mContextRef.get();
            if (context instanceof DownloadService) {
                DownloadService service = (DownloadService)context;
                return service.mTasks.remove(mTask.keyGen());
            }
            return null;
        }

        private void checkWhetherStopService() {
            Context context = mContextRef.get();
            if (context instanceof DownloadService) {
                DownloadService service = (DownloadService)context;
                service.cancelAndStopSelf();
            }
        }

        @Override
        public void onDownloadStart(String url, String outputPath, Object data) {
            Bundle bundle = createCommonBundle(url, outputPath);
            mTask.mReceiver.send(IPCConstants.MSG_STARTED, bundle);
        }

        @Override
        public void onUpdateProgress(String url, String outputPath, int progress, Object data) {
            Bundle bundle = createCommonBundle(url, outputPath);
            bundle.putInt(IPCConstants.KEY_PROGRESS, progress);
            mTask.mReceiver.send(IPCConstants.MSG_UPDATE_PROGRESS, bundle);
            Context context = mContextRef.get();
            if (context instanceof DownloadService) {
                DownloadService service = (DownloadService)context;
                if (null != service.mNotifyBuilder && null != service.mNotifyManager) {
                    service.mNotifyBuilder.setProgress(100, progress, progress > 100);
                    service.mNotifyManager.notify(mTask.mNotifyID,
                            service.mNotifyBuilder.build());
                }
            }
        }

        @Override
        public void onDownloadSuccess(String url, String outputPath, Object data) {
            Bundle bundle = createCommonBundle(url, outputPath);
            mTask.mReceiver.send(IPCConstants.MSG_SUCCESS, bundle);
            removeNotification();
        }

        @Override
        public void onDownloadCancelled(String url, String outputPath, Object data) {
            Bundle bundle = createCommonBundle(url, outputPath);
            mTask.mReceiver.send(IPCConstants.MSG_CANCELLED, bundle);
            removeNotification();
        }

        @Override
        public void onDownloadFailed(String url, String outputPath, int errCode, Object data) {
            Bundle bundle = createCommonBundle(url, outputPath);
            bundle.putInt(IPCConstants.KEY_ERROR_CODE, errCode);
            mTask.mReceiver.send(IPCConstants.MSG_FAILED, bundle);
            notifyDownloadFailed();
        }

        @Override
        public boolean isCancelled(String url, String outputPath) {
            return mStoped;
        }

        private void removeNotification() {
            Context context = mContextRef.get();
            if (context instanceof DownloadService) {
                DownloadService service = (DownloadService) context;
                if (null != service.mNotifyManager) {
                    service.mNotifyManager.cancel(mTask.mNotifyID);
                }
            }
        }

        private void notifyDownloadFailed() {
            Context context = mContextRef.get();
            if (context instanceof DownloadService) {
                DownloadService service = (DownloadService) context;
                if (null != service.mNotifyBuilder && null != service.mNotifyManager) {
                    service.mNotifyBuilder.setContentTitle("Download Failed!");
                    service.mNotifyManager.notify(mTask.mNotifyID,
                            service.mNotifyBuilder.build());
                }
            }
        }
    }
}
