package com.coopox.network.http;

/**
 * Created with IntelliJ IDEA.
 * User: lokii
 * Date: 15/2/5
 */
public class IPCConstants {
    public static final String EXTRA_OUTPUT_PATH = "OutputPath";
    public static final String EXTRA_RECEIVER = "Receiver";
    public static final String EXTRA_CANCEL = "Cancel";

    public static final String KEY_URL = "kUrl";
    public static final String KEY_OUTPUT = "kOutput";
    public static final String KEY_PROGRESS = "kProgress";
    public static final String KEY_ERROR_CODE = "kErrorCode";

    public static final int MSG_WAITING = 0x0;
    public static final int MSG_STARTED = 0x1;
    public static final int MSG_UPDATE_PROGRESS = 0x2;
    public static final int MSG_SUCCESS = 0x3;
    public static final int MSG_FAILED = 0x4;
    public static final int MSG_CANCELLED = 0x5;
    public static final int MSG_QUEUE_UP = 0x6;
}
