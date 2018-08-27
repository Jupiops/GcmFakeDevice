package net.jupiops.gcmfakedevice;

import java.util.Map;

public class test {
    public static void main(String[] args) {
        FakeDevice fakeDevice = new FakeDevice();
        System.out.println(fakeDevice.checkin());
        System.out.println(fakeDevice.getToken());
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Object> entry : fakeDevice.getGcmResponse().entrySet()) {
            System.out.println(entry.getKey() + ":" + String.valueOf(entry.getValue()));
        }
        fakeDevice.closeAll();
    }
}
