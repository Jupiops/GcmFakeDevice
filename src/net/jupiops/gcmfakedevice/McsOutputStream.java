package net.jupiops.gcmfakedevice;

import com.google.protobuf.Message;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import static org.microg.gms.gcm.mcs.McsConstants.*;

public class McsOutputStream extends Thread implements Closeable {
    private static final boolean DEBUG = false;
    private final OutputStream os;
    private boolean initialized;
    private int version = MCS_VERSION_CODE;
    private int streamId = 0;

    private boolean closed = false;

    public McsOutputStream(OutputStream os) {
        this(os, false);
    }

    public McsOutputStream(OutputStream os, boolean initialized) {
        this.os = os;
        this.initialized = initialized;
        setName("McsOutputStream");
    }

    @Override
    public void run() {
        super.run();
    }

    public boolean handleMessage(int what, int arg, Message msg) {
        switch (what) {
            case MSG_OUTPUT:
                try {
                    if (DEBUG) System.out.println("Outgoing message:\n" + msg);
                    writeInternal(msg, arg);
                } catch (IOException e) {
                }
                return true;
            case MSG_TEARDOWN:
                try {
                    os.close();
                } catch (IOException ignored) {
                }
                return true;
        }
        return false;
    }

    public int getStreamId() {
        return streamId;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            interrupt();
        }
    }

    private synchronized void writeInternal(Message message, int tag) throws IOException {
        if (!initialized) {
            if (DEBUG) System.out.println("Write MCS version code: " + version);
            os.write(version);
            initialized = true;
        }
        os.write(tag);
        writeVarint(os, message.getSerializedSize());
        os.write(message.toByteArray());
        os.flush();
        streamId++;
    }

    private void writeVarint(OutputStream os, int value) throws IOException {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                os.write(value);
                return;
            } else {
                os.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

}
