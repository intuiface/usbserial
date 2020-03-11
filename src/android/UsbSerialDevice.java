package com.intuilab.intuifaceplayer.usbserial;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.intuilab.intuifaceplayer.usbserial.driver.UsbSerialDriver;
import com.intuilab.intuifaceplayer.usbserial.driver.UsbSerialPort;
import com.intuilab.intuifaceplayer.usbserial.util.SerialInputOutputManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsbSerialDevice {
    private final String TAG = UsbSerialDevice.class.getSimpleName();

    private UsbSerialDriver driver;
    private UsbSerialPort port;

    private int baudRate;
    private int dataBits;
    private int stopBits;
    private int parity;
    private boolean setDTR;
    private boolean setRTS;
    private boolean sleepOnPause;

    private CallbackContext readCallback;
    private final ByteBuffer mReadBuffer = ByteBuffer.allocate(4096);
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;

    public UsbSerialDevice(final UsbSerialDriver driver) {
        this.driver = driver;
    }

    public void openSerial(final UsbManager manager, final JSONObject opts) throws Exception {
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());

        if (connection != null) {
            port = driver.getPorts().get(0);

            if (opts != null) {
                baudRate = opts.has("baudRate") ? opts.getInt("baudRate") : 9600;
                dataBits = opts.has("dataBits") ? opts.getInt("dataBits") : UsbSerialPort.DATABITS_8;
                stopBits = opts.has("stopBits") ? opts.getInt("stopBits") : UsbSerialPort.STOPBITS_1;
                parity = opts.has("parity") ? opts.getInt("parity") : UsbSerialPort.PARITY_NONE;
                setDTR = opts.has("dtr") && opts.getBoolean("dtr");
                setRTS = opts.has("rts") && opts.getBoolean("rts");
                sleepOnPause = !opts.has("sleepOnPause") || opts.getBoolean("sleepOnPause");
            }

            port.open(connection);
            port.setParameters(baudRate, dataBits, stopBits, parity);

            if (setDTR) port.setDTR(true);
            if (setRTS) port.setRTS(true);
        } else {
            throw new Exception("Cannot connect to the device " + driver.getDevice().getDeviceName());
        }
        onDeviceStateChange();
    }

    public int writeSerial(final JSONObject opts) throws Exception {
        if (port == null) {
            throw new Exception("Writing a closed port.");
        }

        String data = opts.optString("data");
        Log.d(TAG, data);
        byte[] buffer = data.getBytes();
        return port.write(buffer, 1000);
    }

    public byte[] readSerial() throws Exception {
        if (port == null) {
            throw new Exception("Reading a closed port.");
        }

        int len = port.read(mReadBuffer.array(), 200);
        if (len > 0) {
            Log.d(TAG, "Read data len=" + len);
            final byte[] data = new byte[len];
            mReadBuffer.get(data, 0, len);
            mReadBuffer.clear();
            return data;
        }

        return new byte[0];
    }

    public void closeSerial() throws Exception {
        if (port != null) {
            port.close();
        }
        port = null;
        onDeviceStateChange();
    }

    public void registerReadCallback(final CallbackContext callback) {
        readCallback = callback;
    }

    public boolean isConnected(final UsbManager manager) {
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (device.getDeviceName().equals(this.driver.getDevice().getDeviceName())) {
                return port != null;
            }
        }
        return false;
    }

    public UsbDevice getDevice() {
        return driver.getDevice();
    }

    /**
     * Observe serial connection
     */
    private void startIoManager() {
        if (driver != null) {
            Log.d(TAG, "Starting io manager on " + driver.getDevice());

            final SerialInputOutputManager.Listener mListener =
                    new SerialInputOutputManager.Listener() {
                        @Override
                        public void onRunError(Exception e) {
                            Log.d(TAG, "Runner stopped.");
                        }

                        @Override
                        public void onNewData(final byte[] data) {
                            UsbSerialDevice.updateReceivedData(data, readCallback);
                        }
                    };

            mSerialIoManager = new SerialInputOutputManager(port, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    /**
     * Stop observing serial connection
     */
    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.d(TAG, "Stopping io manager.");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    /**
     * Restart the observation of the serial connection
     */
    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    /**
     * Dispatch read data to javascript
     *
     * @param data the array of bytes to dispatch
     */
    private static void updateReceivedData(final byte[] data, final CallbackContext readCallback) {
        if (readCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
            result.setKeepCallback(true);
            readCallback.sendPluginResult(result);
        }
    }

    public void onPause(final boolean multitasking) {
        if (sleepOnPause) {
            stopIoManager();
            if (port != null) {
                try {
                    port.close();
                } catch (IOException e) {
                    // Ignore
                }
                port = null;
            }
        }
    }

    public void onResume(final UsbManager manager, final boolean multitasking) {
        Log.d(TAG, "Resumed, driver=" + driver);
        if (sleepOnPause) {
            if (driver == null) {
                Log.d(TAG, "No serial device to resume.");
            } else {
                try {
                    this.openSerial(manager, null);
                    Log.d(TAG, "Serial device: " + driver.getClass().getSimpleName());
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }

            }
        }
    }

    public void onDestroy() {
        Log.d(TAG, "Destroy, port=" + port);
        if (port != null) {
            try {
                port.close();
            } catch (IOException e) {
                Log.d(TAG, e.getMessage());
            }
        }
        onDeviceStateChange();
    }
}
