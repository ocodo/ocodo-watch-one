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
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.activePaint;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.ambientPaint;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.createStrokePaint;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.createTextPaint;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.drawClockHand;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.drawDayMonthDate;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.drawRepeatingTextDigits;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.drawRepeatingTicks;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.drawStepsCount;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.drawSweepingSecondHand;
import static info.ocodo.android.wearable.watchface.OcodoWatchFaceUtils.drawWatchName;
import static info.ocodo.android.wearable.watchface.R.string;

public class OcodoWatchFaceVitsoe extends CanvasWatchFaceService {

    private static final String TAG = "OcodoWatchFaceVitsoe";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            ResultCallback<DailyTotalResult> {

        private static final float HOUR_HAND_THICKNESS = 22f;
        private static final float MINUTE_HAND_THICKNESS = 14f;
        private static final float MINUTE_TICKS_THICKNESS = 4f;
        private static final float HOUR_TICKS_THICKNESS = 10f;
        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 0f;
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
        private float secondHandLengthPercent = 0.87f;
        private float secondHandCenterOffsetPercent = -0.23f;
        private float minuteHandLengthPercent = 0.84f;
        private float hourHandLengthPercent = 0.63f;
        private int mWidth;
        private int mHeight;
        private int LIGHT_BG = getResources().getColor(R.color.light_bg);
        private int DARK_HANDS = getResources().getColor(R.color.bg);
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        private int mWatchHandColor;
        private Typeface normalTypeface;
        private Typeface lightTypeface;

        private boolean mRegisteredReceiver = false;
        private Paint detailTextPaint;
        private Paint mQuartzTextPaint;
        private Paint mHourTextPaint;
        private Paint mMinuteTextPaint;
        private Paint mHourHandPaint;
        private Paint mMinuteHandPaint;
        private Paint mSecondHandPaint;
        private Paint mSecondHandTipPaint;
        private Paint mMinuteTickPaint;
        private Paint mHourTickPaint;
        private Calendar mCalendar;
        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private GoogleApiClient mGoogleApiClient;
        private boolean mStepsRequested;
        private Paint mStepCountPaint;
        private int mStepsTotal = 0;
        private Paint mCenterCirclePaint;

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }

            super.onCreate(holder);

            mCenterCirclePaint = new Paint();
            mCenterCirclePaint.setStrokeWidth(0);
            mCenterCirclePaint.setAntiAlias(true);
            mCenterCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mCenterCirclePaint.setColor(DARK_HANDS);

            mWatchHandColor = DARK_HANDS;

            mHourHandPaint = createStrokePaint(DARK_HANDS, HOUR_HAND_THICKNESS, Paint.Style.STROKE);
            mHourHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mMinuteHandPaint = createStrokePaint(DARK_HANDS, MINUTE_HAND_THICKNESS, Paint.Style.STROKE);
            mMinuteHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondHandPaint = createStrokePaint(DARK_HANDS, 6f, Paint.Style.STROKE);
            mSecondHandPaint.setStrokeCap(Paint.Cap.BUTT);

            mSecondHandTipPaint = createStrokePaint(Color.RED, 6f, Paint.Style.STROKE);
            mSecondHandTipPaint.setStrokeCap(Paint.Cap.BUTT);

            mMinuteTickPaint = createStrokePaint(DARK_HANDS, MINUTE_TICKS_THICKNESS, Paint.Style.STROKE);
            mHourTickPaint = createStrokePaint(DARK_HANDS, HOUR_TICKS_THICKNESS, Paint.Style.STROKE);

            lightTypeface = Typeface.createFromAsset(getAssets(), "gothamrnd-light.ttf");
            normalTypeface = Typeface.createFromAsset(getAssets(), "Futura-Medium.ttf");

            mStepsRequested = false;
            mGoogleApiClient = new GoogleApiClient.Builder(OcodoWatchFaceVitsoe.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Fitness.HISTORY_API)
                    .addApi(Fitness.RECORDING_API)
                    .useDefaultAccount()
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(OcodoWatchFaceVitsoe.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            detailTextPaint = createTextPaint(DARK_HANDS, lightTypeface);
            detailTextPaint.setTextAlign(Paint.Align.CENTER);
            mStepCountPaint = createTextPaint(DARK_HANDS, lightTypeface);
            mStepCountPaint.setTextSize(18);
            mStepCountPaint.setTextAlign(Paint.Align.CENTER);
            mHourTextPaint = createTextPaint(DARK_HANDS, normalTypeface);
            mHourTextPaint.setTextSize(28);
            mHourTextPaint.setTextAlign(Paint.Align.CENTER);
            mMinuteTextPaint = createTextPaint(DARK_HANDS, normalTypeface);
            mMinuteTextPaint.setTextSize(12);
            mMinuteTextPaint.setTextAlign(Paint.Align.CENTER);
            mQuartzTextPaint = createTextPaint(DARK_HANDS, normalTypeface);
            mQuartzTextPaint.setTextSize(8);
            mQuartzTextPaint.setTextAlign(Paint.Align.CENTER);

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
            detailTextPaint.setTextSize(16);
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

            for (int tickIndex = 0; tickIndex < 60; tickIndex++)
                drawRepeatingTicks(canvas, mCenterX * 0.82f, mCenterX * 0.87f, tickIndex, 60, mMinuteTickPaint, true, mCenterX, mCenterY);


            for (int tickIndex = 0; tickIndex < 12; tickIndex++)
                drawRepeatingTicks(canvas, mCenterX * 0.77f, mCenterX * 0.87f, tickIndex, 12, mHourTickPaint, true, mCenterX, mCenterY);

            drawRepeatingTextDigits(canvas, new String[]{"12", "3", "6", "9"},
                    mHourTextPaint, mCenterX, mCenterY, 0.38f, 4, mCenterX * 0.68f);

            List<String> minutesList = new ArrayList<>();
            for (int i = 0;
                 i < 12;
                 i++) minutesList.add(Integer.toString(i * 5));

            drawRepeatingTextDigits(canvas, minutesList.toArray(new String[]{}), mMinuteTextPaint,
                    mCenterX, mCenterY, 0.40f, 12, mCenterX * 0.93f);

            drawWatchName(canvas, getString(string.ocodo_vitsoe_logo_text), mCenterY * 0.52f,
                    mWidth, mMinuteTextPaint);

            drawWatchName(canvas, getString(string.ocodo_vitsoe_quartz_text), mCenterY * 0.57f,
                    mWidth, mQuartzTextPaint);

            drawDayMonthDate(canvas, mStepCountPaint, mWidth * 0.5f, mHeight * 0.70f, "dd MMM");

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            final float minutesRotation = getMinuteRotation();
            final float hoursRotation = getHourRotation();

            if (!isInAmbientMode()) drawStepsCount(canvas, mStepsTotal, "###,###", "%s ST",
                    mWidth / 2, mHeight * 0.75f, detailTextPaint);

            updateWatchHandStyle();

            canvas.save();


            // Draw Hours
            drawClockHand(canvas, hoursRotation, mCenterX,
                    mCenterY,
                    mCenterY + CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterY - sHourHandLength,
                    mHourHandPaint);

            // Draw Minutes
            drawClockHand(canvas, minutesRotation - hoursRotation, mCenterX,
                    mCenterY,
                    mCenterY + CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterY - sMinuteHandLength,
                    mMinuteHandPaint);

            Paint outerCenterCirclePaint = new Paint();
            outerCenterCirclePaint.setColor(LIGHT_BG);
            outerCenterCirclePaint.setAntiAlias(true);

            canvas.drawCircle(mCenterX, mCenterY, 28, outerCenterCirclePaint);

            canvas.drawCircle(mCenterX, mCenterY, 22, mCenterCirclePaint);

            if (!isInAmbientMode()) drawSweepingSecondHand(canvas, minutesRotation, mCalendar,
                    mCenterX, mCenterY, mWidth, secondHandCenterOffsetPercent,
                    mSecondHandLength, mSecondHandPaint);

            canvas.restore();
        }

        private float getHourRotation() {
            return (mCalendar.get(Calendar.HOUR) * 30) + mCalendar.get(Calendar.MINUTE) / 2f;
        }

        private float getMinuteRotation() {
            return mCalendar.get(Calendar.MINUTE) * 6f;
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
                ambientPaint(mSecondHandPaint, DARK_HANDS);
                ambientPaint(mHourTickPaint, DARK_HANDS);
                ambientPaint(mMinuteTickPaint, DARK_HANDS);
            } else {
                activePaint(mSecondHandPaint, DARK_HANDS);
                activePaint(mHourTickPaint, mWatchHandColor);
                activePaint(mMinuteHandPaint, mWatchHandColor);
                activePaint(mMinuteTickPaint, mWatchHandColor);
                activePaint(mHourHandPaint, mWatchHandColor);
            }
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            OcodoWatchFaceVitsoe.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            OcodoWatchFaceVitsoe.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onConnected(@Nullable Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "mGoogleApiAndFitCallbacks.onConnected: " + connectionHint);
            }
            mStepsRequested = false;
            subscribeToSteps(new ResultCallback<Status>() {
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

        private void subscribeToSteps(ResultCallback<Status> resultCallback) {
            Fitness.RecordingApi.subscribe(mGoogleApiClient, DataType.TYPE_STEP_COUNT_DELTA)
                    .setResultCallback(resultCallback);
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
