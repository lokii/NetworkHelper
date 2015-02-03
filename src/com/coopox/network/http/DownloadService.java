package com.coopox.network.http;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.util.Log;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
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

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    public static final int MSG_STARTED = 0x0;
    public static final int MSG_PROGRESS_UPDATE = 0x1;
    public static final int MSG_CANCELLED = 0x2;
    public static final int MSG_SUCCESS = 0x3;
    public static final int MSG_FAILED = 0x4;

    public static final String ACTION_STATED = "DownloadService.Started";
    public static final String ACTION_PROGRESS_UPDATE = "DownloadService.Update";
    public static final String ACTION_CANCELLED = "DownloadService.Cancelled";
    public static final String ACTION_SUCCESS = "DownloadService.Success";
    public static final String ACTION_FAILED = "DownloadService.Failed";

    public static final String[] MSG_TO_ACTION = {
            ACTION_STATED,
            ACTION_PROGRESS_UPDATE,
            ACTION_CANCELLED,
            ACTION_SUCCESS,
            ACTION_FAILED
    };

    public static final String EXTRA_URL = "URL";
    public static final String EXTRA_OUTPUT_PATH = "OutputPath";
    public static final String EXTRA_MESSENGER = "Messenger";
    public static final String EXTRA_STOP = "Stop";
    private static final int MAX_THREADS = 3;

    class DownloadTask {
        public DownloadTask(String url, String output, Messenger messenger) {
            mUrl = url;
            mOutput = output;
            mMessenger = messenger;
        }

        public final String mUrl;
        public final String mOutput;
        public final Messenger mMessenger;
        public HttpURLConnection mConnection;
        public DownloadFileTask mAsyncTask;
        public DownloadFileRunnable mRunnable;
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
            String url = intent.getStringExtra(EXTRA_URL);
            String outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH);
            Messenger messenger = intent.getParcelableExtra(EXTRA_MESSENGER);
            boolean needStop = intent.getBooleanExtra(EXTRA_STOP, false);

            if (null != url) {
                if (needStop) {
                    if (mTasks.containsKey(url)) {
                        stopDownload(mTasks.get(url));
                    }
                } else if (null != outputPath) {
                    DownloadTask task = mTasks.get(url);
                    if (null == task) {
                        task = new DownloadTask(url, outputPath, messenger);
                        mTasks.put(url, task);
                        startDownload(task);
                    } else {
                        Log.d(TAG, String.format("Task %s was already started", url));
                    }
                }
            }

            if (mTasks.isEmpty()) {
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startDownload(DownloadTask task) {
        if (null != task) {
            task.mRunnable = new DownloadFileRunnable(this, task);
            mThreadPool.execute(task.mRunnable);
        }
    }

    private void stopDownload(DownloadTask task) {
        if (null != task && null != task.mRunnable) {
            task.mRunnable.stop();
        }
    }

    static class DownloadFileRunnable implements Runnable {

        private static final int CACHE_BLOCK_SIZE = 4096;
        private final DownloadTask mTask;
        private final WeakReference<Context> mContextRef;
        private volatile boolean mStoped;

        public DownloadFileRunnable(Context context, DownloadTask task) {
            mContextRef = new WeakReference<Context>(context);
            mTask = task;
        }

        public void stop() {
            mStoped = true;
        }

        @Override
        public void run() {
            if (mStoped) {
                onDownloadCancelled();
                return;
            }

            DownloadTask task = mTask;
            FileOutputStream outputStream = null;
            InputStream inputStream = null;
            Exception exception = null;

            onStartDownload();
            try {
                URL url = new URL(task.mUrl);
                task.mConnection = (HttpURLConnection) url.openConnection();
                task.mConnection.setConnectTimeout(CONNECT_TIMEOUT);
                task.mConnection.setReadTimeout(READ_TIMEOUT);
                task.mConnection.setInstanceFollowRedirects(true);

                if (task.mConnection.getResponseCode() == 200) {
                    int totalSize = task.mConnection.getContentLength();

                    File outputFile = new File(task.mOutput);
                    outputStream = new FileOutputStream(outputFile);

                    inputStream = task.mConnection.getInputStream();

                    byte[] buffer = new byte[CACHE_BLOCK_SIZE];

                    int len;
                    int downloadSize = 0;
                    int lastPercent = 0;
                    while (!mStoped &&
                            (len = inputStream.read(buffer, 0, buffer.length)) != -1) {
                        outputStream.write(buffer, 0, len);
                        downloadSize += len;

                        if (totalSize > 0) {
                            int percent = (int) ((downloadSize / (float) totalSize) * 100);
                            // Update progress when percent changed.
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                sendMessageToClient(MSG_PROGRESS_UPDATE, 0, percent, mTask.mUrl, null);
                            }
                        } else {
                            sendMessageToClient(MSG_PROGRESS_UPDATE, 0, downloadSize, mTask.mUrl, null);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                exception = e;
            } finally {
                if (null != outputStream) {
                    Log.i(TAG, String.format("Close output Stream for %s", task.mUrl));
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        exception = e;
                    }
                }

                clearTask();

                if (mStoped) {
                    onDownloadCancelled();
                } else {
                    onDownloadOver(exception);
                }

                if (null != inputStream) {
                    Log.i(TAG, String.format("Close input Stream for %s", task.mUrl));
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                Log.i(TAG, String.format("Close connection for %s", task.mUrl));
                if (null != task.mConnection) {
                    task.mConnection.disconnect();
                    task.mConnection = null;
                }

                checkWhetherStopService();
            }
        }

        private void onStartDownload() {
            sendMessageToClient(MSG_STARTED, 0, 0, mTask.mUrl, null);
        }

        private void onDownloadCancelled() {
            Log.d(TAG, String.format("Task %s was cancelled", mTask.mUrl));
            sendMessageToClient(MSG_CANCELLED, 0, 0, mTask.mUrl, null);
        }

        private void onDownloadOver(Exception e) {
            Log.d(TAG, String.format("Task %s was Over", mTask.mUrl));
            if (null == e) {
                sendMessageToClient(MSG_SUCCESS, 0, 0, mTask.mUrl, null);
            } else {
                Bundle data = new Bundle();
                data.putString("error", e.toString());
                sendMessageToClient(MSG_FAILED, 0, 0, mTask.mUrl, data);
            }
        }

        private DownloadTask clearTask() {
            // Download finish, remove the task from queue.
            Context context = mContextRef.get();
            if (context instanceof DownloadService) {
                DownloadService service = (DownloadService)context;
                return service.mTasks.remove(mTask.mUrl);
            }
            return null;
        }

        private void checkWhetherStopService() {
            Context context = mContextRef.get();
            if (context instanceof DownloadService) {
                DownloadService service = (DownloadService)context;
                if (service.mTasks.isEmpty()) {
                    service.stopSelf();
                }
            }
        }

        private void sendMessageToClient(int what, int arg1, int arg2, Serializable obj, Bundle data) {
            Messenger messenger = mTask.mMessenger;
            if (null != messenger) {
                Message msg = Message.obtain();
                msg.what = what;
                msg.arg1 = arg1;
                msg.arg2 = arg2;
                msg.obj = obj;
                if (null != data) {
                    msg.setData(data);
                }

                try {
                    messenger.send(msg);
                    return;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            sendBroadcastToClient(what, arg1, arg2, obj, data);
        }

        private void sendBroadcastToClient(int what, int arg1, int arg2, Serializable obj, Bundle data) {
            Context context = mContextRef.get();
            if (null != context) {
                Intent intent = new Intent(MSG_TO_ACTION[what]);
                intent.putExtra("arg1", arg1);
                intent.putExtra("arg2", arg2);
                if (null != obj) {
                    intent.putExtra("obj", obj);
                }
                if (null != data) {
                    intent.putExtra("data", data);
                }
                context.sendBroadcast(intent);
            }
        }
    }

    static class DownloadFileTask extends AsyncTask<Object, Integer, Boolean> {

        private static final int CACHE_BLOCK_SIZE = 4096;
        private DownloadTask mTask;
        private WeakReference<Context> mContextRef;

        public DownloadFileTask(Context context, DownloadTask task) {
            mContextRef = new WeakReference<Context>(context);
            mTask = task;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            DownloadTask task = mTask;
            FileOutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                URL url = new URL(task.mUrl);
                task.mConnection = (HttpURLConnection) url.openConnection();
                task.mConnection.setConnectTimeout(CONNECT_TIMEOUT);
                task.mConnection.setReadTimeout(READ_TIMEOUT);
                task.mConnection.setInstanceFollowRedirects(true);

                if (task.mConnection.getResponseCode() == 200) {
                    int totalSize = task.mConnection.getContentLength();

                    File outputFile = new File(task.mOutput);
                    outputStream = new FileOutputStream(outputFile);

                    inputStream = task.mConnection.getInputStream();

                    byte[] buffer = new byte[CACHE_BLOCK_SIZE];

                    int len;
                    int downloadSize = 0;
                    int lastPercent = 0;
                    while ((len = inputStream.read(buffer, 0, buffer.length)) != -1) {
                        outputStream.write(buffer, 0, len);
                        downloadSize += len;

                        if (totalSize > 0) {
                            int percent = (int) ((downloadSize / (float) totalSize) * 100);
                            // Update progress when percent changed.
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                publishProgress(percent);
                            }
                        } else {
                            publishProgress(downloadSize);
                        }
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                if (null != task.mConnection) {
                    task.mConnection.disconnect();
                    task.mConnection = null;
                }

                try {
                    if (null != outputStream) {
                        outputStream.close();
                    }
                    if (null != inputStream) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return true;
        }

        @Override
        protected void onPreExecute() {
            sendMessageToClient(MSG_STARTED, 0, 0, mTask.mUrl);
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, String.format("Task %s was cancelled", mTask.mUrl));
            sendMessageToClient(MSG_CANCELLED, 0, 0, mTask.mUrl);
            removeTask();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.d(TAG, String.format("Task %s was completed", mTask.mUrl));
            if (result) {
                sendMessageToClient(MSG_SUCCESS, 0, 0, mTask.mUrl);
            } else {
                sendMessageToClient(MSG_FAILED, 0, 0, mTask.mUrl);
            }
            removeTask();
        }

        private void removeTask() {
            // Download finish, remove the task from queue.
            Context context = mContextRef.get();
            if (context instanceof DownloadService) {
                DownloadService service = (DownloadService)context;
                service.mTasks.remove(mTask.mUrl);
                if (service.mTasks.isEmpty()) {
                    service.stopSelf();
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            sendMessageToClient(MSG_PROGRESS_UPDATE, 0, values[0], mTask.mUrl);
        }

        private void sendMessageToClient(int what, int arg1, int arg2, Serializable obj) {
            Messenger messenger = mTask.mMessenger;
            if (null != messenger) {
                Message msg = Message.obtain();
                msg.what = what;
                msg.arg1 = arg1;
                msg.arg2 = arg2;
                msg.obj = obj;
                try {
                    messenger.send(msg);
                    return;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            sendBroadcastToClient(what, arg1, arg2, obj);
        }

        private void sendBroadcastToClient(int what, int arg1, int arg2, Serializable obj) {
            Context context = mContextRef.get();
            if (null != context) {
                Intent intent = new Intent(MSG_TO_ACTION[what]);
                intent.putExtra("arg1", arg1);
                intent.putExtra("arg2", arg2);
                if (null != obj) {
                    intent.putExtra("obj", obj);
                }
                context.sendBroadcast(intent);
            }
        }
    }
}
