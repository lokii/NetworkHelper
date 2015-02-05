package com.coopox.network.http;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.io.*;
import java.net.HttpURLConnection;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: lokii
 * Date: 15/2/5
 */
public class MetaFileUtil {
    private static final String TAG = "MetaFileUtil";

    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String ETAG = "Etag";
    public static final String LAST_MODIFIED = "Last-Modified";
    public static final String RANGE = "Range";
    public static final String IF_RANGE = "If-Range";
    public static final String META_URL = "Url";

    private static final String META_SUFFIX = ".meta";

    public static int getDownloadedPercent(String url, String outputPath) {
        Pair<Integer, Integer> downloadedAndTotalSize = getDownloadedAndTotalSize(url, outputPath);
        if (null != downloadedAndTotalSize) {
            int downloadedLen = downloadedAndTotalSize.first;
            int totalSize = downloadedAndTotalSize.second;
            return (int) ((downloadedLen / (float) totalSize) * 100);
        }
        return 0;
    }

    public static Pair<Integer, Integer> getDownloadedAndTotalSize(String url, String outputPath) {
        Properties p = loadMetaProperties(url, outputPath);
        if (null != p) {
            File outputFile = new File(outputPath);
            long downloadedLen = outputFile.length();
            String contentLength = p.getProperty(MetaFileUtil.CONTENT_LENGTH);
            if (TextUtils.isDigitsOnly(contentLength) && downloadedLen > 0) {
                int totalSize = Integer.parseInt(contentLength);
                return Pair.create((int)downloadedLen, totalSize);
            }
        }
        return null;
    }

    public static long getBreakpointStart(HttpURLConnection connection,
                                          String outputPath, Properties properties) {
        if (canBreakpointResume(outputPath, properties)) {
            String eTag = properties.getProperty(ETAG);
            String lastModified = properties.getProperty(LAST_MODIFIED);
            File outputFile = new File(outputPath);
            long downloadedLen = outputFile.length();
            if (downloadedLen > 0) {
                Log.d(TAG, "It's can continue download");
                String range = String.format("bytes=%d-", downloadedLen);
                String ifRange = null != eTag ? eTag : lastModified;
                connection.setRequestProperty(RANGE, range);
                connection.setRequestProperty(IF_RANGE, ifRange);
                Log.d(TAG, "Range: " + range);
                Log.d(TAG, "If-Range: " + ifRange);

                return downloadedLen;
            }
        }
        return 0;
    }

    public static boolean canBreakpointResume(String outputPath, Properties properties) {
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

    public static Properties loadMetaProperties(String url, String outputPath) {
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
        } else {
            Log.d(TAG, "Can't Find a meta file for " + url);
        }
        return null;
    }

    public static void updateMetaFile(String outputPath, String urlToDownload,
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

    public static void saveMetaFile(String outputPath, Properties p) {
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
                        Log.d(TAG, String.format("%s has been saved", metaPath));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    public static void removeMetaFile(String outputPath) {
        if (null != outputPath) {
            String metaPath = outputPath + META_SUFFIX;
            if (safeRemoveFile(metaPath)) {
                Log.d(TAG, String.format("%s has been removed", metaPath));
            }
        }
    }

    public static boolean safeRemoveFile(String path) {
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
