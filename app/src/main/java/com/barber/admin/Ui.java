package com.barber.admin;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.List;

/** توابع کمکی رابط کاربری. */
public final class Ui {

    private Ui() {
    }

    public static ArrayAdapter<String> spinnerAdapter(Context ctx, List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    public static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    public static void toastLong(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }

    public static int dp(Context ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** رنگ یک attr از تم فعلی (رنگ ساده یا ColorStateList) */
    public static int themeColor(Context ctx, int attr, int fallback) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (!ctx.getTheme().resolveAttribute(attr, tv, true)) return fallback;
        if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        if (tv.resourceId != 0) {
            try {
                return ctx.getResources().getColorStateList(tv.resourceId, ctx.getTheme()).getDefaultColor();
            } catch (Exception e) {
                return fallback;
            }
        }
        return fallback;
    }

    /** پس‌زمینه‌ی گرد با رنگ نیمه‌شفاف (برای چیپ ساعت/تاریخ و آواتار) */
    public static android.graphics.drawable.GradientDrawable tinted(Context ctx, int color, int alpha255, float radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor((color & 0x00FFFFFF) | (alpha255 << 24));
        g.setCornerRadius(dp(ctx, radiusDp));
        return g;
    }

    public static void rtl(View v) {
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }
}
