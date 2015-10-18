package io.foolsday.quadbridge;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;


public class UIActivity extends Activity {

    // The RepeatListener class was copy and pasted verbatim from:
    // http://stackoverflow.com/questions/4284224/android-hold-button-to-repeat-action
    /**
     * A class, that can be used as a TouchListener on any view (e.g. a Button).
     * It cyclically runs a clickListener, emulating keyboard-like behaviour. First
     * click is fired immediately, next after initialInterval, and subsequent after
     * normalInterval.
     *
     * <p>Interval is scheduled after the onClick completes, so it has to run fast.
     * If it runs slow, it does not generate skipped onClicks.
     */
    public class RepeatListener implements View.OnTouchListener {

        private Handler handler = new Handler();

        private int initialInterval;
        private final int normalInterval;
        private final View.OnClickListener clickListener;

        private Runnable handlerRunnable = new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(this, normalInterval);
                clickListener.onClick(downView);
            }
        };

        private View downView;

        /**
         * @param initialInterval The interval after first click event
         * @param normalInterval The interval after second and subsequent click
         *       events
         * @param clickListener The OnClickListener, that will be called
         *       periodically
         */
        public RepeatListener(int initialInterval, int normalInterval,
                              OnClickListener clickListener) {
            if (clickListener == null)
                throw new IllegalArgumentException("null runnable");
            if (initialInterval < 0 || normalInterval < 0)
                throw new IllegalArgumentException("negative interval");

            this.initialInterval = initialInterval;
            this.normalInterval = normalInterval;
            this.clickListener = clickListener;
        }

        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    handler.removeCallbacks(handlerRunnable);
                    handler.postDelayed(handlerRunnable, initialInterval);
                    downView = view;
                    clickListener.onClick(view);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(handlerRunnable);
                    downView = null;
                    return true;
            }

            return false;
        }

    }

    private class DeviceListItem {
        private final BluetoothDevice mDevice;
        private int mRSSI;

        public DeviceListItem(BluetoothDevice device, int rssi) {
            mDevice = device;
            mRSSI = rssi;
        }

        public void setRSSI(int rssi) {
            mRSSI = rssi;
        }

        public BluetoothDevice getDevice() {
            return mDevice;
        }

        @Override
        public boolean equals(Object object) {
            DeviceListItem rhs = (DeviceListItem)object;
            return rhs.getDevice().equals(this.getDevice());
        }

        @Override
        public String toString() {
            return String.format("'%s' (%ddBm)", mDevice.getName(), mRSSI);
        }
    }

    private class BLEScanDialog extends Dialog {

        private final ArrayList<DeviceListItem> mDevices = new ArrayList<>();
        private ArrayAdapter<DeviceListItem> mArrayAdapter;
        private ListView mListView;
        private TextView mLabel;

        public BLEScanDialog(final UIActivity context, final BLE bleParent) {
            super(context);

            setContentView(R.layout.ble_scan_popup_ui);
            setTitle(R.string.select_device_text);
            setCancelable(true);

            mLabel = (TextView) findViewById(R.id.listLabelView);

            mListView = (ListView) findViewById(R.id.bleScanListView);
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {
                    if (position < mDevices.size()) {
                        bleParent.connect(mDevices.get(position).getDevice());
                        hide();
                    }
                }
            });

            mArrayAdapter = new ArrayAdapter<>(context,
                    R.layout.list_view_ui,
                    R.id.bleScanListTextView,
                    mDevices);
            mListView.setAdapter(mArrayAdapter);

            final Button cancelButton = (Button) findViewById(R.id.cancelButton);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bleParent.stopScan();
                    clearScanResults();
                    context.scanDialogClosed();
                    cancel();
                }
            });

            this.setOnCancelListener(new Dialog.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cancelButton.performClick();
                }
            });
        }

        public void clearScanResults() {
            mDevices.clear();
            mArrayAdapter.notifyDataSetChanged();
            mLabel.setVisibility(View.VISIBLE);
        }

        public void addScanResult(BluetoothDevice device, int rssi) {
            DeviceListItem item = new DeviceListItem(device, rssi);
            if (!mDevices.contains(item)) {
                mDevices.add(item);
                if (View.VISIBLE == mLabel.getVisibility()) {
                    mLabel.setVisibility(View.GONE);
                }
            } else {
                int index = mDevices.indexOf(item);
                mDevices.get(index).setRSSI(rssi);
            }
            mArrayAdapter.notifyDataSetChanged();
        }
    }

    private static final int DISCONNECTED_MODE = 0; // Waiting to connect.
    private static final int SERVICE_DISCOVERY_MODE = 1; // GATT discovery
    private static final int UNBOUND_MODE = 2; // Waiting to bind.
    private static final int BOUND_MODE = 3; // Waiting to disconnect.
    private static final int UNBINDING_MODE = 4;

    private int mUIMode = DISCONNECTED_MODE;
    private Accel mAccel;
    private BLE mBLE;
    private QuadModel mQuadModel;
    private QuadSurface mQuadSurface;
    private Button mThrottleUpButton;
    private Button mThrottleDownButton;
    private Button mBindConnectButton;
    private BLEScanDialog mScanDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ui);

        mThrottleUpButton = (Button)findViewById(R.id.throttleUpButton);
        mThrottleDownButton = (Button)findViewById(R.id.throttleDownButton);
        mBindConnectButton = (Button)findViewById(R.id.bindConnectButton);

        mQuadSurface = (QuadSurface)findViewById(R.id.surfaceView);
        mQuadModel = new QuadModel(this);
        mAccel = new Accel(this);
        mBLE = new BLE(this);
        mScanDialog = new BLEScanDialog(this, mBLE);

        mThrottleUpButton.setOnTouchListener(new RepeatListener(400, 100, new OnClickListener() {
            @Override
            public void onClick(View view) {
                mThrottleUpButton.performClick();
            }
        }));

        mThrottleDownButton.setOnTouchListener(new RepeatListener(400, 100, new OnClickListener() {
            @Override
            public void onClick(View view) {
                mThrottleDownButton.performClick();
            }
        }));

        if (!mAccel.hasSensor()) {
            toastAndFinish("No accelerometer detected.");
        }

        if (!mBLE.hasRadio()) {
            toastAndFinish("This device does not support BLE.");
        }

        mAccel.addListener(mQuadSurface);
        mAccel.addListener(mQuadModel);
        mQuadModel.addListener(mQuadSurface);
        mQuadModel.addListener(mBLE);
        mBLE.addListener(mQuadSurface);
    }

    public void bleConnecting() {
        mUIMode = SERVICE_DISCOVERY_MODE;
        mBindConnectButton.setText(R.string.connecting_text);
        mBindConnectButton.setEnabled(false);
    }

    public void bleConnected() {
        mUIMode = UNBOUND_MODE;
        mBindConnectButton.setText(R.string.bind_text);
        mBindConnectButton.setEnabled(true);
    }

    public void bleDisconnected() {
        if (DISCONNECTED_MODE != mUIMode) {
            mUIMode = DISCONNECTED_MODE;
            mQuadModel.reset();
            mBindConnectButton.setText(R.string.connect_text);
            mBindConnectButton.setEnabled(true);
        }
    }

    public void enableThrottleButtons() {
        mThrottleUpButton.setEnabled(true);
        mThrottleDownButton.setEnabled(true);
    }

    public void disableThrottleButtons() {
        mThrottleUpButton.setEnabled(false);
        mThrottleDownButton.setEnabled(false);
    }

    public void onThrottleUpButtonClick(View button) {
        mQuadModel.throttleUp();
    }

    public void onBound() {
        mQuadModel.bind();
    }

    public void onThrottleDownButtonClick(View button) {
        mQuadModel.throttleDown();
    }

    public void onConnectButtonClick(View button) {
        switch (mUIMode) {
            case DISCONNECTED_MODE:
                mBLE.startScan();
                mBindConnectButton.setEnabled(false);
                mScanDialog.show();
                break;
            case UNBOUND_MODE:
                mBLE.bind();
                mUIMode = BOUND_MODE;
                mBindConnectButton.setText(R.string.disconnect_text);
                break;
            case BOUND_MODE:
                mUIMode = UNBINDING_MODE;
                mBindConnectButton.setEnabled(false);
                mBindConnectButton.setText(R.string.disconnecting_text);
                mBLE.unbind();
                break;
            default:
                break;
        }
    }

    public boolean addScanResult(BluetoothDevice device, int rssi) {
        if (mScanDialog.isShowing()) {
            mScanDialog.addScanResult(device, rssi);
            return true;
        } else {
            return false;
        }
    }

    public void scanDialogClosed() {
        mBindConnectButton.setEnabled(true);
    }

    /**
     * A convenience function for allowing the other modules to display
     * an error message and then quit.
     *
     * @param msg
     */
    public void toastAndFinish(String msg) {
        Toast.makeText(this,
                msg,
                Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    final Intent data) {
        switch (requestCode) {
            case BLE.REQUEST_ENABLE_BT:
                mBLE.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Accelerometer data is only required when the app is running.
        mAccel.start();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Accelerometer data is not required until the app resumes.
        mAccel.stop();
        mBLE.disconnect();
        bleDisconnected();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

}
