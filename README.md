# NetworkHelper
Network Helper for Android.

## Http Downloader
A download helper that can support Breakpoint resume and Multi-tasking download.

### Download directly

```java
    /**
     * Save urlToDownload to file as outputPath directly.
     * Make sure to call this method in a worker thread for avoid block the UI thread.
     * @param urlToDownload: the HTTP URL to be downloaded.
     * @param outputPath: Path to save the downloaded file. If the outputPath
     *                  just is a directory(not contains file name) then
     *                  the downloader will guess a name by URL.
     * @return ERR_OK if download successfully, else return a error code.
     **/
    public static int downloadFile(String urlToDownload, String outputPath);
    
    /**
     * Save urlToDownload to file as outputPath directly.
     * Make sure to call this method in a worker thread for avoid block the UI thread.
     * @param urlToDownload the HTTP URL to be downloaded.
     * @param outputPath Path to save the downloaded file. If the outputPath
     *                  just is a directory(not contains file name) then
     *                  the downloader will guess a name by URL.
     * @param listener A listener for receive notify sent by downloader.
     * @return ERR_OK if download successfully, else return a error code.
     **/
    public static int downloadFile(String urlToDownload, String outputPath,
                            DownloadListener listener);
                            
```

### Parallel download by service

```java
    /**
     * Start download url to local file as outputPath via a Service.
     * Download task will be execute in a worker thread.
     * @param url the HTTP URL to be downloaded.
     * @param outputPath Path to save the downloaded file. If the outputPath
     *                  just is a directory(not contains file name) then
     *                  the downloader will guess a name by URL.
     * @param listener A listener for receive notify sent by downloader.
     * @return ERR_OK if download successfully, else return a error code.
     **/
    public void startDownloadInParallel(String url, String outputPath,
                                         DownloadListener listener);
                                         
    /**
     * Stop a download task in DownloadService.
     * @param url the HTTP URL to be downloaded.
     * @param outputPath Path to save the downloaded file. If the outputPath
     *                  just is a directory(not contains file name) then
     *                  the downloader will guess a name by URL.
     * @param listener A listener for receive notify sent by downloader.
     * @return ERR_OK if download successfully, else return a error code.
     **/
    public void stopDownloadInParallel(String url, String outputPath,
                                        DownloadListener listener);
                                      
```

### Add Service to AndroidManifest.xml

```
        <service android:name="com.coopox.network.http.DownloadService" />
        <service android:name="com.coopox.network.http.DownloadIntentService" />

```
