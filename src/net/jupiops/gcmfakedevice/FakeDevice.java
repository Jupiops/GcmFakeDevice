package net.jupiops.gcmfakedevice;

import org.microg.gms.checkin.CheckinProto;
import org.microg.gms.gcm.mcs.Mcs;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.microg.gms.gcm.mcs.McsConstants.MCS_LOGIN_REQUEST_TAG;
import static org.microg.gms.gcm.mcs.McsConstants.MSG_OUTPUT;

public class FakeDevice {
    public static final String SERVICE_HOST = "mtalk.google.com";
    public static final int SERVICE_PORT = 5228;
    private static final boolean DEBUG = false;
    private static final String CHECKIN_URL = "https://android.clients.google.com/checkin";
    private static final String C2DM_REGISTER_URL = "https://android.clients.google.com/c2dm/register3";

    private long androidId;
    private long securityToken;
    private Socket sslSocket;
    private McsOutputStream outputStream;
    private ExecutorService executor;

    public FakeDevice(long androidId, long securityToken) {
        this.androidId = androidId;
        this.securityToken = securityToken;
        executor = Executors.newSingleThreadExecutor();
    }

    public FakeDevice() {
        this(-1, -1);
    }

    private static Map<String, String> c2dmRegister(long androidId, long securityToken, boolean delete) throws Exception {
        return register(androidId, securityToken, "com.tellm.android.app", "a4a8d4d7b09736a0f65596a868cc6fd620920fb0", 1001800, "425112442765", null, delete);
    }

    private static Map<String, String> register(long androidId, long securityToken, String app, String appSignature, int appVersion, String sender, String info, boolean delete) throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        Map<String, String> params = new HashMap<String, String>();

        headers.put("User-Agent", "Android-GCM/1.3 (vbox86p JLS36G)");
        headers.put("Authorization", "AidLogin " + androidId + ":" + securityToken);
        headers.put("app", app);

        if (info != null) params.put("info", info);
        if (!delete && sender != null) params.put("sender", sender);
        params.put("app", app);
        params.put("cert", appSignature);
        params.put("app_ver", Integer.toString(appVersion));
        if (delete) params.put("delete", Boolean.toString(delete));
        params.put("device", Long.toString(androidId));
        byte[] responseBytes = sendPost(C2DM_REGISTER_URL, params, headers);
        return parseResponse(new String(responseBytes));
    }

    private static CheckinProto.CheckinResponse checkin(CheckinProto.CheckinRequest request) throws Exception {
        Map<String, String> headers = new HashMap<String, String>();

        headers.put("Content-type", "application/x-protobuffer");
        headers.put("Content-Encoding", "gzip");
        headers.put("Accept-Encoding", "gzip");
        headers.put("User-Agent", "Android-Checkin/2.0 (vbox86p JLS36G); gzip");

        byte[] content = sendPost(CHECKIN_URL, request.toByteArray(), headers);
        return CheckinProto.CheckinResponse.parseFrom(content);
    }

    private static CheckinProto.CheckinRequest buildCheckinRequest() {
        return CheckinProto.CheckinRequest.newBuilder().setAndroidId(0)
                .setCheckin(CheckinProto.CheckinRequest.Checkin.newBuilder()
                        .setBuild(CheckinProto.CheckinRequest.Checkin.Build.newBuilder().setSdkVersion(18)))
                .setVersion(3).setFragment(0).build();
    }

    private static Mcs.LoginRequest buildLoginRequest(long androidId, long securityToken) {
        return Mcs.LoginRequest.newBuilder()
                .setAuthService(Mcs.LoginRequest.AuthService.ANDROID_ID)
                .setAuthToken(Long.toString(securityToken))
                .setId("android-" + 18)
                .setDomain("mcs.android.com")
                .setDeviceId("android-" + Long.toHexString(androidId))
                .setNetworkType(1)
                .setResource(Long.toString(androidId))
                .setUser(Long.toString(androidId))
                .build();
    }

    private static SSLSocket createSSLSocket(String host, int port) throws Exception {
        if (DEBUG)
            System.out.println("Starting MCS connection...");
        Socket socket = new Socket(host, port);
        if (DEBUG)
            System.out.println("Connected to " + host + ":" + port);
        Socket sslSocket = SSLContext.getDefault().getSocketFactory().createSocket(socket, host, port, true);
        if (DEBUG)
            System.out.println("Activated SSL with " + host + ":" + port);

        if (DEBUG) {
            SSLSession session = ((SSLSocket) sslSocket).getSession();
            Certificate[] cchain = session.getPeerCertificates();
            System.out.println("The Certificates used by peer");
            for (int i = 0; i < cchain.length; i++) {
                System.out.println(((X509Certificate) cchain[i]).getSubjectDN());
            }
            System.out.println("Peer host is " + session.getPeerHost());
            System.out.println("Cipher is " + session.getCipherSuite());
            System.out.println("Protocol is " + session.getProtocol());
            System.out.println("ID is " + new BigInteger(session.getId()));
            System.out.println("Session created in " + session.getCreationTime());
            System.out.println("Session accessed in " + session.getLastAccessedTime());
        }
        return ((SSLSocket) sslSocket);
    }

    private static void tryClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static byte[] sendPost(String url, Map<String, String> params, Map<String, String> headers)
            throws Exception {
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        if (DEBUG)
            System.out.println("Post parameters : " + getDataString(new HashMap<String, String>(params)));
        return sendPost(url, getDataString(new HashMap<String, String>(params)).getBytes(StandardCharsets.UTF_8),
                headers);
    }

    private static byte[] sendPost(String url, byte[] request, Map<String, String> headers) throws Exception {

        URL obj = new URL(url);
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

        con.setRequestMethod("POST");
        con.setDoInput(true);
        con.setDoOutput(true);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            con.setRequestProperty(entry.getKey(), entry.getValue());
        }

        OutputStream os;
        if (headers.containsKey("Content-Encoding") && headers.get("Content-Encoding").equalsIgnoreCase("gzip"))
            os = new GZIPOutputStream(con.getOutputStream());
        else
            os = con.getOutputStream();

        os.write(request);
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();
        if (DEBUG) {
            System.out.println("\nSending 'POST' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);
        }
        if (responseCode != 200) {
            try {
                throw new IOException(new String(readStreamToEnd(new GZIPInputStream(con.getErrorStream()))));
            } catch (Exception e) {
                throw new IOException(con.getResponseMessage(), e);
            }
        }

        InputStream is;
        if (con.getContentEncoding() != null && con.getContentEncoding().equalsIgnoreCase("gzip"))
            is = new GZIPInputStream(con.getInputStream());
        else
            is = con.getInputStream();

        return readStreamToEnd(is);
    }

    private static String getDataString(HashMap<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first)
                first = false;
            else
                result.append("&");
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        return result.toString();
    }

    private static byte[] readStreamToEnd(final InputStream is) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (is != null) {
            final byte[] buff = new byte[1024];
            int read;
            do {
                bos.write(buff, 0, (read = is.read(buff)) < 0 ? 0 : read);
            } while (read >= 0);
            bos.flush();
            is.close();
        }
        return bos.toByteArray();
    }

    private static Map<String, String> parseResponse(String response) {
        Map<String, String> keyValueMap = new HashMap<String, String>();
        StringTokenizer st = new StringTokenizer(response, "\n\r");
        while (st.hasMoreTokens()) {
            String[] keyValue = st.nextToken().split("=", 2);
            if (keyValue.length >= 2) {
                keyValueMap.put(keyValue[0], keyValue[1]);
            }
        }
        return keyValueMap;
    }

    public boolean checkin() {
        try {
            CheckinProto.CheckinResponse response = checkin(buildCheckinRequest());
            if (response.hasAndroidId() && response.hasSecurityToken()) {
                androidId = response.getAndroidId();
                securityToken = response.getSecurityToken();
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public String getToken() {
        try {
            Map<String, String> register = c2dmRegister(androidId, securityToken, false);
            if (register.containsKey("token") && register.get("token") != null) return register.get("token");
        } catch (Exception e) {
        }
        return null;
    }

    public Map<String, Object> getGcmResponse() {
        Future<Map<String, Object>> future;
//        Map<String, String> returnMap = new HashMap<String, String>();
        try {
            sslSocket = createSSLSocket(FakeDevice.SERVICE_HOST, FakeDevice.SERVICE_PORT);
            outputStream = new McsOutputStream(sslSocket.getOutputStream());
            future = executor.submit(new McsInputStreamCallable(sslSocket.getInputStream()));
            outputStream.start();
            outputStream.handleMessage(MSG_OUTPUT, MCS_LOGIN_REQUEST_TAG, buildLoginRequest(androidId, securityToken));
            return future.get();
        } catch (Exception e) {
        }
        return null;
    }

    public long getAndroidId() {
        return androidId;
    }

    public long getSecurityToken() {
        return securityToken;
    }

    public void closeAll() {
        tryClose(outputStream);
//        tryClose(inputStream);
        executor.shutdown();
        if (sslSocket != null) {
            try {
                sslSocket.close();
            } catch (Exception ignored) {
            }
        }
    }
}
