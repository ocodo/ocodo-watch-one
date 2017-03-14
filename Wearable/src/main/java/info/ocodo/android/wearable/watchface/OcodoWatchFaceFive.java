package info.ocodo.android.wearable.watchface;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static info.ocodo.android.wearable.watchface.R.dimen;
import static info.ocodo.android.wearable.watchface.R.string;

public class OcodoWatchFaceFive extends CanvasWatchFaceService {

    private static final String TAG = "OcodoWatchFaceFive";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final float HOUR_HAND_THICKNESS = 18f;
        private static final float MINUTE_HAND_THICKNESS = 14f;
        private static final float SECOND_TICKS_THICKNESS = 10f;
        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 30f;
        private static final int SHADOW_RADIUS = 3;
        private static final int MSG_UPDATE_TIME = 0;

        private float mOcodoWidth;
        private float mOcodoCentering;
        private boolean mLowBitAmbient;

        private float secondHandLengthPercent = 1.0f;
        private float secondHandCenterOffsetPercent = 0.78f;

        private float minuteHandLengthPercent = 0.95f;
        private float hourHandLengthPercent = 0.70f;

        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        invalidate();
                        if (shouldUpdateTimeHandlerBeRunning()) {
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, 50L);
                        }
                        break;
                }
            }
        };

        private int mWidth;
        private int mHeight;
        private int LIGHT_BG = getResources().getColor(R.color.light_bg);
        private int DARK_HANDS = getResources().getColor(R.color.bg);
        private int SECOND_HAND = getResources().getColor(R.color.second_hand);
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        private int mWatchHandColor;
        private int mWatchHandShadowColor;
        private Typeface normalTypeface;

        private boolean mRegisteredReceiver = false;
        private Paint mOcodoTextPaint;
        private Paint mHourHandPaint;
        private Paint mMinuteHandPaint;
        private Paint mSecondHandPaint;
        private Paint mTickAndCirclePaint;
        private Paint mHourTickPaint;
        private Calendar mCalendar;

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };


        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }

            super.onCreate(holder);

            mWatchHandColor = DARK_HANDS;
            mWatchHandShadowColor = LIGHT_BG;


            mHourHandPaint = new Paint();
            mHourHandPaint.setColor(DARK_HANDS);
            mHourHandPaint.setStrokeWidth(HOUR_HAND_THICKNESS);
            mHourHandPaint.setAntiAlias(true);
            mHourHandPaint.setStyle(Paint.Style.STROKE);
            mHourHandPaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);

            mMinuteHandPaint = new Paint();
            mMinuteHandPaint.setColor(DARK_HANDS);
            mMinuteHandPaint.setStrokeWidth(MINUTE_HAND_THICKNESS);
            mMinuteHandPaint.setAntiAlias(true);
            mMinuteHandPaint.setStyle(Paint.Style.STROKE);
            mMinuteHandPaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);

            mSecondHandPaint = new Paint();
            mSecondHandPaint.setColor(SECOND_HAND);
            mSecondHandPaint.setStrokeWidth(SECOND_TICKS_THICKNESS);
            mSecondHandPaint.setAntiAlias(true);
            mSecondHandPaint.setStyle(Paint.Style.STROKE);
            mSecondHandPaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(DARK_HANDS);
            mTickAndCirclePaint.setStrokeWidth(4f);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);

            mHourTickPaint = new Paint();
            mHourTickPaint.setColor(DARK_HANDS);
            mHourTickPaint.setStrokeWidth(10f);
            mHourTickPaint.setAntiAlias(true);
            mHourTickPaint.setStyle(Paint.Style.STROKE);
            mHourTickPaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);

            normalTypeface = Typeface.createFromAsset(getAssets(), "gothamrnd-light.ttf");

            setWatchFaceStyle(new WatchFaceStyle.Builder(OcodoWatchFaceFive.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mOcodoTextPaint = createTextPaint(DARK_HANDS, normalTypeface);
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int color, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onVisibilityChanged: " + visible);
            }
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }


        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            OcodoWatchFaceFive.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            OcodoWatchFaceFive.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);
            Resources resources = getResources();
            mOcodoTextPaint.setTextSize(resources.getDimension(dimen.ocodo_logo_text_size));
            mOcodoWidth = mOcodoTextPaint.measureText(resources.getString(string.ocodo_five_logo_text));
            mOcodoCentering = (mWidth / 2f) - (mOcodoWidth / 2);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection
                        + ", low-bit ambient = " + mLowBitAmbient);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }

            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }

            invalidate();
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (isInAmbientMode()) {
                mHourHandPaint.setColor(DARK_HANDS);
                mMinuteHandPaint.setColor(DARK_HANDS);
                mSecondHandPaint.setColor(SECOND_HAND);
                mTickAndCirclePaint.setColor(DARK_HANDS);
                mHourTickPaint.setColor(DARK_HANDS);

                mHourHandPaint.setAntiAlias(false);
                mMinuteHandPaint.setAntiAlias(false);
                mSecondHandPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);
                mHourTickPaint.setAntiAlias(false);

                mHourHandPaint.clearShadowLayer();
                mMinuteHandPaint.clearShadowLayer();
                mSecondHandPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();
                mHourTickPaint.clearShadowLayer();
            } else {
                mHourHandPaint.setColor(mWatchHandColor);
                mMinuteHandPaint.setColor(mWatchHandColor);
                mSecondHandPaint.setColor(SECOND_HAND);
                mTickAndCirclePaint.setColor(mWatchHandColor);
                mHourTickPaint.setColor(mWatchHandColor);

                mHourHandPaint.setAntiAlias(true);
                mMinuteHandPaint.setAntiAlias(true);
                mSecondHandPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);
                mHourTickPaint.setAntiAlias(true);

                mHourHandPaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
                mMinuteHandPaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
                mSecondHandPaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);

                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
                mHourTickPaint.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            canvas.drawColor(LIGHT_BG);
            float innerTickRadius = mCenterX - 25;
            float outerTickRadius = mCenterX - 10;
            for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 60);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                if (tickIndex % 5 != 0) {
                    canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                            mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
                }
            }

            innerTickRadius = mCenterX - 45;
            outerTickRadius = mCenterX - 10;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                if(tickIndex != 3) {
                    canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                            mCenterX + outerX, mCenterY + outerY, mHourTickPaint);
                }
            }

            float ocodoTextYOffset = 120f;
            canvas.drawText(getString(string.ocodo_five_logo_text), mOcodoCentering, ocodoTextYOffset, mOcodoTextPaint);

            @SuppressLint("SimpleDateFormat")
            String date = new SimpleDateFormat("dd MMM").format(new Date()).toUpperCase();
            float dateWidth = mOcodoTextPaint.measureText(date);
            float mDateOffsetY = mHeight / 2;
            canvas.drawText(date, (mWidth / 2) + dateWidth - 10, mDateOffsetY + 8, mOcodoTextPaint);

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            updateWatchHandStyle();

            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY + CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourHandPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY + CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinuteHandPaint);

            if (!isInAmbientMode()) {
                final float milliseconds = ((mCalendar.get(Calendar.SECOND) * 1000) +
                        mCalendar.get(Calendar.MILLISECOND));

                final float secondsRotation = milliseconds * 0.006f;

                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - ((mWidth/2) * secondHandCenterOffsetPercent),
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondHandPaint);
            }

            canvas.restore();
        }

        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldUpdateTimeHandlerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldUpdateTimeHandlerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mCenterX = width / 2f;
            mCenterY = height / 2f;
            mWidth = width;
            mHeight = height;
            mSecondHandLength = (float) (mCenterX * secondHandLengthPercent);
            sMinuteHandLength = (float) (mCenterX * minuteHandLengthPercent);
            sHourHandLength = (float) (mCenterX * hourHandLengthPercent);
        }
    }
}
