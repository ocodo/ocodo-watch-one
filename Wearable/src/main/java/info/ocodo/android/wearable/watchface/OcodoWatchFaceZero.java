package info.ocodo.android.wearable.watchface;

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

import static info.ocodo.android.wearable.watchface.R.*;
import static java.lang.String.format;

public class OcodoWatchFaceZero extends CanvasWatchFaceService {

    private static final String TAG = "OcodoWatchFaceZero";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            ResultCallback<DailyTotalResult> {

        private static final float HOUR_STROKE_WIDTH = 10f;
        private static final float MINUTE_STROKE_WIDTH = 4f;
        private static final float SECOND_TICK_STROKE_WIDTH = 10f;
        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 80f;
        private static final int SHADOW_RADIUS = 10;
        private static final String COLON_STRING = ":";
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
        private int mHeight;
        private int BACKGROUND_COLOR = Color.parseColor("#191620");
        private int TEXT_HOURS_MINS_COLOR = Color.parseColor("#DAD0BD");
        private int TEXT_SECONDS_COLOR = Color.parseColor("#9A9185");
        private int TEXT_AM_PM_COLOR = Color.parseColor("#9A9185");
        private int TEXT_COLON_COLOR = Color.parseColor("#666058");
        private int TEXT_STEP_COUNT_COLOR = Color.parseColor("#017483");
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        private int mWatchHandColor;
        private int mWatchHandShadowColor;
        private Typeface normalTypeface;

        private boolean mRegisteredReceiver = false;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mAmPmPaint;
        private Paint mColonPaint;
        private Paint mStepCountPaint;
        private Paint mOcodoTextPaint;
        private Paint mHourHandPaint;
        private Paint mMinuteHandPaint;
        private Paint mSecondHandPaint;
        private Paint mTickAndCirclePaint;
        private Paint mHourTickPaint;
        private float mColonWidth;
        private Calendar mCalendar;

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private float mXOffset;
        private float mXStepsOffset;
        private float mYOffset;
        private float mLineHeight;
        private float mOcodoWidth;
        private float mOcodoCentering;
        private String mAmString;
        private String mPmString;
        private String mOcodoText;
        private boolean mLowBitAmbient;
        private GoogleApiClient mGoogleApiClient;
        private boolean mStepsRequested;
        private int mStepsTotal = 0;

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

            mStepsRequested = false;
            mGoogleApiClient = new GoogleApiClient.Builder(OcodoWatchFaceZero.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Fitness.HISTORY_API)
                    .addApi(Fitness.RECORDING_API)
                    .useDefaultAccount()
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(OcodoWatchFaceZero.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = getResources();

            mYOffset = resources.getDimension(dimen.y_offset);
            mLineHeight = resources.getDimension(dimen.line_height);
            mAmString = resources.getString(string.am_text);
            mPmString = resources.getString(string.pm_text);

            mHourPaint = createTextPaint(TEXT_HOURS_MINS_COLOR, normalTypeface);
            mMinutePaint = createTextPaint(TEXT_HOURS_MINS_COLOR, normalTypeface);
            mSecondPaint = createTextPaint(TEXT_SECONDS_COLOR, normalTypeface);
            mAmPmPaint = createTextPaint(TEXT_AM_PM_COLOR, normalTypeface);
            mColonPaint = createTextPaint(TEXT_COLON_COLOR, normalTypeface);
            mStepCountPaint = createTextPaint(TEXT_STEP_COUNT_COLOR, normalTypeface);
            mOcodoTextPaint = createTextPaint(TEXT_COLON_COLOR, normalTypeface);
            mOcodoText = resources.getString(string.ocodo_zero_logo_text);
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
                mGoogleApiClient.connect();

                registerReceiver();

                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
            }

            updateTimer();
        }


        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            OcodoWatchFaceZero.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            OcodoWatchFaceZero.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);

            Resources resources = OcodoWatchFaceZero.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? dimen.x_offset_round : dimen.x_offset);
            mXStepsOffset = resources.getDimension(isRound
                    ? dimen.steps_or_distance_x_offset_round : dimen.steps_or_distance_x_offset);
            float textSize = resources.getDimension(isRound
                    ? dimen.hr_min_text_size_round : dimen.hr_min_text_size);
            float amPmSize = resources.getDimension(isRound
                    ? dimen.am_pm_size_round : dimen.am_pm_size);

            Paint mHourPaint = this.mHourPaint;
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mSecondPaint.setTextSize(resources.getDimension(dimen.seconds_text_size));
            mAmPmPaint.setTextSize(amPmSize);
            mColonPaint.setTextSize(resources.getDimension(dimen.colon_text_size));
            mOcodoTextPaint.setTextSize(resources.getDimension(dimen.ocodo_logo_text_size));
            mStepCountPaint.setTextSize(resources.getDimension(dimen.steps_or_distance_text_size));
            mColonWidth = mColonPaint.measureText(COLON_STRING);
            mOcodoWidth = mOcodoTextPaint.measureText(mOcodoText);
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

            getTotalSteps();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mSecondPaint.setAntiAlias(antiAlias);
                mAmPmPaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
                mStepCountPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            updateTimer();
        }

        private String formatTwoDigitNumber(int hour) {
            return format("%02d", hour);
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
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
            boolean is24Hour = DateFormat.is24HourFormat(OcodoWatchFaceZero.this);

            canvas.drawColor(BACKGROUND_COLOR);

            float innerTickRadius = mCenterX - 15;
            float outerTickRadius = mCenterX - 10;
            for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 60);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }

            innerTickRadius = mCenterX - 20;
            outerTickRadius = mCenterX;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mHourTickPaint);
            }

            float mColonYOffset = mYOffset - 10;

            float ocodoTextYOffset = 90f;
            canvas.drawText(mOcodoText, mOcodoCentering, ocodoTextYOffset, mOcodoTextPaint);

            float x = mXOffset;
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            canvas.drawText(hourString, x, mYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);

            Boolean mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            if (isInAmbientMode() || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mColonYOffset, mColonPaint);
            }
            x += mColonWidth;

            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
            x += mMinutePaint.measureText(minuteString);

            if (!isInAmbientMode()) {
                String secondsString = formatTwoDigitNumber(mCalendar.get(Calendar.SECOND));
                canvas.drawText(secondsString, x, mYOffset - 20, mSecondPaint);
                DecimalFormat df = new DecimalFormat("###,###");
                String stepString = getString(string.steps_text, df.format(mStepsTotal));
                canvas.drawText(stepString, mXStepsOffset, mYOffset + mLineHeight, mStepCountPaint);
            }

            if (!is24Hour) {
                canvas.drawText(getAmPmString(
                        mCalendar.get(Calendar.AM_PM)), x, mYOffset, mAmPmPaint);
            }

            String date = new SimpleDateFormat("dd MMM").format(new Date()).toUpperCase();
            float dateWidth = mAmPmPaint.measureText(date);
            canvas.drawText(date, (mWidth / 2) - (dateWidth / 2), 300f, mAmPmPaint);

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            updateWatchHandStyle();

            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourHandPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
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

        private void getTotalSteps() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "getTotalSteps()");
            }

            if ((mGoogleApiClient != null)
                    && (mGoogleApiClient.isConnected())
                    && (!mStepsRequested)) {

                mStepsRequested = true;

                PendingResult<DailyTotalResult> stepsResult =
                        Fitness.HistoryApi.readDailyTotal(
                                mGoogleApiClient,
                                DataType.TYPE_STEP_COUNT_DELTA);

                stepsResult.setResultCallback(this);
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "mGoogleApiAndFitCallbacks.onConnected: " + connectionHint);
            }
            mStepsRequested = false;
            subscribeToSteps();
            getTotalSteps();
        }

        private void subscribeToSteps() {
            Fitness.RecordingApi.subscribe(mGoogleApiClient, DataType.TYPE_STEP_COUNT_DELTA)
                    .setResultCallback(new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                            if (status.isSuccess()) {
                                if (status.getStatusCode()
                                        == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                    Log.i(TAG, "Existing subscription for activity detected.");
                                } else {
                                    Log.i(TAG, "Successfully subscribed!");
                                }
                            } else {
                                Log.i(TAG, "There was a problem subscribing.");
                            }
                        }
                    });
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mCenterX = width / 2f;
            mCenterY = height / 2f;

            mWidth = width;
            mHeight = height;

            mSecondHandLength = (float) (mCenterX * 1.0);
            sMinuteHandLength = (float) (mCenterX * 0.85);
            sHourHandLength = (float) (mCenterX * 0.75);
        }

        @Override
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "mGoogleApiAndFitCallbacks.onConnectionSuspended: " + cause);
            }
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "mGoogleApiAndFitCallbacks.onConnectionFailed: " + result);
            }
        }

        @Override
        public void onResult(DailyTotalResult dailyTotalResult) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "mGoogleApiAndFitCallbacks.onResult(): " + dailyTotalResult);
            }
            mStepsRequested = false;
            if (dailyTotalResult.getStatus().isSuccess()) {
                List<DataPoint> points = dailyTotalResult.getTotal().getDataPoints();
                if (!points.isEmpty()) {
                    mStepsTotal = points.get(0).getValue(Field.FIELD_STEPS).asInt();
                    Log.d(TAG, "steps updated: " + mStepsTotal);
                }
            } else {
                Log.e(TAG, "onResult() failed! " + dailyTotalResult.getStatus().getStatusMessage());
            }
        }
    }
}
