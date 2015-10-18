package io.foolsday.quadbridge;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

public class BLE extends BluetoothGattCallback implements BluetoothAdapter.LeScanCallback,
        QuadModel.QuadModelEventListener {

    public static final int REQUEST_ENABLE_BT = 0;
    public static final int INVALID_RSSI = -1000;

    public interface RSSIEventListener {
        void onRSSIUpdate(int rssi);
    }

    private static final String NUS_SERVICE_UUID_STR = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String NUS_TX_CHAR_UUID_STR = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String NUS_RX_CHAR_UUID_STR = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final UUID NUS_SERVICE_UUID = UUID.fromString(NUS_SERVICE_UUID_STR);
    private static final UUID[] NUS_SERVICE_UUID_ARRAY = {NUS_SERVICE_UUID};
    private static final UUID NOTIFICATION_DESCRIPTOR_UUID_STR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int UNBOUND_MODE = 0;
    private static final int BINDING_MODE = 1;
    private static final int BOUND_MODE = 2;
    private static final int UNBINDING_MODE = 3;

    // These are matched to the ble_cmd_t enum in the ble.c file.
    private static final byte[] BLE_CMD_BIND_ARRAY = {0x00};
    private static final int BLE_CMD_CTL = 1;
    private static final byte[] BLE_CMD_UNBIND_ARRAY = {0x02};

    // These are matched to the ble_cmd_response_t enum in the ble.c file.
    private static final int BLE_RESPONSE_BOUND = 0;
    private static final int BLE_RESPONSE_ERROR = 1;
    private static final int BLE_RESPONSE_UNBOUND = 2;

    private final static long RSSI_INTERVAL_MS = 1000;

    private UIActivity mUIActivity;

    private BluetoothAdapter mBTAdapter;
    private BluetoothGatt mBTGatt;
    private BluetoothGattService mQuadService;
    private BluetoothGattCharacteristic mQuadTXChar;
    private BluetoothGattCharacteristic mQuadRXChar;

    private Handler mHandler;
    private Runnable mRSSIRunnable;
    private ArrayList<RSSIEventListener> mListeners;

    private boolean mScanning;
    private boolean mConnected;
    private int mRSSI;
    private int mMode;
    private boolean mBLEWritePending;

    private byte[] mCtlBLECmd = {BLE_CMD_CTL, 0x00, 0x00, 0x00, 0x00};

    public BLE(UIActivity activity) {
        mUIActivity = activity;

        mHandler = new Handler();
        mListeners = new ArrayList<>();

        mScanning = false;
        mConnected = false;
        mRSSI = INVALID_RSSI;
        mMode = UNBOUND_MODE;
        mBLEWritePending = false;

        BluetoothManager manager;
        manager = (BluetoothManager) mUIActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBTAdapter = manager.getAdapter();

        if (!isBLEEnabled()) {
            enableBLE();
        }
    }

    public void addListener(RSSIEventListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(RSSIEventListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    public void startScan() {
        mScanning = true;
        mBTAdapter.startLeScan(NUS_SERVICE_UUID_ARRAY, this);
    }

    public void stopScan() {
        if (mScanning) {
            mBTAdapter.stopLeScan(this);
            mScanning = false;
        }
    }

    @Override
    public void onLeScan(final BluetoothDevice device,
                         final int rssi,
                         final byte[] scanRecord) {
        if (null != device) {
            mUIActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mUIActivity.addScanResult(device, rssi);
                }
            });
        }
    }

    /**
     * The parent Activity forwards results to this function in its onActivityResult
     * function.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case BLE.REQUEST_ENABLE_BT:
                if (Activity.RESULT_OK != resultCode) {
                    mUIActivity.toastAndFinish("Could not enable BLE.");
                }
                break;
        }
    }

    @Override
    public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
        if (BluetoothGatt.GATT_SUCCESS == status) {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    mConnected = true;

                    if (!gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)) {
                        toastAndDisconnect("Failed to set connection priority.");
                        return;
                    }

                    gatt.discoverServices();

                    mRSSIRunnable = new Runnable(){
                        public void run() {
                            gatt.readRemoteRssi();
                        }
                    };
                    mHandler.postDelayed(mRSSIRunnable, RSSI_INTERVAL_MS);

                    return;
                case BluetoothProfile.STATE_DISCONNECTED:
                    toastAndDisconnect("Disconnected.");
                    return;
                default:
                    // Not sure if this actually ever happens.
                    break;
            }
        } else {
            // Error 133 happens on the Nexus 5 when a threading conflict occurs.
            // Error 8 happens for an unknown reason but leads to a disconnect.
            toastAndDisconnect(String.format("A connection error occurred: %d", status));
        }
    }

    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
        if (BluetoothGatt.GATT_SUCCESS == status) {
            mQuadService = gatt.getService(UUID.fromString(NUS_SERVICE_UUID_STR));
            if (null == mQuadService) {
                toastAndDisconnect("Could not get the service from the GATT server.");
                return;
            }

            mQuadTXChar = mQuadService.getCharacteristic(UUID.fromString(NUS_TX_CHAR_UUID_STR));
            if (null == mQuadTXChar) {
                toastAndDisconnect("Could not get the TX char from the GATT server.");
                return;
            }

            if (BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE != mQuadTXChar.getWriteType()) {
                toastAndDisconnect("WRITE_TYPE_NO_RESPONSE is not available for TX char.");
                return;
            }

            mQuadRXChar = mQuadService.getCharacteristic(UUID.fromString(NUS_RX_CHAR_UUID_STR));
            if (null == mQuadRXChar) {
                toastAndDisconnect("Could not get the RX char from the GATT server.");
                return;
            }

            if (!gatt.setCharacteristicNotification(mQuadRXChar, true)) {
                toastAndDisconnect("Could not enable notifications for RX char.");
                return;
            }

            BluetoothGattDescriptor descriptor;
            descriptor = mQuadRXChar.getDescriptor(NOTIFICATION_DESCRIPTOR_UUID_STR);
            if (null == descriptor) {
                toastAndDisconnect("Failed to find notification descriptor for RX char.");
                return;
            }

            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(descriptor)) {
                toastAndDisconnect("Failed to write notification descriptor for RX char.");
                return;
            }

            if (!gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)) {
                toastAndDisconnect("Failed to request CONNECTION_PRIORITY_HIGH.");
                return;
            }

            mBLEWritePending = false;
        } else {
            String errString;
            errString = String.format("The service discovery failed with status: %d", status);
            toastAndDisconnect(errString);
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt,
                                  BluetoothGattDescriptor descriptor,
                                  int status) {
        if (BluetoothGatt.GATT_SUCCESS == status) {
            mUIActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Notifications should now be enabled for the RX char.
                    mUIActivity.bleConnected();
                }
            });
        } else {
            toastAndDisconnect("Failed to enable notifications on RX characteristic.");
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        byte[] chValue = ch.getValue();

        if (1 != chValue.length) {
            toastAndDisconnect(String.format("Unexpected response of length: %d",
                    chValue.length));
            return;
        }

        switch (chValue[0]) {
            case BLE_RESPONSE_BOUND:
                if (BINDING_MODE == mMode) {
                    mMode = BOUND_MODE;
                    mUIActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mUIActivity.onBound();
                        }
                    });
                }
                break;
            case BLE_RESPONSE_ERROR:
                toastAndDisconnect("Error notification received.");
                break;
            case BLE_RESPONSE_UNBOUND:
                disconnect();
                break;
            default:
                toastAndDisconnect(String.format("Unexpected response of length: %d",
                        chValue.length));
                break;
        }
    }

    private boolean sendDataToQuad(byte[] data) {
        if (!mBLEWritePending) {
            mBLEWritePending = true;
        } else {
            return false;
        }

        // NOTE: The BluetoothGattCharacteristic module simply stores a reference to the given
        //       data array. A local copy is made to prevent any potential threading issues.
        byte[] buf = new byte[data.length];
        System.arraycopy(data, 0, buf, 0, buf.length);

        mQuadTXChar.setValue(buf);
        return mBTGatt.writeCharacteristic(mQuadTXChar);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic,
                                      int status) {
        if (BluetoothGatt.GATT_SUCCESS == status) {
            mBLEWritePending = false;
            if (UNBINDING_MODE == mMode) {
                mMode = UNBOUND_MODE;
                if (!sendDataToQuad(BLE_CMD_UNBIND_ARRAY)) {
                    toastAndDisconnect("Failed to write unbind command.");
                }
            }
        } else {
            toastAndDisconnect("A char write failed!");
        }
    }

    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        if (BluetoothGatt.GATT_SUCCESS == status) {
            mRSSI = rssi;
            notifyListeners();
        }
        mHandler.postDelayed(mRSSIRunnable, RSSI_INTERVAL_MS);
    }

    public boolean hasRadio() {
        PackageManager mgr = mUIActivity.getPackageManager();
        return mgr.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public void bind() {
        mMode = BINDING_MODE;
        if (!sendDataToQuad(BLE_CMD_BIND_ARRAY)) {
            toastAndDisconnect("Failed to write bind command.");
        }
    }

    public void unbind() {
        if (UNBOUND_MODE == mMode) {
            disconnect();
        } else {
            if (mBLEWritePending) {
                // Change the mode to prevent future control packets from being sent and then wait
                // for the current write to finish.
                mMode = UNBINDING_MODE;
            } else {
                if (!sendDataToQuad(BLE_CMD_UNBIND_ARRAY)) {
                    toastAndDisconnect("Failed to write unbind command.");
                }
                mMode = UNBOUND_MODE;
            }
        }
    }

    public void connect(final BluetoothDevice device) {
        mMode = UNBOUND_MODE;
        mUIActivity.bleConnecting();
        stopScan();
        mBTGatt = device.connectGatt(mUIActivity, false, BLE.this);
    }

    private void toastAndDisconnect(final String errString) {
        mUIActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mUIActivity, errString, Toast.LENGTH_LONG).show();
                disconnect();
            }
        });
    }

    public void disconnect() {
        if (mConnected) {
            mConnected = false;

            mHandler.removeCallbacks(mRSSIRunnable);
            mRSSIRunnable = null;
            mRSSI = INVALID_RSSI;

            notifyListeners();

            mUIActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mUIActivity.bleDisconnected();
                    mBTGatt.close();
                }
            });
        }

        mMode = UNBOUND_MODE;
    }

    private boolean isBLEEnabled() {
        BluetoothManager btManager;
        BluetoothAdapter adapter;
        btManager = (BluetoothManager) mUIActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = btManager.getAdapter();
        return ((null != adapter) && adapter.isEnabled());
    }

    private void enableBLE() {
        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mUIActivity.startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

    private void notifyListeners() {
        for (RSSIEventListener listener : mListeners) {
            listener.onRSSIUpdate(mRSSI);
        }
    }

    @Override
    public void onModelUpdate(int throttle, int pitch, int roll, int yaw, boolean isBound) {
        mCtlBLECmd[1] = (byte) throttle;
        mCtlBLECmd[2] = (byte) pitch;
        mCtlBLECmd[3] = (byte) roll;
        mCtlBLECmd[4] = (byte) yaw;

        if ((BOUND_MODE == mMode) && !mBLEWritePending) {
            if (!sendDataToQuad(mCtlBLECmd)) {
                toastAndDisconnect("Failed to write CTL command.");
            }
        }
    }
}