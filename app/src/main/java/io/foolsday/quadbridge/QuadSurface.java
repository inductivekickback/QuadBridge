package io.foolsday.quadbridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class QuadSurface extends SurfaceView implements SurfaceHolder.Callback,
        Accel.AccelEventListener,
        QuadModel.QuadModelEventListener,
        BLE.RSSIEventListener {

    // These constants are used for fine-tuning the UI.
    private static final int BUBBLE_LEVEL_RADIUS = 60;
    private static final int DEAD_CENTER_RADIUS = 80;
    private static final int DEAD_CENTER_LINE_WIDTH = 8;
    private static final int RSSI_FONT_SIZE = 48;
    private static final int RSSI_TEXT_LEFT_MARGIN = 20;
    private static final int RSSI_TEXT_BOTTOM_MARGIN = 10;
    private static final int REDRAW_INTERVAL_MS = 50;
    private static final float BUBBLE_SCALER = 2.0f;
    private static final int DIR_ARROW_RADIUS = 150;
    private static final int ARROW_SIDE_LEN = 20;

    private SurfaceHolder mHolder;
    private Handler mHandler;
    private Runnable mRedrawRunnable;

    private Paint mPaint;
    private Rect mTextMeasureRect;
    private int mPixelFormat;
    private int mWidth;
    private int mHeight;
    private int mMidX;
    private int mMidY;
    private RectF mCircleRect;
    private Path mArrowPath;
    private float mBubbleCenterX; // The bubble values come from the accelerometer.
    private float mBubbleCenterY;
    private boolean mIsBound;
    private int mThrottle;
    private int mPitch;
    private int mRoll;
    private int mYaw;
    private int mRSSI;

    public QuadSurface(Context context, AttributeSet attrSet) {
        super(context, attrSet);
        mHolder = getHolder();
        mHolder.addCallback(this);

        mHandler = new Handler();
        mRedrawRunnable = new Runnable() {
            @Override
            public void run() {
                mHandler.postDelayed(this, REDRAW_INTERVAL_MS);
                QuadSurface.this.invalidate();
            }
        };
        mHandler.postDelayed(mRedrawRunnable, REDRAW_INTERVAL_MS);

        mPaint = new Paint();
        mPaint.setTextSize(RSSI_FONT_SIZE);
        mArrowPath = new Path();

        mTextMeasureRect = new Rect();
        mPaint.getTextBounds("BLE RSSI: ?", 0, 7, mTextMeasureRect);

        mBubbleCenterX = 0;
        mBubbleCenterY = 0;

        mIsBound = false;
        mThrottle = 0;
        mPitch = 0;
        mRoll = 0;
        mYaw = 0;
        mRSSI = BLE.INVALID_RSSI;
    }

    @Override
    public void onAccelUpdate(float x, float y, float z, float maxAccel) {
        mBubbleCenterX = mMidX;
        mBubbleCenterY = mMidY;

        // Calculate the bubble position.
        mBubbleCenterX -= (mBubbleCenterX * (x / maxAccel) * BUBBLE_SCALER);
        mBubbleCenterY += (mBubbleCenterY * (y / maxAccel) * BUBBLE_SCALER);

        if (mBubbleCenterX < 0) {
            mBubbleCenterX = 0;
        }

        if (mBubbleCenterY < 0) {
            mBubbleCenterY = 0;
        }

        if (mBubbleCenterX > mWidth) {
            mBubbleCenterX = mWidth;
        }

        if (mBubbleCenterY > mHeight) {
            mBubbleCenterY = mHeight;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Clear the background.
        canvas.drawColor(Color.BLACK);

        // Draw the throttle as a filled circle.
        int degrees = (int)((((float)mThrottle) / QuadModel.MAX_THROTTLE_VALUE) * 180);
        int startAngle;
        if (degrees < 90) {
            startAngle = (90 - degrees);
        } else {
            startAngle = (450 - degrees);
        }
        int sweep = (degrees * 2);
        int left = (mMidX - DEAD_CENTER_RADIUS);
        int top = (mMidY - DEAD_CENTER_RADIUS);
        mPaint.setColor(Color.RED);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawArc(left,
                top,
                left + (DEAD_CENTER_RADIUS * 2),
                top + (DEAD_CENTER_RADIUS * 2),
                startAngle,
                sweep,
                false,
                mPaint);

        // Draw the dead center.
        if ((Math.abs(mBubbleCenterX - mMidX) < (DEAD_CENTER_RADIUS - BUBBLE_LEVEL_RADIUS)) &&
                (Math.abs(mBubbleCenterY - mMidY) < (DEAD_CENTER_RADIUS - BUBBLE_LEVEL_RADIUS))) {
            mPaint.setColor(Color.YELLOW);
        } else {
            mPaint.setColor(Color.DKGRAY);
        }
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(DEAD_CENTER_LINE_WIDTH);
        canvas.drawCircle(mMidX, mMidY, DEAD_CENTER_RADIUS, mPaint);

        if (BLE.INVALID_RSSI != mRSSI) {
            mPaint.setColor(Color.GRAY);
            mPaint.setStyle(Paint.Style.FILL);

            String rssiText = String.format("BLE RSSI: %d", mRSSI);

            canvas.drawText(rssiText,
                    RSSI_TEXT_LEFT_MARGIN,
                    (mHeight - mTextMeasureRect.height() - RSSI_TEXT_BOTTOM_MARGIN),
                    mPaint);
        }

        // Draw the bubble.
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setARGB(255, 155, 155, 155);
        canvas.drawCircle(mBubbleCenterX, mBubbleCenterY, BUBBLE_LEVEL_RADIUS, mPaint);

        if (null == mCircleRect) {
            return;
        }

        // Draw the arrow.
        mArrowPath.reset();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.YELLOW);

        if (15 > Math.abs(mPitch)) {
            if (-5 > mYaw) {
                mArrowPath.addArc(mCircleRect, 10, 345);
                mArrowPath.rMoveTo(0, 5);
                mArrowPath.rLineTo(-ARROW_SIDE_LEN, -ARROW_SIDE_LEN);
                mArrowPath.rMoveTo((2 * ARROW_SIDE_LEN), 0);
                mArrowPath.rLineTo((-ARROW_SIDE_LEN - 3), (ARROW_SIDE_LEN + 3));
                canvas.drawPath(mArrowPath, mPaint);
            } else if (5 < mYaw) {
                mArrowPath.addArc(mCircleRect, 170, -345);
                mArrowPath.rMoveTo(0, 5);
                mArrowPath.rLineTo(-ARROW_SIDE_LEN, -ARROW_SIDE_LEN);
                mArrowPath.rMoveTo((2 * ARROW_SIDE_LEN), 0);
                mArrowPath.rLineTo((-ARROW_SIDE_LEN - 3), (ARROW_SIDE_LEN + 3));
                canvas.drawPath(mArrowPath, mPaint);
            }
        } else {
            if (0 < mPitch) {
                mArrowPath.moveTo(mMidX, mCircleRect.top);
                mArrowPath.cubicTo(mMidX,
                        mCircleRect.top,
                        mMidX,
                        (mCircleRect.top - (2 * mPitch)),
                        (mMidX - (2 * mYaw)),
                        (mCircleRect.top - (2 * mPitch)));
                if (ARROW_SIDE_LEN < Math.abs(mYaw)) {
                    if (0 < mYaw) {
                        mArrowPath.rMoveTo(-5, 0);
                        mArrowPath.rLineTo(ARROW_SIDE_LEN, ARROW_SIDE_LEN);
                        mArrowPath.rMoveTo(0, -(2 * ARROW_SIDE_LEN));
                        mArrowPath.rLineTo((-ARROW_SIDE_LEN - 3), (ARROW_SIDE_LEN + 3));
                    } else {
                        mArrowPath.rMoveTo(5, 0);
                        mArrowPath.rLineTo(-ARROW_SIDE_LEN, ARROW_SIDE_LEN);
                        mArrowPath.rMoveTo(0, -(2 * ARROW_SIDE_LEN));
                        mArrowPath.rLineTo((ARROW_SIDE_LEN + 3), (ARROW_SIDE_LEN + 3));
                    }
                }
                canvas.drawPath(mArrowPath, mPaint);
            } else {
                mArrowPath.moveTo(mMidX, mCircleRect.right);
                mArrowPath.cubicTo(mMidX,
                        mCircleRect.right,
                        mMidX,
                        (mCircleRect.bottom - (2 * mPitch)),
                        (mMidX - (2 * mYaw)),
                        (mCircleRect.bottom - (2 * mPitch)));
                if (ARROW_SIDE_LEN < Math.abs(mYaw)) {
                    if (0 < mYaw) {
                        mArrowPath.rMoveTo(-5, 0);
                        mArrowPath.rLineTo(ARROW_SIDE_LEN, ARROW_SIDE_LEN);
                        mArrowPath.rMoveTo(0, -(2 * ARROW_SIDE_LEN));
                        mArrowPath.rLineTo((-ARROW_SIDE_LEN - 3), (ARROW_SIDE_LEN + 3));
                    } else {
                        mArrowPath.rMoveTo(5, 0);
                        mArrowPath.rLineTo(-ARROW_SIDE_LEN, ARROW_SIDE_LEN);
                        mArrowPath.rMoveTo(0, -(2 * ARROW_SIDE_LEN));
                        mArrowPath.rLineTo((ARROW_SIDE_LEN + 3), (ARROW_SIDE_LEN + 3));
                    }
                }
                canvas.drawPath(mArrowPath, mPaint);
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Canvas c = mHolder.lockCanvas(null);
        draw(c);
        mHolder.unlockCanvasAndPost(c);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mPixelFormat = format;
        mWidth = width;
        mHeight = height;
        mMidX = (width / 2);
        mMidY = (height / 2);
        mCircleRect = new RectF((mMidX - DIR_ARROW_RADIUS),
                (mMidY - DIR_ARROW_RADIUS),
                (mMidX + DIR_ARROW_RADIUS),
                (mMidY + DIR_ARROW_RADIUS));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    public void onModelUpdate(int throttle, int pitch, int roll, int yaw, boolean isBound) {
        mIsBound = isBound;
        mThrottle = throttle;
        mPitch = pitch;
        mRoll = roll;
        mYaw = yaw;
    }

    @Override
    public void onRSSIUpdate(int rssi) {
        mRSSI = rssi;
    }
}
