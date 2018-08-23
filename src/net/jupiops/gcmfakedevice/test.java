package net.jupiops.gcmfakedevice;

import java.util.Map;

public class test {
    public static void main(String[] args) {
        FakeDevice fakeDevice = new FakeDevice();
        System.out.println(fakeDevice.checkin());
        System.out.println(fakeDevice.getToken());
        for (Map.Entry<String, Object> entry : fakeDevice.getGcmResponse().entrySet()) {
            System.out.println(entry.getKey() + ":" + String.valueOf(entry.getValue()));
        }
        fakeDevice.closeAll();
    }
}
