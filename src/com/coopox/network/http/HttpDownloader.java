package com.coopox.network.http;

import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.*;
import java.net.*;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: lokii
 * Date: 15/2/5
 */
public class HttpDownloader {
    private static final String TAG = "HttpDownloader";

    public interface DownloadListener {
        void onDownloadStart(String url, String outputPath, Object data);

        void onUpdateProgress(String url, String outputPath, int progress, Object data);

        void onDownloadSuccess(String url, String outputPath, Object data);

        void onDownloadCancelled(String url, String outputPath, Object data);

        void onDownloadFailed(String url, String outputPath, int errCode, Object data);
    }

    public interface WorkFlowController {
        boolean isCancelled(String url, String outputPath);
    }

    private static final String CONTENT_DISPOSITION = "Content-Disposition";

    private static final int CACHE_BLOCK_SIZE = 1024;
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    public static final int ERR_OK = 0;
    public static final int ERR_CANCELLED = -1;
    public static final int ERR_CONNECT_FAILED = -2;
    public static final int ERR_TIMEOUT = -3;
    public static final int ERR_SOCKET = -4;
    public static final int ERR_RES_NOT_FOUND = -5;
    public static final int ERR_IO_ERROR = -6;
    public static final int ERR_PROTOCOL = -7;
    public static final int ERR_HTTP_RESP = -8;
    public static final int ERR_INVALID_PARAM = -9;
    public static final int ERR_HOST_ERROR = -10;


    public static int downloadFile(String urlToDownload, String outputPath) {
        return downloadFile(urlToDownload, outputPath);
    }

    public static int downloadFile(String urlToDownload, String outputPath,
                            DownloadListener listener) {
        return downloadFile(urlToDownload, outputPath, listener, null);
    }

    public static int downloadFile(String urlToDownload, String outputPath,
                                   DownloadListener listener,
                                   WorkFlowController controller) {
        return downloadFile(urlToDownload, outputPath, null, listener, controller);
    }

    public static int downloadFile(String urlToDownload, String outputPath, Object data,
                            DownloadListener listener,
                            WorkFlowController controller) {
        if (TextUtils.isEmpty(urlToDownload) || TextUtils.isEmpty(outputPath)) {
            return ERR_INVALID_PARAM;
        }

        int retCode = ERR_OK;
        HttpURLConnection connection = null;
        InputStream input = null;
        OutputStream output = null;
        int fileLength = -1;

        try {
            URL url = new URL(urlToDownload);
            connection = (HttpURLConnection) url.openConnection();
            connection.setUseCaches(false);

            Properties properties = MetaFileUtil.loadMetaProperties(outputPath, urlToDownload);
            long breakpointOffset = 0;
            if (null != properties) {
                breakpointOffset = MetaFileUtil.getBreakpointStart(connection, outputPath, properties);
            }

            int respCode = connection.getResponseCode();

            outputPath = checkOutputFileName(urlToDownload, outputPath, connection);

            // this will be useful so that you can show a typical 0-100% progress bar
            fileLength = connection.getContentLength();

            boolean append = false;
            if (breakpointOffset > 0 && HttpURLConnection.HTTP_PARTIAL == respCode) {
                append = true;
                // Take the file original size.
                String contentLength = properties.getProperty(MetaFileUtil.CONTENT_LENGTH);
                if (TextUtils.isDigitsOnly(contentLength)) {
                    fileLength = Integer.parseInt(contentLength);
                } else {
                    fileLength = -1; // Unknown file size.
                }
            } else if (HttpURLConnection.HTTP_OK == respCode) {
                // Remote resource modified, can't do breakpoint resume download.
                if (breakpointOffset > 0) {
                    Log.d(TAG, "Remote resource was modified, restart download.");
                    breakpointOffset = 0;
                }
            } else {
                retCode = ERR_HTTP_RESP;
            }

            // Download to file
            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(outputPath, append);

            if (null != listener) {
                listener.onDownloadStart(urlToDownload, outputPath, data);
            }
            byte buffer[] = new byte[CACHE_BLOCK_SIZE];
            int lastPercent = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                breakpointOffset += count;
                output.write(buffer, 0, count);

                // publishing the progress....
                int process = 0;
                if (fileLength > 0) {
                    int percent = (int) ((breakpointOffset / (float) fileLength) * 100);

                    // Update progress only when percent changed.
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        process = percent;
                    }
                } else {
                    process = (int) breakpointOffset;
                }

                if (null != listener && process > 0) {
                    listener.onUpdateProgress(urlToDownload, outputPath, process, data);
                }

                if (null != controller && controller.isCancelled(urlToDownload, outputPath)
                        && (breakpointOffset < fileLength || fileLength < 0)) {
                    retCode = ERR_CANCELLED;
                    break;
                }
            }

            output.flush();

        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            retCode = ERR_TIMEOUT;
        } catch (ConnectException e) {
            e.printStackTrace();
            retCode = ERR_CONNECT_FAILED;
        } catch (SocketException e) {
            e.printStackTrace();
            retCode = ERR_SOCKET;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            retCode = ERR_RES_NOT_FOUND;
        } catch (ProtocolException e) {
            e.printStackTrace();
            retCode = ERR_PROTOCOL;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            retCode = ERR_HOST_ERROR;
        } catch (IOException e) {
            e.printStackTrace();
            retCode = ERR_IO_ERROR;
        } finally {

            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (null != listener) {
                switch (retCode) {
                    case ERR_OK:
                        listener.onDownloadSuccess(urlToDownload, outputPath, data);
                        break;
                    case ERR_CANCELLED:
                        listener.onDownloadCancelled(urlToDownload, outputPath, data);
                        break;
                    default:
                        listener.onDownloadFailed(urlToDownload, outputPath, retCode, data);
                        break;
                }
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
                    MetaFileUtil.updateMetaFile(outputPath, urlToDownload, fileLength, connection);
                } else {
                    MetaFileUtil.removeMetaFile(outputPath);
                }

                connection.disconnect();
            }
        }

        return retCode;
    }

    private static String checkOutputFileName(String urlToDownload, String outputPath, HttpURLConnection connection) {
        File outputFile = new File(outputPath);
        if (outputFile.isDirectory()) {
            String fileName = URLUtil.guessFileName(urlToDownload,
                    connection.getHeaderField(CONTENT_DISPOSITION),
                    connection.getContentType());
            Log.d(TAG, String.format("%s is a directory, I guess the file name is %s",
                    outputPath, fileName));
            outputFile = new File(outputPath, fileName);
            outputPath = outputFile.getPath();
        }
        return outputPath;
    }
}
