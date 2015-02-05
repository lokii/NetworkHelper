package com.coopox.network.http;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;


/**
 * Created with IntelliJ IDEA.
 * User: lokii
 * Date: 15/2/5
 */
public class DownloadClient {

    private final static String TAG = "DownloadClient";

    public interface DownloadListener extends HttpDownloader.DownloadListener {
        void onDownloadWaiting(String url, String outputPath, Object data);
    }

    private final Context mContext;
    private Handler mMainLoopHandler;

    private static class DownloadReceiver extends ResultReceiver {
        private DownloadListener mListener;

        public DownloadReceiver(Handler handler,
                                DownloadListener listener) {
            super(handler);
            mListener = listener;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            if (null == mListener || null == resultData) {
                Log.w(TAG, "Invalid param in onReceiveResult!");
                return ;
            }

            Log.d(TAG, "Download Message: " + resultCode);
            String url = getUrlToDownload(resultData);
            String outputPath = getOutputPath(resultData);

            switch (resultCode) {
                case IPCConstants.MSG_WAITING:
                    mListener.onDownloadWaiting(url, outputPath, null);
                    break;
                case IPCConstants.MSG_STARTED:
                    mListener.onDownloadStart(url, outputPath, null);
                    break;
                case IPCConstants.MSG_UPDATE_PROGRESS:
                    int progress = resultData.getInt(IPCConstants.KEY_PROGRESS);
                    mListener.onUpdateProgress(url, outputPath, progress, null);
                    break;
                case IPCConstants.MSG_SUCCESS:
                    mListener.onDownloadStart(url, outputPath, null);
                    break;
                case IPCConstants.MSG_CANCELLED:
                    mListener.onDownloadCancelled(url, outputPath, null);
                    break;
                case IPCConstants.MSG_FAILED:
                    int errCode = resultData.getInt(IPCConstants.KEY_ERROR_CODE);
                    mListener.onDownloadFailed(url, outputPath, errCode, null);
                    break;
            }
        }

        public static String getUrlToDownload(Bundle bundle) {
            String url = null;
            if (null != bundle) {
                url = bundle.getString(IPCConstants.KEY_URL);
            }
            return null != url ? url : "";
        }

        public static String getOutputPath(Bundle bundle) {
            String output = null;
            if (null != bundle) {
                output = bundle.getString(IPCConstants.KEY_OUTPUT);
            }
            return null != output ? output : "";
        }
    }

    public DownloadClient(Context context) {
        mContext = context;
        mMainLoopHandler = new Handler(Looper.getMainLooper());
    }

    public void startDownloadInParallel(String url, String outputPath,
                                         DownloadListener listener) {
        if (null != mContext && URLUtil.isHttpUrl(url)
                && !TextUtils.isEmpty(outputPath) && null != listener) {
            Intent intent = new Intent(mContext, DownloadService.class);
            intent.setData(Uri.parse(url));

            intent.putExtra(IPCConstants.EXTRA_OUTPUT_PATH, outputPath);
            intent.putExtra(IPCConstants.EXTRA_RECEIVER,
                    new DownloadReceiver(mMainLoopHandler, listener));
            mContext.startService(intent);
        }
    }

    public void stopDownloadInParallel(String url, String outputPath,
                                        DownloadListener listener) {
        if (null != mContext && URLUtil.isHttpUrl(url)
                && !TextUtils.isEmpty(outputPath) && null != listener) {
            Intent intent = new Intent(mContext, DownloadService.class);
            intent.setData(Uri.parse(url));

            intent.putExtra(IPCConstants.EXTRA_OUTPUT_PATH, outputPath);
            intent.putExtra(IPCConstants.EXTRA_RECEIVER,
                    new DownloadReceiver(mMainLoopHandler, listener));
            intent.putExtra(IPCConstants.EXTRA_CANCEL, true);
            mContext.startService(intent);
        }
    }

    public void startDownloadInSerial(String url, String outputPath,
                                        DownloadListener listener) {
        if (null != mContext && URLUtil.isHttpUrl(url)
                && !TextUtils.isEmpty(outputPath) && null != listener) {
            Intent intent = new Intent(mContext, DownloadIntentService.class);
            intent.setData(Uri.parse(url));

            intent.putExtra(IPCConstants.EXTRA_OUTPUT_PATH, outputPath);
            intent.putExtra(IPCConstants.EXTRA_RECEIVER,
                    new DownloadReceiver(mMainLoopHandler, listener));
            mContext.startService(intent);
        }
    }
}
