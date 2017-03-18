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
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
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

public class OcodoWatchFaceSix extends CanvasWatchFaceService {

    private static final String TAG = "OcodoWatchFaceSix";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            ResultCallback<DailyTotalResult> {

        private static final float HOUR_HAND_THICKNESS = 18f;
        private static final float MINUTE_HAND_THICKNESS = 14f;
        private static final float SECOND_TICKS_THICKNESS = 10f;
        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 30f;
        private static final int SHADOW_RADIUS = 3;
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
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, 50L);
                        }
                        break;
                }
            }
        };
        private boolean mLowBitAmbient;
        private float secondHandLengthPercent = 1.0f;
        private float secondHandCenterOffsetPercent = 0.78f;
        private float minuteHandLengthPercent = 0.95f;
        private float hourHandLengthPercent = 0.70f;
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
        private Typeface lightTypeface;

        private boolean mRegisteredReceiver = false;
        private Paint mOcodoTextPaint;
        private Paint mHourTextPaint;
        private Paint mHourHandPaint;
        private Paint mMinuteHandPaint;
        private Paint mSecondHandPaint;
        private Paint mSecondsTickPaint;
        private Paint mHourTickPaint;
        private Calendar mCalendar;

        private GoogleApiClient mGoogleApiClient;
        private boolean mStepsRequested;
        private Paint mStepCountPaint;
        private int mStepsTotal = 0;

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }

            super.onCreate(holder);

            mWatchHandColor = DARK_HANDS;
            mWatchHandShadowColor = LIGHT_BG;

            mHourHandPaint = createStrokePaint(DARK_HANDS, HOUR_HAND_THICKNESS, Paint.Style.STROKE, SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
            mMinuteHandPaint = createStrokePaint(DARK_HANDS, MINUTE_HAND_THICKNESS, Paint.Style.STROKE, SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
            mSecondHandPaint = createStrokePaint(SECOND_HAND, SECOND_TICKS_THICKNESS, Paint.Style.STROKE, SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
            mSecondsTickPaint = createStrokePaint(DARK_HANDS, 4f, Paint.Style.STROKE, SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
            mHourTickPaint = createStrokePaint(DARK_HANDS, 10f, Paint.Style.STROKE, SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);

            lightTypeface = Typeface.createFromAsset(getAssets(), "gothamrnd-light.ttf");
            normalTypeface = Typeface.createFromAsset(getAssets(), "helvetica-75-bold.ttf");

            mStepsRequested = false;
            mGoogleApiClient = new GoogleApiClient.Builder(OcodoWatchFaceSix.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Fitness.HISTORY_API)
                    .addApi(Fitness.RECORDING_API)
                    .useDefaultAccount()
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(OcodoWatchFaceSix.this)
                    .setStatusBarGravity(Gravity.CENTER)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mOcodoTextPaint = createTextPaint(DARK_HANDS, lightTypeface);
            mOcodoTextPaint.setTextAlign(Paint.Align.CENTER);
            mStepCountPaint = createTextPaint(DARK_HANDS, lightTypeface);
            mStepCountPaint.setTextSize(20);
            mStepCountPaint.setTextAlign(Paint.Align.CENTER);
            mHourTextPaint = createTextPaint(DARK_HANDS, normalTypeface);
            mHourTextPaint.setTextSize(60);
            mHourTextPaint.setTextAlign(Paint.Align.CENTER);
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
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

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);
            Resources resources = getResources();
            mOcodoTextPaint.setTextSize(resources.getDimension(dimen.ocodo_logo_text_size));
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

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mCenterX = width / 2f;
            mCenterY = height / 2f;
            mWidth = width;
            mHeight = height;
            mSecondHandLength = mCenterX * secondHandLengthPercent;
            sMinuteHandLength = mCenterX * minuteHandLengthPercent;
            sHourHandLength = mCenterX * hourHandLengthPercent;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            canvas.drawColor(LIGHT_BG);

            for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                drawRepeatingTicks(canvas, mCenterX - 25, mCenterX - 10, tickIndex, 60, mSecondsTickPaint, true);
            }

            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                drawRepeatingTicks(canvas, mCenterX - 45, mCenterX - 10, tickIndex, 12, mHourTickPaint, true);
            }

            drawHourDigits(canvas);

            drawWatchName(canvas);

            drawDayMonthDate(canvas);

            drawDayOfWeek(canvas);

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            drawStepsCount(canvas);
            updateWatchHandStyle();

            canvas.save();

            drawHours(canvas, hoursRotation);
            drawMinutes(canvas, minutesRotation, hoursRotation);
            drawSweepingSecondHand(canvas, minutesRotation);

            canvas.restore();
        }

        private void drawWatchName(Canvas canvas) {
            float ocodoTextYOffset = 120f;
            canvas.drawText(getString(string.ocodo_six_logo_text), mWidth / 2, ocodoTextYOffset, mOcodoTextPaint);
        }

        private void drawDayOfWeek(Canvas canvas) {
            @SuppressLint("SimpleDateFormat")
            String date = new SimpleDateFormat("EEE").format(new Date()).toUpperCase();
            float mDateOffsetY = mHeight * 0.67f;
            canvas.drawText(date, mWidth / 2, mDateOffsetY, mStepCountPaint);
        }

        private void drawDayMonthDate(Canvas canvas) {
            @SuppressLint("SimpleDateFormat")
            String date = new SimpleDateFormat("dd MMM").format(new Date()).toUpperCase();
            float mDateOffsetY = mHeight * 0.73f;
            canvas.drawText(date, mWidth / 2, mDateOffsetY, mOcodoTextPaint);
        }

        private void drawStepsCount(Canvas canvas) {
            if (!isInAmbientMode()) {
                DecimalFormat df = new DecimalFormat("###,###");
                String stepString = getString(string.steps_text, df.format(mStepsTotal));
                float stepsYOffset = mHeight * 0.80f;
                canvas.drawText(stepString, mWidth / 2, stepsYOffset, mStepCountPaint);
            }
        }

        private void drawSweepingSecondHand(Canvas canvas, float minutesRotation) {
            if (!isInAmbientMode()) {
                final float milliseconds = ((mCalendar.get(Calendar.SECOND) * 1000) +
                        mCalendar.get(Calendar.MILLISECOND));
                final float secondsRotation = milliseconds * 0.006f;

                drawClockHand(canvas, secondsRotation - minutesRotation, mCenterX,
                        mCenterY,
                        mCenterY - ((mWidth / 2) * secondHandCenterOffsetPercent),
                        mCenterY - mSecondHandLength,
                        mSecondHandPaint);
            }
        }

        private void drawHours(Canvas canvas, float hoursRotation) {
            drawClockHand(canvas, hoursRotation, mCenterX,
                    mCenterY,
                    mCenterY + CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterY - sHourHandLength,
                    mHourHandPaint);
        }

        private void drawMinutes(Canvas canvas, float minutesRotation, float hoursRotation) {
            drawClockHand(canvas, minutesRotation - hoursRotation, mCenterX,
                    mCenterY,
                    mCenterY + CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterY - sMinuteHandLength,
                    mMinuteHandPaint);
        }

        private String formatTwoDigitNumber(int hour) {
            return format("%02d", hour);
        }

        private void drawClockHand(Canvas canvas, float hoursRotation, float mCenterX, float mCenterY,
                                   float startY, float stopY, Paint mHourHandPaint) {
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    startY,
                    mCenterX,
                    stopY,
                    mHourHandPaint);
        }

        private void drawHourDigits(Canvas canvas) {
            String[] textItems = new String[]{
                    getResources().getString(string.twelve),
                    getResources().getString(string.three),
                    getResources().getString(string.six),
                    getResources().getString(string.nine)
            };
            for (int digitIndex = 0; digitIndex < 4; digitIndex++) {
                drawRepeatingText(textItems[digitIndex], canvas,
                        mCenterX - 60f,
                        digitIndex, 4,
                        mHourTextPaint, true);
            }
        }

        private void drawRepeatingTicks(Canvas canvas, float innerRadius, float outerRadius,
                                        int index, int count, Paint paint, boolean predicate) {
            float tickRot = (float) (index * Math.PI * 2 / count);
            float innerX = (float) Math.sin(tickRot) * innerRadius;
            float innerY = (float) -Math.cos(tickRot) * innerRadius;
            float outerX = (float) Math.sin(tickRot) * outerRadius;
            float outerY = (float) -Math.cos(tickRot) * outerRadius;
            if (predicate) {
                canvas.drawLine(
                        mCenterX + innerX,
                        mCenterY + innerY,
                        mCenterX + outerX,
                        mCenterY + outerY,
                        paint
                );
            }
        }

        private void drawRepeatingText(String text, Canvas canvas, float offset, int index,
                                       int count, Paint paint, boolean predicate) {
            float tickRot = (float) (index * Math.PI * 2 / count);
            float innerX = (float) Math.sin(tickRot) * offset;
            float innerY = (float) -Math.cos(tickRot) * offset;
            float x = (mCenterX + innerX);
            float y = (mCenterY + innerY) - (paint.ascent() * 0.38f);
            if (predicate) canvas.drawText(text, x, y, paint);
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

        private void updateWatchHandStyle() {
            if (isInAmbientMode()) {
                ambientPaint(mHourHandPaint, DARK_HANDS);
                ambientPaint(mMinuteHandPaint, DARK_HANDS);
                ambientPaint(mSecondHandPaint, SECOND_HAND);
                ambientPaint(mHourTickPaint, DARK_HANDS);
                ambientPaint(mSecondsTickPaint, DARK_HANDS);
            } else {
                activePaint(mSecondHandPaint, SECOND_HAND);
                activePaint(mHourTickPaint, mWatchHandColor);
                activePaint(mMinuteHandPaint, mWatchHandColor);
                activePaint(mSecondsTickPaint, mWatchHandColor);
                activePaint(mHourHandPaint, mWatchHandColor);
            }
        }

        private void ambientPaint(Paint p, int color) {
            p.setColor(color);
            p.setAntiAlias(false);
            p.clearShadowLayer();
        }

        private void activePaint(Paint p, int color) {
            p.setColor(color);
            p.setAntiAlias(true);
            p.setShadowLayer(SHADOW_RADIUS, 1.0f, 1.0f, mWatchHandShadowColor);
        }

        private Paint createTextPaint(int color, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createStrokePaint(int color, float strokeWidth, Paint.Style style,
                                        int shadowRadius, float dx, float dy, int shadowColor) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setAntiAlias(true);
            paint.setStyle(style);
            paint.setShadowLayer(shadowRadius, dx, dy, shadowColor);
            return paint;
        }

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            OcodoWatchFaceSix.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            OcodoWatchFaceSix.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onConnected(@Nullable Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "mGoogleApiAndFitCallbacks.onConnected: " + connectionHint);
            }
            mStepsRequested = false;
            subscribeToSteps();
            getTotalSteps();
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
