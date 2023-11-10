package com.intuilab.intuifaceplayer.usbserial;

import java.util.HashMap;
import java.util.Iterator;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;

import org.json.JSONObject;

import com.intuilab.intuifaceplayer.usbserial.driver.UsbSerialDriver;
import com.intuilab.intuifaceplayer.usbserial.driver.UsbSerialProber;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import android.os.Build;

/**
 * Cordova plugin to communicate with the android serial port
 * @author Xavier Seignard <xavier.seignard@gmail.com>
 * @author Dario Cavada <dario.cavada.lab@gmail.com>
 */
public class UsbSerial extends CordovaPlugin {
	private final String TAG = UsbSerial.class.getSimpleName();

	private static final String ACTION_LIST = "listSerial";
	private static final String ACTION_REQUEST_PERMISSION = "requestPermission";
	private static final String ACTION_OPEN = "openSerial";
	private static final String ACTION_READ = "readSerial";
	private static final String ACTION_WRITE = "writeSerial";
	private static final String ACTION_CLOSE = "closeSerial";
	private static final String ACTION_READ_CALLBACK = "registerReadCallback";
	private static final String ACTION_IS_CONNECTED = "isConnectedSerial";

	private HashMap<String, UsbSerialDevice> serialDeviceList = new HashMap<String, UsbSerialDevice>();

	/**
	 * Overridden execute method
	 * @param action the string representation of the action to execute
	 * @param args
	 * @param callbackContext the cordova {@link CallbackContext}
	 * @return true if the action exists, false otherwise
	 */
	@Override
	public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
		Log.d(TAG, "Action: " + action);

		JSONObject opts = args.optJSONObject(0);

		if (action.equals(ACTION_LIST))
			listSerial(callbackContext);
		else if (action.equals(ACTION_REQUEST_PERMISSION))
			requestPermission(opts, callbackContext);
		else if (action.equals(ACTION_OPEN))
			openSerial(opts, callbackContext);
		else if (action.equals(ACTION_WRITE))
			writeSerial(opts, callbackContext);
		else if (action.equals(ACTION_READ))
			readSerial(opts, callbackContext);
		else if (action.equals(ACTION_CLOSE))
			closeSerial(opts, callbackContext);
		else if (action.equals(ACTION_READ_CALLBACK))
			registerReadCallback(opts, callbackContext);
		else if (action.equals(ACTION_IS_CONNECTED))
			isConnectedSerial(opts, callbackContext);
		else
			return false;

		return true;
	}

	/**
	 * List all serial ports
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	@TargetApi(21)
	private void listSerial(final CallbackContext callbackContext) {
		Log.d(TAG, "Listing serial port");
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					UsbManager usbManager = (UsbManager) cordova.getActivity().getSystemService(Context.USB_SERVICE);
					HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
					Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
					JSONArray devices = new JSONArray();

					while (deviceIterator.hasNext()) {
						UsbDevice device = deviceIterator.next();
						JSONObject json = new JSONObject();
						json.put("devicePort", device.getDeviceName());
						json.put("deviceId", device.getDeviceId());
						json.put("productId", device.getProductId());
						json.put("productName", device.getProductName());
						json.put("vendorId", device.getVendorId());
						json.put("serialNumber", device.getSerialNumber());
						json.put("manufacturerName", device.getManufacturerName());
						json.put("interfaceCount", device.getInterfaceCount());
						devices.put(json);
					}

					Log.d(TAG, "List of serial port: " + devices.toString());
					callbackContext.success(devices.toString());
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
			}
		});
	}

	/**
	 * Request permission the the user for the app to use the USB/serial port
	 * @param opts a {@link JSONObject} containing the connection paramters
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	private void requestPermission(final JSONObject opts, final CallbackContext callbackContext) {
		Log.d(TAG, "Requesting permission");
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					UsbManager manager = getUsbManager();
					UsbSerialProber prober = UsbSerialProber.getProber(opts);
					Iterator<UsbSerialDriver> availableDriversIterator = prober.findAllDrivers(manager).iterator();
					final String port = opts.optString("port");
					UsbSerialDriver driver = null;
					UsbDevice device = null;

					while (availableDriversIterator.hasNext()) {
						driver = availableDriversIterator.next();
						if (driver.getDevice().getDeviceName().contains(port)) {
							device = driver.getDevice();
							break;
						}
					}

					if (device == null) {
						Log.d(TAG, "No device found!");
						callbackContext.error("No device found!");
						return;
					}

					// create the intent that will be used to get the permission
					final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
					PendingIntent pendingIntent = PendingIntent.getBroadcast(cordova.getActivity(), 0, new Intent(UsbBroadcastReceiver.USB_PERMISSION), flags);
					// and a filter on the permission we ask
					IntentFilter filter = new IntentFilter();
					filter.addAction(UsbBroadcastReceiver.USB_PERMISSION);
					// this broadcast receiver will handle the permission results
					UsbBroadcastReceiver usbReceiver = new UsbBroadcastReceiver(callbackContext, cordova.getActivity());
					cordova.getActivity().registerReceiver(usbReceiver, filter);
					// finally ask for the permission
					manager.requestPermission(device, pendingIntent);

					serialDeviceList.put(port, new UsbSerialDevice(driver));

				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
			}
		});
	}

	/**
	 * Open the serial port from Cordova
	 * @param opts a {@link JSONObject} containing the connection paramters
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	private void openSerial(final JSONObject opts, final CallbackContext callbackContext) {
		Log.d(TAG, "Opening serial");
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					final UsbSerialDevice device = findUsbSerialDevice(opts);
					device.openSerial(getUsbManager(), opts);
					Log.d(TAG, "Serial port opened on " + device.getDevice().getDeviceName());
					callbackContext.success("Serial port opened on " + device.getDevice().getDeviceName());
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
			}
		});
	}

	/**
	 * Write on the serial port
	 * @param opts a {@link JSONObject} representation of the data to be written on the port
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	private void writeSerial(final JSONObject opts, final CallbackContext callbackContext) {
		Log.d(TAG, "Writing on serial");
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					final UsbSerialDevice device = findUsbSerialDevice(opts);
					int bytesWritten = device.writeSerial(opts);
					Log.d(TAG,bytesWritten + " bytes written on " + device.getDevice().getDeviceName());
					callbackContext.success(bytesWritten + " bytes written on " + device.getDevice().getDeviceName());
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
			}
		});
	}

	/**
	 * Read on the serial port
	 * @param callbackContext the {@link CallbackContext}
	 */
	private void readSerial(final JSONObject opts, final CallbackContext callbackContext) {
		Log.d(TAG, "Reading serial");
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					final UsbSerialDevice device = findUsbSerialDevice(opts);
					byte[] data = device.readSerial();
					callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, data));
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
			}
		});
	}

	/**
	 * Close the serial port
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	private void closeSerial(final JSONObject opts, final CallbackContext callbackContext) {
		Log.d(TAG, "Closing serial");
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					final UsbSerialDevice device = findUsbSerialDevice(opts);
					device.closeSerial();
					Log.d(TAG, "Serial port closed on " + device.getDevice().getDeviceName());
					callbackContext.success("Serial port closed on " + device.getDevice().getDeviceName());
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
			}
		});
	}

	/**
	 * Register callback for read data
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	private void registerReadCallback(final JSONObject opts, final CallbackContext callbackContext) {
		Log.d(TAG, "Registering callback");
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					final UsbSerialDevice device = findUsbSerialDevice(opts);
					Log.d(TAG, "Callback registered on " + device.getDevice().getDeviceName());
					device.registerReadCallback(callbackContext);
					PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, "");
					pluginResult.setKeepCallback(true);
					callbackContext.sendPluginResult(pluginResult);
				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
			}
		});
	}

	/**
	 * Check if serial is connected
	 * @param opts a {@link JSONObject} representation of the data to be written on the port
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	private void isConnectedSerial(final JSONObject opts, final CallbackContext callbackContext) {
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					final UsbSerialDevice device = findUsbSerialDevice(opts);
					final boolean isConnected = device.isConnected(getUsbManager());
					if (isConnected)
						callbackContext.success("true");
					else
						callbackContext.success("false");

				} catch (Exception e) {
					Log.e(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
			}
		});
	}

	private UsbSerialDevice findUsbSerialDevice(final JSONObject opts) throws Exception {
		String port = opts.optString("port");
		UsbSerialDevice device = serialDeviceList.get(port);
		if (device == null) {
			throw new Exception("Device is not registered!");
		}
		return device;
	}

	private UsbManager getUsbManager() {
		return (UsbManager) cordova.getActivity().getSystemService(Context.USB_SERVICE);
	}

	/**
	 * Paused activity handler
	 * @see org.apache.cordova.CordovaPlugin#onPause(boolean)
	 */
	@Override
	public void onPause(boolean multitasking) {
		Log.d(TAG, "onPausing");
		for (HashMap.Entry<String, UsbSerialDevice> serialDeviceEntry : serialDeviceList.entrySet()) {
			serialDeviceEntry.getValue().onPause(multitasking);
		}
	}

	/**
	 * Resumed activity handler
	 * @see org.apache.cordova.CordovaPlugin#onResume(boolean)
	 */
	@Override
	public void onResume(boolean multitasking) {
		Log.d(TAG, "onResuming");
		for (HashMap.Entry<String, UsbSerialDevice> serialDeviceEntry : serialDeviceList.entrySet()) {
			serialDeviceEntry.getValue().onResume(getUsbManager(), multitasking);
		}
	}

	/**
	 * Destroy activity handler
	 * @see org.apache.cordova.CordovaPlugin#onDestroy()
	 */
	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroying");
		for (HashMap.Entry<String, UsbSerialDevice> serialDeviceEntry : serialDeviceList.entrySet()) {
			serialDeviceEntry.getValue().onDestroy();
		}
	}
}
