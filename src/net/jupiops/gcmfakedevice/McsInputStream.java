package net.jupiops.gcmfakedevice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Message;
import org.microg.gms.gcm.mcs.Mcs;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import static org.microg.gms.gcm.mcs.McsConstants.*;

public class McsInputStream extends Thread implements Closeable {
    private static final String TAG = "GmsGcmMcsInput";
    private static final boolean DEBUG = false;

    private final InputStream is;

    private boolean initialized;
    private int version = -1;
    //    private int streamId = 0;
    private int lastMsgTag = -1;

    private boolean closed = false;

    public McsInputStream(InputStream is) {
        this(is, false);
    }

    public McsInputStream(InputStream is, boolean initialized) {
        this.is = is;
        this.initialized = initialized;
    }

    private static Message read(int mcsTag, byte[] bytes, int len) throws IOException {
        try {
            switch (mcsTag) {
                case MCS_HEARTBEAT_PING_TAG:
                    return Mcs.HeartbeatPing.parseFrom(bytes);
                case MCS_HEARTBEAT_ACK_TAG:
                    return Mcs.HeartbeatAck.parseFrom(bytes);
                case MCS_LOGIN_REQUEST_TAG:
                    return Mcs.LoginRequest.parseFrom(bytes);
                case MCS_LOGIN_RESPONSE_TAG:
                    return Mcs.LoginResponse.parseFrom(bytes);
                case MCS_CLOSE_TAG:
                    return Mcs.Close.parseFrom(bytes);
                case MCS_IQ_STANZA_TAG:
                    return Mcs.IqStanza.parseFrom(bytes);
                case MCS_DATA_MESSAGE_STANZA_TAG:
                    return Mcs.DataMessageStanza.parseFrom(bytes);
                default:
                    if (DEBUG) System.out.println(TAG + "Unknown tag: " + mcsTag);
                    return null;
            }
        } catch (IllegalStateException e) {
            System.err.println(TAG + "Error parsing tag: " + mcsTag + "\n" + e);
            return null;
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && !closed) {
                Message msg = read();
                if (msg != null) {
                    if (handleInput(lastMsgTag, msg))
                        break;
                } else {
                    break; // if input is empty, do not continue looping
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean handleInput(int type, Message message) {
        try {
            switch (type) {
                case MCS_DATA_MESSAGE_STANZA_TAG:
                    Mcs.DataMessageStanza messageStanza = ((Mcs.DataMessageStanza) message);
                    for (Mcs.AppData entry : messageStanza.getAppDataList()) {
                        if (entry.getKey().equalsIgnoreCase("payload")) ;
                        JsonObject jObject = new JsonParser().parse(entry.getValue()).getAsJsonObject();
                        if (jObject.has("server_time") && jObject.has("verification_code"))
                            System.out.println(jObject.get("server_time") + "\n" + jObject.get("verification_code"));
                        return true;
                    }
                    return false;
                case MCS_HEARTBEAT_PING_TAG:
                    break;
                case MCS_HEARTBEAT_ACK_TAG:
                    break;
                case MCS_CLOSE_TAG:
                    break;
                case MCS_LOGIN_RESPONSE_TAG:
                    break;
                default:
                    if (DEBUG) System.out.println(TAG + "Unknown message: " + message);
                    break;
            }
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        }
        return false;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            interrupt();
        }
    }

    private synchronized void ensureVersionRead() {
        if (!initialized) {
            try {
                version = is.read();
                if (DEBUG) System.out.println(TAG + "Reading from MCS version: " + version);
                initialized = true;
            } catch (IOException e) {
                System.out.println(TAG + e);
            }
        }
    }

    public synchronized Message read() throws IOException {
        ensureVersionRead();
        int mcsTag = is.read();
        lastMsgTag = mcsTag;
        int mcsSize = readVarint();
        if (mcsTag < 0 || mcsSize < 0) {
            if (DEBUG) System.out.println(TAG + "mcsTag: " + mcsTag + " mcsSize: " + mcsSize);
            return null;
        }
        byte[] bytes = new byte[mcsSize];
        int len = 0, read = 0;
        while (len < mcsSize && read >= 0) {
            len += (read = is.read(bytes, len, mcsSize - len)) < 0 ? 0 : read;
        }
        Message message = read(mcsTag, bytes, len);
        if (message == null) return null;
        if (DEBUG) System.out.println(TAG + "Incoming message: " + message);
//        streamId++;
//        return mainHandler.obtainMessage(MSG_INPUT, mcsTag, streamId, message);
        return message;
    }

    private int readVarint() throws IOException {
        int res = 0, s = -7, read;
        do {
            res |= ((read = is.read()) & 0x7F) << (s += 7);
        } while (read >= 0 && (read & 0x80) == 0x80 && s < 32);
        if (read < 0) return -1;
        return res;
    }

}

