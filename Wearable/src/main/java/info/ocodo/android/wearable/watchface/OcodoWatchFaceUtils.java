package info.ocodo.android.wearable.watchface;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static java.lang.String.format;

public class OcodoWatchFaceUtils {

    static void drawWatchName(Canvas canvas, String name, float textYOffset, int screenWidth, Paint paint) {
        canvas.drawText(name, screenWidth / 2, textYOffset, paint);
    }

    static void drawDayMonthDate(Canvas canvas, Paint paint, float x, float y, String simpleDateFormatPattern) {
        @SuppressLint("SimpleDateFormat")
        String date = new SimpleDateFormat(simpleDateFormatPattern).format(new Date()).toUpperCase();
        canvas.drawText(date, x, y, paint);
    }

    static void drawStepsCount(Canvas canvas, int stepsTotal,
                               String numberFormat, String stringFormat, int x, float y, Paint paint) {
        DecimalFormat df = new DecimalFormat(numberFormat);
        String formattedStepString = format(stringFormat, df.format(stepsTotal));
        canvas.drawText(formattedStepString, x, y, paint);
    }

    static void drawTickingSecondHand(Canvas canvas, float minutesRotation, Calendar calendar, float centerX, float centerY, int width,
                                      float centerOffsetPercent, float handLength, Paint handPaint) {
        final float secondsRotation = calendar.get(Calendar.SECOND) * 6f;

        drawClockHand(canvas, secondsRotation - minutesRotation, centerX,
                centerY,
                centerY - ((width / 2) * centerOffsetPercent),
                centerY - handLength,
                handPaint);
    }

    static void drawSweepingSecondHand(Canvas canvas, float minutesRotation, Calendar calendar, float centerX, float centerY, int width,
                                       float centerOffsetPercent, float handLength, Paint handPaint) {
        final float milliseconds = ((calendar.get(Calendar.SECOND) * 1000) +
                calendar.get(Calendar.MILLISECOND));
        final float secondsRotation = milliseconds * 0.006f;

        drawClockHand(canvas, secondsRotation - minutesRotation, centerX,
                centerY,
                centerY - ((width / 2) * centerOffsetPercent),
                centerY - handLength,
                handPaint);
    }

    static String formatTwoDigitNumber(int hour) {
        return format("%02d", hour);
    }

    static void drawClockHand(Canvas canvas, float hoursRotation, float mCenterX, float mCenterY,
                              float startY, float stopY, Paint mHourHandPaint) {
        canvas.rotate(hoursRotation, mCenterX, mCenterY);
        canvas.drawLine(
                mCenterX,
                startY,
                mCenterX,
                stopY,
                mHourHandPaint);
    }

    static void drawRepeatingTicks(Canvas canvas, float innerRadius, float outerRadius,
                                   int index, int count, Paint paint, boolean predicate, float centerX, float centerY) {
        float tickRot = (float) (index * Math.PI * 2 / count);
        float innerX = (float) Math.sin(tickRot) * innerRadius;
        float innerY = (float) -Math.cos(tickRot) * innerRadius;
        float outerX = (float) Math.sin(tickRot) * outerRadius;
        float outerY = (float) -Math.cos(tickRot) * outerRadius;
        if (predicate) {
            canvas.drawLine(
                    centerX + innerX,
                    centerY + innerY,
                    centerX + outerX,
                    centerY + outerY,
                    paint
            );
        }
    }

    static void drawRepeatingTextDigits(Canvas canvas, String[] textItems, Paint textPaint, float x, float y, float yAdjust, int count, float offset) {
        for (int digitIndex = 0; digitIndex < count; digitIndex++) {
            drawRepeatingText(textItems[digitIndex], canvas,
                    offset,
                    digitIndex, count,
                    textPaint, true, x, y, yAdjust);
        }
    }

    static void drawRepeatingText(String text, Canvas canvas, float offset, int index,
                                  int count, Paint paint, boolean predicate, float centerX, float centerY, float yAdjust) {
        float tickRot = (float) (index * Math.PI * 2 / count);
        float innerX = (float) Math.sin(tickRot) * offset;
        float innerY = (float) -Math.cos(tickRot) * offset;
        float x = (centerX + innerX);
        float y = (centerY + innerY) - (paint.ascent() * yAdjust);
        if (predicate) canvas.drawText(text, x, y, paint);
    }

    static Paint createTextPaint(int color, Typeface typeface) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setTypeface(typeface);
        paint.setAntiAlias(true);
        return paint;
    }

    static Paint createStrokePaint(int color, float strokeWidth, Paint.Style style,
                                   int shadowRadius, float dx, float dy, int shadowColor) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setAntiAlias(true);
        paint.setStyle(style);
        paint.setShadowLayer(shadowRadius, dx, dy, shadowColor);
        return paint;
    }

    static Paint createStrokePaint(int color, float strokeWidth, Paint.Style style) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setAntiAlias(true);
        paint.setStyle(style);
        return paint;
    }

    static void ambientPaint(Paint p, int color) {
        p.setColor(color);
        p.setAntiAlias(false);
        p.clearShadowLayer();
    }

    static void activePaint(Paint p, int color, int shadowRadius, float shadowX, float shadowY, int shadowColor) {
        p.setColor(color);
        p.setAntiAlias(true);
        p.setShadowLayer(shadowRadius, shadowX, shadowY, shadowColor);
    }

    static void activePaint(Paint p, int color) {
        p.setColor(color);
        p.setAntiAlias(true);
    }
}
