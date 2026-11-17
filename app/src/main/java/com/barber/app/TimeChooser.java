package com.barber.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * انتخاب ساعت و دقیقه با دو چرخ. دقیقه‌های مجاز طبق حالت نوبت‌دهی محدود می‌شود:
 * ساعتی = فقط :00، نیم‌ساعتی = :00 و :30، آزاد = هر دقیقه.
 */
public final class TimeChooser {

    public interface OnPicked {
        void onPicked(int minutesFromMidnight);
    }

    private TimeChooser() {
    }

    public static void show(Context ctx, int initialMinutes, int mode, final OnPicked cb) {
        final int[] allowed = Logic.allowedMinutes(mode);
        int h = Math.max(0, Math.min(23, initialMinutes / 60));
        int m = Logic.snapMinute(initialMinutes % 60, allowed);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // ساعت : دقیقه
        int pad = Ui.dp(ctx, 16);
        row.setPadding(pad, pad, pad, pad);

        final NumberPicker ph = new NumberPicker(ctx);
        ph.setMinValue(0);
        ph.setMaxValue(23);
        ph.setValue(h);
        ph.setWrapSelectorWheel(true);
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(java.util.Locale.US, "%02d", i);
        ph.setDisplayedValues(hours);

        TextView colon = new TextView(ctx);
        colon.setText(" : ");
        colon.setTextSize(24);

        final NumberPicker pm = new NumberPicker(ctx);
        String[] mins = new String[allowed.length];
        int sel = 0;
        for (int i = 0; i < allowed.length; i++) {
            mins[i] = String.format(java.util.Locale.US, "%02d", allowed[i]);
            if (allowed[i] == m) sel = i;
        }
        pm.setMinValue(0);
        pm.setMaxValue(allowed.length - 1);
        pm.setDisplayedValues(mins);
        pm.setValue(sel);
        pm.setWrapSelectorWheel(allowed.length > 2);

        row.addView(ph, new LinearLayout.LayoutParams(Ui.dp(ctx, 80), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(colon);
        row.addView(pm, new LinearLayout.LayoutParams(Ui.dp(ctx, 80), LinearLayout.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("انتخاب ساعت")
                .setView(row)
                .setPositiveButton("تأیید", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        cb.onPicked(ph.getValue() * 60 + allowed[pm.getValue()]);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }
}
