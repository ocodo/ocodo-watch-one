package info.ocodo.android.wearable.watchface;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.result.DailyTotalResult;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static info.ocodo.android.wearable.watchface.R.dimen;
import static info.ocodo.android.wearable.watchface.R.string;
import static java.lang.String.format;

public class OcodoWatchFaceTwo extends CanvasWatchFaceService {

    private static final String TAG = "OcodoWatchFaceTwo";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final float HOUR_STROKE_WIDTH = 16f;
        private static final float MINUTE_STROKE_WIDTH = 8f;
        private static final float SECOND_TICK_STROKE_WIDTH = 10f;
        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 30f;
        private static final int SHADOW_RADIUS = 12;
        private static final int MSG_UPDATE_TIME = 0;

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
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, 90L);
                        }
                        break;
                }
            }
        };

        private int mWidth;
        private int BACKGROUND_COLOR = getResources().getColor(R.color.bg);
        private int TEXT_COLON_COLOR = getResources().getColor(R.color.colon);
        private int TEXT_STEP_COUNT_COLOR = getResources().getColor(R.color.step_count);
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

        private float mOcodoWidth;
        private float mOcodoCentering;
        private boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }

            super.onCreate(holder);

            mWatchHandColor = TEXT_COLON_COLOR;
            mWatchHandShadowColor = BACKGROUND_COLOR;


            mHourHandPaint = new Paint();
            mHourHandPaint.setColor(TEXT_COLON_COLOR);
            mHourHandPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourHandPaint.setAntiAlias(true);
            mHourHandPaint.setStyle(Paint.Style.STROKE);
            mHourHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinuteHandPaint = new Paint();
            mMinuteHandPaint.setColor(TEXT_COLON_COLOR);
            mMinuteHandPaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinuteHandPaint.setAntiAlias(true);
            mMinuteHandPaint.setStyle(Paint.Style.STROKE);
            mMinuteHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondHandPaint = new Paint();
            mSecondHandPaint.setColor(TEXT_STEP_COUNT_COLOR);
            mSecondHandPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondHandPaint.setAntiAlias(true);
            mSecondHandPaint.setStyle(Paint.Style.STROKE);
            mSecondHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(TEXT_COLON_COLOR);
            mTickAndCirclePaint.setStrokeWidth(0f);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mHourTickPaint = new Paint();
            mHourTickPaint.setColor(TEXT_COLON_COLOR);
            mHourTickPaint.setStrokeWidth(10f);
            mHourTickPaint.setAntiAlias(true);
            mHourTickPaint.setStyle(Paint.Style.STROKE);
            mHourTickPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            normalTypeface = Typeface.createFromAsset(getAssets(), "gothamrnd-light.ttf");

            setWatchFaceStyle(new WatchFaceStyle.Builder(OcodoWatchFaceTwo.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mOcodoTextPaint = createTextPaint(TEXT_COLON_COLOR, normalTypeface);
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
            OcodoWatchFaceTwo.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            OcodoWatchFaceTwo.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);
            Resources resources = getResources();
            mOcodoTextPaint.setTextSize(resources.getDimension(dimen.ocodo_logo_text_size));
            mOcodoWidth = mOcodoTextPaint.measureText(resources.getString(string.ocodo_two_logo_text));
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
                mHourHandPaint.setColor(TEXT_COLON_COLOR);
                mMinuteHandPaint.setColor(TEXT_COLON_COLOR);
                mSecondHandPaint.setColor(TEXT_STEP_COUNT_COLOR);
                mTickAndCirclePaint.setColor(TEXT_COLON_COLOR);
                mHourTickPaint.setColor(TEXT_COLON_COLOR);

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
                mSecondHandPaint.setColor(TEXT_STEP_COUNT_COLOR);
                mTickAndCirclePaint.setColor(mWatchHandColor);
                mHourTickPaint.setColor(mWatchHandColor);

                mHourHandPaint.setAntiAlias(true);
                mMinuteHandPaint.setAntiAlias(true);
                mSecondHandPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);
                mHourTickPaint.setAntiAlias(true);

                mHourHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinuteHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mHourTickPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            canvas.drawColor(BACKGROUND_COLOR);
            float innerTickRadius = mCenterX - 25;
            float outerTickRadius = mCenterX - 20;
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

            innerTickRadius = mCenterX - 70;
            outerTickRadius = mCenterX - 35;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }

            float ocodoTextYOffset = 120f;
            canvas.drawText(getString(string.ocodo_two_logo_text), mOcodoCentering, ocodoTextYOffset, mOcodoTextPaint);

            @SuppressLint("SimpleDateFormat")
            String date = new SimpleDateFormat("dd MMM").format(new Date()).toUpperCase();
            float dateWidth = mOcodoTextPaint.measureText(date);
            float mDateOffsetY = 300f;
            canvas.drawText(date, (mWidth / 2) - (dateWidth / 2), mDateOffsetY, mOcodoTextPaint);

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
                        mCenterY - 180,
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
            mSecondHandLength = (float) (mCenterX * 1.0);
            sMinuteHandLength = (float) (mCenterX * 0.83);
            sHourHandLength = (float) (mCenterX * 0.55);
        }
    }
}
