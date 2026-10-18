package com.barber.admin;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** انتخاب تاریخ شمسی با سه چرخ (روز، ماه، سال). */
public final class JalaliPicker {

    public interface OnPicked {
        void onPicked(int year, int month, int day);
    }

    private static final int MIN_YEAR = 1380;
    private static final int MAX_YEAR = 1460;

    private JalaliPicker() {
    }

    public static void show(Context ctx, int[] initial, final OnPicked callback) {
        int iy = Math.max(MIN_YEAR, Math.min(MAX_YEAR, initial[0]));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);

        final NumberPicker pd = new NumberPicker(ctx);
        final NumberPicker pm = new NumberPicker(ctx);
        final NumberPicker py = new NumberPicker(ctx);

        py.setMinValue(MIN_YEAR);
        py.setMaxValue(MAX_YEAR);
        py.setValue(iy);
        py.setWrapSelectorWheel(false);

        pm.setMinValue(1);
        pm.setMaxValue(12);
        pm.setDisplayedValues(JalaliCalendar.MONTH_NAMES);
        pm.setValue(initial[1]);
        pm.setWrapSelectorWheel(true);

        pd.setMinValue(1);
        pd.setMaxValue(JalaliCalendar.monthLength(iy, initial[1]));
        pd.setValue(Math.min(initial[2], pd.getMaxValue()));
        pd.setWrapSelectorWheel(true);

        NumberPicker.OnValueChangeListener updateDays = new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                int len = JalaliCalendar.monthLength(py.getValue(), pm.getValue());
                int cur = pd.getValue();
                pd.setMaxValue(len);
                if (cur > len) pd.setValue(len);
            }
        };
        py.setOnValueChangedListener(updateDays);
        pm.setOnValueChangedListener(updateDays);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(pd, lp);
        row.addView(pm, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f));
        row.addView(py, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f));

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("انتخاب تاریخ")
                .setView(row)
                .setPositiveButton("تأیید", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        callback.onPicked(py.getValue(), pm.getValue(), pd.getValue());
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }
}
