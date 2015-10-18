package io.foolsday.quadbridge;

import java.util.ArrayList;

public class QuadModel implements Accel.AccelEventListener {

    public interface QuadModelEventListener {
        void onModelUpdate(int throttle, int pitch, int roll, int yaw, boolean isBound);
    }

    public static final int MIN_THROTTLE_VALUE = 0;
    public static final int MAX_THROTTLE_VALUE = 255;
    public static final int MIN_YAW_VALUE = -128;
    public static final int MAX_YAW_VALUE = 127;
    public static final int MIN_ROLL_VALUE = -128;
    public static final int MAX_ROLL_VALUE = 127;
    public static final int MIN_PITCH_VALUE = -128;
    public static final int MAX_PITCH_VALUE = 127;

    public static final float PITCH_SCALER = 2.0f;
    public static final float YAW_SCALER = 2.0f;
    public static final int ROLL_PITCH_THRESHOLD = 50;
    public static final float ROLL_YAW_SCALER = 0.7f;

    private static final int THROTTLE_INCREMENT = 5;

    private ArrayList<QuadModelEventListener> mListeners;
    private UIActivity mUIActivity;

    private float mAccelX;
    private float mAccelY;
    private int mThrottle;
    private int mPitch;
    private int mRoll;
    private int mYaw;
    private boolean mIsBound;

    public QuadModel(UIActivity activity) {
        mUIActivity = activity;

        mAccelX = 0;
        mAccelY = 0;
        mThrottle = MIN_THROTTLE_VALUE;
        mPitch = 0;
        mRoll = 0;
        mYaw = 0;
        mIsBound = false;

        mListeners = new ArrayList<>();

        mUIActivity.disableThrottleButtons();
    }

    public void throttleUp() {
        if (MAX_THROTTLE_VALUE == mThrottle) {
            return;
        } else if ((MAX_THROTTLE_VALUE - THROTTLE_INCREMENT) < mThrottle) {
            mThrottle = MAX_THROTTLE_VALUE;
        } else {
            mThrottle += THROTTLE_INCREMENT;
        }

        notifyListeners();
    }

    public void throttleDown() {
        if (MIN_THROTTLE_VALUE == mThrottle) {
            return;
        } else if ((MIN_THROTTLE_VALUE + THROTTLE_INCREMENT) > mThrottle) {
            mThrottle = MIN_THROTTLE_VALUE;
        } else {
            mThrottle -= THROTTLE_INCREMENT;
        }

        notifyListeners();
    }

    public void bind() {
        mIsBound = true;
        mUIActivity.enableThrottleButtons();
        notifyListeners();
    }

    public void reset() {
        mAccelX = 0;
        mAccelY = 0;
        mThrottle = MIN_THROTTLE_VALUE;
        mPitch = 0;
        mRoll = 0;
        mYaw = 0;
        mIsBound = false;

        mUIActivity.disableThrottleButtons();
        notifyListeners();
    }

    public void addListener(QuadModelEventListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(QuadModelEventListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    @Override
    public void onAccelUpdate(float x, float y, float z, float maxAccel) {
        mAccelX = (x / maxAccel);
        mAccelY = (y / maxAccel);

        mYaw = (int)(mAccelX * MAX_YAW_VALUE * YAW_SCALER);
        mPitch = (int)(mAccelY * MIN_PITCH_VALUE * PITCH_SCALER);

        // Roll should be proportional to yaw but should not be applied below a certain pitch.
        if (mPitch >= ROLL_PITCH_THRESHOLD) {
            mRoll = (int) (mYaw * ROLL_YAW_SCALER);
        } else {
            mRoll = 0;
        }

        if (MAX_YAW_VALUE < mYaw) {
            mYaw = MAX_YAW_VALUE;
        } else if (MIN_YAW_VALUE > mYaw) {
            mYaw = MIN_YAW_VALUE;
        }

        if (MAX_PITCH_VALUE < mPitch) {
            mPitch = MAX_PITCH_VALUE;
        } else if (MIN_PITCH_VALUE > mPitch) {
            mPitch = MIN_PITCH_VALUE;
        }

        if (MAX_ROLL_VALUE < mRoll) {
            mRoll = MAX_ROLL_VALUE;
        } else if (MIN_ROLL_VALUE > mRoll) {
            mRoll = MIN_ROLL_VALUE;
        }

        notifyListeners();
    }

    private void notifyListeners() {
        for (QuadModelEventListener listener : mListeners) {
            listener.onModelUpdate(mThrottle, mPitch, mRoll, mYaw, mIsBound);
        }
    }
}
