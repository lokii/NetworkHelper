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

/**
 * Created with IntelliJ IDEA.
 * User: lokii
 * Date: 15/2/1
 */
public class DownloadService extends Service {

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    public static final int MSG_STARTED = 0x0;
    public static final int MSG_PROGRESS_UPDATE = 0x1;
    public static final int MSG_CANCELED = 0x2;
    public static final int MSG_COMPLETED = 0x3;
    public static final int MSG_FAILED = 0x4;

    public static final String ACTION_STATED = "DownloadService.Started";
    public static final String ACTION_PROGRESS_UPDATE = "DownloadService.Update";
    public static final String ACTION_CANCELED = "DownloadService.Canceled";
    public static final String ACTION_COMPLETED = "DownloadService.Completed";
    public static final String ACTION_FAILED = "DownloadService.Failed";

    public static final String[] MSG_TO_ACTION = {
            ACTION_STATED,
            ACTION_PROGRESS_UPDATE,
            ACTION_CANCELED,
            ACTION_COMPLETED,
            ACTION_FAILED
    };
    private static final String TAG = "DownloadService";

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
    }

    private Hashtable<String, DownloadTask> mTasks = new Hashtable<String, DownloadTask>();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null != intent) {
            String url = intent.getStringExtra("URL");
            String outputPath = intent.getStringExtra("OutputPath");
            Messenger messenger = intent.getParcelableExtra("Messenger");
            boolean needStop = intent.getBooleanExtra("Stop", false);

            if (null != url && null != outputPath) {
                if (needStop) {
                    if (mTasks.contains(url)) {
                        stopDownload(mTasks.get(url));
                    }
                } else {
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
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void startDownload(DownloadTask task) {
        if (null != task) {
            task.mAsyncTask = new DownloadFileTask(this, task);
            task.mAsyncTask.execute();
        }
    }

    private void stopDownload(DownloadTask task) {
        task.mAsyncTask.cancel(true);
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
                    while ((len = inputStream.read(buffer, 0, buffer.length)) != -1) {
                        outputStream.write(buffer, 0, len);
                        downloadSize += len;

                        if (totalSize > 0) {
                            publishProgress((int) ((downloadSize / (float) totalSize) * 100));
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
            sendMessageToClient(MSG_CANCELED, 0, 0, mTask.mUrl);
            removeTask();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                sendMessageToClient(MSG_COMPLETED, 0, 0, mTask.mUrl);
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
