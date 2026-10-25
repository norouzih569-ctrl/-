package com.barber.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** نمودار ستونی ساده‌ی درآمد روزانه؛ روز ۱ سمت راست (راست‌به‌چپ) */
public class BarChartView extends View {

    private double[] values = new double[0];
    private int barColor = 0xFF1F5FBF;
    private int textColor = 0xFF888888;
    private int bestColor = 0xFF16A085;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint();
    private final RectF rect = new RectF();

    public BarChartView(Context c) {
        super(c);
    }

    public BarChartView(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setData(double[] v, int barColor, int bestColor, int textColor) {
        this.values = v == null ? new double[0] : v;
        this.barColor = barColor;
        this.bestColor = bestColor;
        this.textColor = textColor;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int hh = (int) (getResources().getDisplayMetrics().density * 170);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), w), hh);
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = values.length;
        if (n == 0) return;
        float d = getResources().getDisplayMetrics().density;
        float padB = 20 * d, padT = 8 * d, padS = 4 * d;
        float w = getWidth() - 2 * padS, h = getHeight() - padB - padT;
        double max = 0;
        int best = ReportMath.maxIndex(values);
        for (double v : values) max = Math.max(max, v);

        line.setColor((textColor & 0x00FFFFFF) | 0x55000000);
        line.setStrokeWidth(1 * d);
        c.drawLine(padS, padT + h, padS + w, padT + h, line);

        text.setColor(textColor);
        text.setTextSize(10 * d);
        text.setTextAlign(Paint.Align.CENTER);

        float slot = w / n;
        float bw = Math.max(2 * d, slot * 0.68f);
        for (int i = 0; i < n; i++) {
            float cx = padS + w - slot * (i + 0.5f); // روز ۱ راست
            double v = values[i];
            if (max > 0 && v > 0) {
                float bh = (float) (v / max * (h - 14 * d));
                bar.setColor(i == best ? bestColor : barColor);
                rect.set(cx - bw / 2, padT + h - bh, cx + bw / 2, padT + h);
                c.drawRoundRect(rect, 2 * d, 2 * d, bar);
            }
            int day = i + 1;
            if (day == 1 || day % 5 == 0) {
                c.drawText(String.valueOf(day), cx, getHeight() - 5 * d, text);
            }
        }
    }
}
