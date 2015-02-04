package com.coopox.network.http;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.Hashtable;
import java.util.Properties;
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

    public static final int MSG_PROGRESS_UPDATE = 0x1;

/*    public static final String ACTION_STATED = "DownloadService.Started";
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
    };*/

    public static final String EXTRA_OUTPUT_PATH = "OutputPath";
    public static final String EXTRA_RECEIVER = "Receiver";
    public static final String EXTRA_CANCEL = "Cancel";

    public static final String KEY_PROGRESS = "Progress";
    public static final String KEY_ERROR_CODE = "ErrorCode";

    public static final int MSG_STARTED = 0x0;
    public static final int MSG_UPDATE_PROGRESS = 0x1;
    public static final int MSG_SUCCESS = 0x2;
    public static final int MSG_CANCELLED = 0x3;
    public static final int MSG_FAILED = 0x4;
    public static final int MSG_QUEUE_UP = 0x5;

    public static final int ERR_OK = 0;
    public static final int ERR_CANCELLED = 1;
    public static final int ERR_HOST_ERROR = -1;
    public static final int ERR_CONNECT_FAILED = -2;
    public static final int ERR_TIMEOUT = -3;
    public static final int ERR_SOCKET = -4;
    public static final int ERR_RES_NOT_FOUND = -5;
    public static final int ERR_IO_ERROR = -6;
    public static final int ERR_PROTOCOL = -7;
    public static final int ERR_HTTP_RESP = -8;

    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String ETAG = "Etag";
    private static final String LAST_MODIFIED = "Last-Modified";
    private static final String RANGE = "Range";
    private static final String IF_RANGE = "If-Range";
    private static final String META_URL = "Url";

    private static final String META_SUFFIX = ".meta";
    private static final int MAX_THREADS = 3;

    class DownloadTask {
        public DownloadTask(String url, String output, ResultReceiver receiver) {
            mUrl = url;
            mOutput = output;
            mReceiver = receiver;
        }

        public String keyGen() {
            return mUrl + mOutput;
        }

        public final String mUrl;
        public final String mOutput;
        public final ResultReceiver mReceiver;
        public HttpURLConnection mConnection;
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
            String url = intent.getDataString();
            String outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH);
            ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);

            boolean cancel = intent.getBooleanExtra(EXTRA_CANCEL, false);

            if (!TextUtils.isEmpty(url) && !TextUtils.isEmpty(outputPath)) {
                String key = url + outputPath;
                if (cancel) {
                    if (mTasks.containsKey(key)) {
                        stopDownload(mTasks.get(key));
                    }
                } else if (null != receiver) {
                    DownloadTask task = mTasks.get(key);
                    if (null == task) {
                        task = new DownloadTask(url, outputPath, receiver);
                        startDownload(task);
                    } else {
                        receiver.send(MSG_QUEUE_UP, null);
                        Log.d(TAG, String.format("Task %s was already started", url));
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

    private void startDownload(DownloadTask task) {
        if (null != task) {
            mTasks.put(task.keyGen(), task);

            task.mRunnable = new DownloadFileRunnable(this, task);
            mThreadPool.execute(task.mRunnable);
        }
    }

    private void stopDownload(DownloadTask task) {
        if (null != task && null != task.mRunnable) {
                // Stop when it's Downloading, it will remove self at end of download
                task.mRunnable.stop();
        }
    }

    static class DownloadFileRunnable implements Runnable {

        private static final int CACHE_BLOCK_SIZE = 1024;
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
                doDownload(mTask);
            } else {
                mTask.mReceiver.send(MSG_CANCELLED, null);
                clearTask();
                checkWhetherStopService();
            }
        }
/*        public void run() {
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

                if (task.mConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    int totalSize = task.mConnection.getContentLength();

                    File outputFile = new File(task.mOutput);
                    outputStream = new FileOutputStream(outputFile);

                    inputStream = new BufferedInputStream(task.mConnection.getInputStream());

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
                } else {
                    exception = new RuntimeException("Http service response code = " + task.mConnection.getResponseCode());
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
        }*/

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
                if (service.mTasks.isEmpty()) {
                    service.stopSelf();
                }
            }
        }

/*        private void sendMessageToClient(int what, int arg1, int arg2, Serializable obj, Bundle data) {
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
        }*/

        private void doDownload(DownloadTask task) {
            String outputPath = task.mOutput;
            String urlToDownload = task.mUrl;
            ResultReceiver receiver = task.mReceiver;

            int retCode = ERR_OK;
            HttpURLConnection connection = null;
            InputStream input = null;
            OutputStream output = null;
            int fileLength = -1;

            try {
                URL url = new URL(urlToDownload);
                connection = (HttpURLConnection) url.openConnection();
                connection.setUseCaches(false);

                Properties properties = loadMetaProperties(outputPath, urlToDownload);
                long breakpointStart = 0;
                if (null != properties) {
                    breakpointStart = getBreakpointStart(connection, outputPath, properties);
                }

                int respCode = connection.getResponseCode();
                // this will be useful so that you can show a typical 0-100% progress bar
                fileLength = connection.getContentLength();

                boolean append = false;
                if (breakpointStart > 0 && HttpURLConnection.HTTP_PARTIAL == respCode) {
                    append = true;
                    // Take the file original size.
                    String contentLength = properties.getProperty(CONTENT_LENGTH);
                    if (TextUtils.isDigitsOnly(contentLength)) {
                        fileLength = Integer.parseInt(contentLength);
                    } else {
                        fileLength = -1; // Unknown file size.
                    }
                } else if (HttpURLConnection.HTTP_OK == respCode) {
                    // Remote resource modified, can't do breakpoint resume download.
                    if (breakpointStart > 0) {
                        Log.d(TAG, "Remote resource was modified, restart download.");
                        breakpointStart = 0;
                    }
                } else {
                    retCode = ERR_HTTP_RESP;
                }

                // Download to file
                input = new BufferedInputStream(connection.getInputStream());
                output = new FileOutputStream(outputPath, append);

                receiver.send(MSG_STARTED, null);
                byte data[] = new byte[CACHE_BLOCK_SIZE];
                int lastPercent = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    breakpointStart += count;
                    output.write(data, 0, count);

                    // publishing the progress....
                    if (fileLength > 0) {
                        int percent = (int) ((breakpointStart / (float) fileLength) * 100);

                        // Update progress only when percent changed.
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            Bundle resultData = new Bundle();
                            resultData.putInt(KEY_PROGRESS, percent);
                            receiver.send(MSG_UPDATE_PROGRESS, resultData);
                        }
                    } else {
                        Bundle resultData = new Bundle();
                        resultData.putLong(KEY_PROGRESS, breakpointStart);
                        receiver.send(MSG_UPDATE_PROGRESS, resultData);
                    }

                    if (mStoped && (breakpointStart < fileLength || fileLength < 0)) {
                        retCode = ERR_CANCELLED;
                        break;
                    }
                }

                output.flush();

            }  catch (SocketTimeoutException e) {
                e.printStackTrace();
                Log.e(TAG, "Connect/read Timeout!");
                retCode = ERR_TIMEOUT;
            } catch (ConnectException e) {
                e.printStackTrace();
                Log.e(TAG, "Connect failed!");
                retCode = ERR_CONNECT_FAILED;
            } catch (SocketException e) {
                e.printStackTrace();
                Log.e(TAG, "Socket exception!");
                retCode = ERR_SOCKET;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "No such resource!");
                retCode = ERR_RES_NOT_FOUND;
            } catch (ProtocolException e) {
                e.printStackTrace();
                Log.e(TAG, "Protocol Error!");
                retCode = ERR_PROTOCOL;
            } catch (UnknownHostException e) {
                e.printStackTrace();
                Log.e(TAG, "Host Error!");
                retCode = ERR_HOST_ERROR;
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO Error!");
                retCode = ERR_IO_ERROR;
            } finally {

                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                switch (retCode) {
                    case ERR_OK:
                        receiver.send(MSG_SUCCESS, null);
                        break;
                    case ERR_CANCELLED:
                        receiver.send(MSG_CANCELLED, null);
                        break;
                    default:
                        Bundle data = new Bundle();
                        data.putInt(KEY_ERROR_CODE, retCode);
                        receiver.send(MSG_FAILED, data);
                        break;
                }

                if (null != input) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (null != connection) {
                    if (retCode != ERR_OK) {
                        // Not download complete, try to generate meta file:
                        updateMetaFile(outputPath, urlToDownload, fileLength, connection);
                    } else {
                        removeMetaFile(outputPath);
                    }

                    connection.disconnect();
                }

                clearTask();
                checkWhetherStopService();
            }
        }

        private static long getBreakpointStart(HttpURLConnection connection,
                                        String outputPath, Properties properties) {
            if (canBreakpointResume(outputPath, properties)) {
                Log.d(TAG, "It's can continue download");
                String eTag = properties.getProperty(ETAG);
                String lastModified = properties.getProperty(LAST_MODIFIED);
                File outputFile = new File(outputPath);
                long downloadedLen = outputFile.length();
                if (downloadedLen > 0) {
                    String range = String.format("bytes=%d-", downloadedLen);
                    String ifRange = null != eTag ? eTag : lastModified;
                    connection.setRequestProperty(RANGE, range);
                    connection.setRequestProperty(IF_RANGE, ifRange);
                    Log.d(TAG, "Range: " + range);
                    Log.d(TAG, "If-Range: " + ifRange);

                    return downloadedLen;
                }
            }
            Log.d(TAG, "Can continue download");
            return 0;
        }

        private static boolean canBreakpointResume(String outputPath, Properties properties) {
            if (!TextUtils.isEmpty(properties.getProperty(ETAG)) ||
                    !TextUtils.isEmpty(properties.getProperty(LAST_MODIFIED))) {
                File outputFile = new File(outputPath);
                String contentLength = properties.getProperty(CONTENT_LENGTH);
                if (TextUtils.isDigitsOnly(contentLength)) {
                    int total = Integer.parseInt(contentLength);
                    return outputFile.length() < total;
                }
            }
            return false;
        }

        private static Properties loadMetaProperties(String outputPath, String url) {
            File outputFile = new File(outputPath);
            File metaFile = new File(outputPath + META_SUFFIX);
            if (outputFile.exists() && outputFile.isFile() &&
                    metaFile.exists() && metaFile.isFile()) {

                Properties p = new Properties();
                InputStream is = null;
                try {
                    is = new FileInputStream(metaFile);
                    p.load(is);
                    if (url.equalsIgnoreCase(p.getProperty(META_URL))) {
                        Log.d(TAG, "Find a meta file for " + url);
                        return p;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (null != is) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            return null;
        }

        private static void updateMetaFile(String outputPath, String urlToDownload,
                                    int contentLength, HttpURLConnection connection) {
            int fileLength = contentLength > 0 ? contentLength : connection.getContentLength();
            String lastModified = connection.getHeaderField(LAST_MODIFIED);
            String eTag = connection.getHeaderField(ETAG);
            if (fileLength > 0 &&
                    (!TextUtils.isEmpty(lastModified) || !TextUtils.isEmpty(eTag))) {
                Properties p = new Properties();

                p.setProperty(META_URL, urlToDownload);
                p.setProperty(CONTENT_LENGTH, String.valueOf(fileLength));
                if (!TextUtils.isEmpty(lastModified)) {
                    p.setProperty(LAST_MODIFIED, lastModified);
                }
                if (!TextUtils.isEmpty(eTag)) {
                    p.setProperty(ETAG, eTag);
                }
                saveMetaFile(outputPath, p);
            }
        }

        private static void saveMetaFile(String outputPath, Properties p) {
            if (null != outputPath && null != p) {
                String metaPath = outputPath += META_SUFFIX;
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(metaPath);
                    p.store(fos, "Meta file for downloading from " + outputPath);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (null != fos) {
                        try {
                            fos.close();
                            Log.d(TAG, "Meta file has been saved");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        }

        private static void removeMetaFile(String outputPath) {
            if (null != outputPath) {
                String metaPath = outputPath + META_SUFFIX;
                if (safeRemoveFile(metaPath)) {
                    Log.d(TAG, "Meta file has been removed");
                }
            }
        }

        private static boolean safeRemoveFile(String path) {
            File origFile = new File(path);
            if (origFile.exists()) {
                File tmpFile = new File(path + SystemClock.uptimeMillis());
                if (origFile.renameTo(tmpFile)) {
                    return tmpFile.delete();
                } else {
                    return origFile.delete();
                }
            }
            return false;
        }
    }
}
