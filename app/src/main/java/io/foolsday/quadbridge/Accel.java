package io.foolsday.quadbridge;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;

public class Accel implements SensorEventListener {

    public static final int UPDATE_INTERVAL_US = 30000;

    public static interface AccelEventListener {
        public void onAccelUpdate(float x, float y, float z, float maxAccel);
    }

    private static final float DEFAULT_MAX_ACCEL_VALUE = 1.0F;

    private SensorManager mSensorManager;
    private Sensor mSensor;

    private ArrayList<AccelEventListener> mListeners;

    // The maximum value differs on each device. The getMaximumRange function does not seem
    // to be reliable.
    private float mMaxVal = DEFAULT_MAX_ACCEL_VALUE;
    private int mAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW;

    private float mXVal = 0;
    private float mYVal = 0;
    private float mZVal = 0;

    public Accel(Activity activity) {
        // Make sure than an accelerometer is present.
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mListeners = new ArrayList<>();
    }

    public boolean hasSensor() {
        return (null != mSensor);
    }

    public void addListener(AccelEventListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(AccelEventListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    public void start() {
        mSensorManager.registerListener(this,
                mSensor,
                UPDATE_INTERVAL_US);
    }

    public void stop() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
        // i.e. SensorManager.SENSOR_STATUS_ACCURACY_HIGH, LOW, or MEDIUM.
        mAccuracy = accuracy;
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // NOTE: This function is called on the UI thread.
        mXVal = event.values[0];
        mYVal = event.values[1];
        mZVal = event.values[2];

        // The max value can't be determined in advance so it will be discovered empirically.
        mMaxVal = Math.max(Math.abs(mXVal), Math.abs(mMaxVal));
        mMaxVal = Math.max(Math.abs(mYVal), Math.abs(mMaxVal));
        mMaxVal = Math.max(Math.abs(mZVal), Math.abs(mMaxVal));

        for (AccelEventListener listener : mListeners) {
            listener.onAccelUpdate(mXVal, mYVal, mZVal, mMaxVal);
        }
    }

    public float getXValPercent() {
        return (mXVal / mMaxVal);
    }

    public float getYValPercent() {
        return (mYVal / mMaxVal);
    }

    public float getZValPercent() {
        return (mZVal / mMaxVal);
    }

    public float getXVal() {
        return mXVal;
    }

    public float getYVal() {
        return mYVal;
    }

    public float getZVal() {
        return mZVal;
    }
}