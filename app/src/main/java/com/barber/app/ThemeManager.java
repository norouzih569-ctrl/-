package com.barber.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** مدیریت تم‌های برنامه: ذخیره انتخاب، اعمال تم و پنجره انتخاب تم با پیش‌نمایش. */
public final class ThemeManager {

    /** حالت خودکار: بر اساس تنظیم روشن/تیره‌ی گوشی */
    public static final int AUTO = -1;

    private static final String PREFS = "app_theme";
    private static final String KEY = "theme_index";

    private static final int DEFAULT_LIGHT = 0;
    private static final int DEFAULT_DARK = 7;

    public static final String[] NAMES = {
            "سفید مدرن",
            "تخت (فیروزه‌ای)",
            "تیره کلاسیک",
            "تیره آبی‌نارنجی",
            "نقره‌ای مشکی",
            "بنفش نئونی",
            "کهربایی",
            "طلایی آرایشگاه"
    };

    private static final int[] STYLES = {
            R.style.Theme_Barber_T0, R.style.Theme_Barber_T1, R.style.Theme_Barber_T2,
            R.style.Theme_Barber_T3, R.style.Theme_Barber_T4, R.style.Theme_Barber_T5,
            R.style.Theme_Barber_T6, R.style.Theme_Barber_T7
    };

    private ThemeManager() {
    }

    /** انتخاب ذخیره‌شده (AUTO یا شماره تم) */
    public static int getSaved(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int v = sp.getInt(KEY, AUTO);
        return (v >= 0 && v < STYLES.length) ? v : AUTO;
    }

    public static void save(Context ctx, int choice) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, choice).apply();
    }

    /** شماره‌ی تمی که واقعاً باید نمایش داده شود */
    public static int resolve(Context ctx) {
        int saved = getSaved(ctx);
        if (saved != AUTO) return saved;
        int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? DEFAULT_DARK : DEFAULT_LIGHT;
    }

    /** باید قبل از super.onCreate و setContentView صدا زده شود. */
    public static void apply(Activity activity) {
        activity.setTheme(STYLES[resolve(activity)]);
    }

    // ---------------- پنجره انتخاب تم ----------------

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    private static int attrColor(Context themed, int attr) {
        TypedValue tv = new TypedValue();
        themed.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    /** پیش‌نمایش کوچک هر تم: نوار بالا، پس‌زمینه، کارت و رنگ تأکیدی */
    private static class Preview extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final int toolbar;
        private final int bg;
        private final int surface;
        private final int accent;

        Preview(Context ctx, int styleRes) {
            super(ctx);
            Context t = new ContextThemeWrapper(ctx, styleRes);
            toolbar = attrColor(t, R.attr.barberToolbar);
            surface = attrColor(t, R.attr.barberSurface);
            accent = attrColor(t, R.attr.barberAccent);
            TypedArray a = t.obtainStyledAttributes(new int[]{android.R.attr.colorBackground});
            bg = a.getColor(0, 0xFFFFFFFF);
            a.recycle();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float rad = h * 0.18f;
            // پس‌زمینه
            p.setColor(bg);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, p);
            // نوار بالا
            c.save();
            c.clipRect(0, 0, w, h * 0.30f);
            p.setColor(toolbar);
            c.drawRoundRect(r, rad, rad, p);
            c.restore();
            // کارت‌ها
            p.setColor(surface);
            r.set(w * 0.08f, h * 0.40f, w * 0.92f, h * 0.62f);
            c.drawRoundRect(r, rad / 2, rad / 2, p);
            r.set(w * 0.08f, h * 0.68f, w * 0.92f, h * 0.90f);
            c.drawRoundRect(r, rad / 2, rad / 2, p);
            // رنگ تأکیدی
            p.setColor(accent);
            c.drawCircle(w * 0.80f, h * 0.51f, h * 0.06f, p);
            r.set(w * 0.16f, h * 0.47f, w * 0.48f, h * 0.55f);
            c.drawRoundRect(r, 6, 6, p);
            c.drawCircle(w * 0.80f, h * 0.79f, h * 0.06f, p);
            // خط ظریف دور پیش‌نمایش
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(0x33808080);
            r.set(1, 1, w - 1, h - 1);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    public interface OnChanged {
        void onChanged();
    }

    /** نمایش پنجره انتخاب تم؛ بعد از انتخاب، callback صدا زده می‌شود (برای recreate). */
    public static void showPicker(final Activity act, final OnChanged callback) {
        final int saved = getSaved(act);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8));

        final AlertDialog[] holder = new AlertDialog[1];

        // ردیف «خودکار»
        box.addView(buildRow(act, "خودکار (طبق روشن/تیره‌ی گوشی)", null, saved == AUTO,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        choose(act, AUTO, saved, holder[0], callback);
                    }
                }));

        for (int i = 0; i < NAMES.length; i++) {
            final int idx = i;
            box.addView(buildRow(act, NAMES[i], new Preview(act, STYLES[i]), saved == i,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            choose(act, idx, saved, holder[0], callback);
                        }
                    }));
        }

        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);

        holder[0] = new MaterialAlertDialogBuilder(act)
                .setTitle("انتخاب تم")
                .setView(scroll)
                .setNegativeButton("بستن", null)
                .show();
    }

    private static void choose(Activity act, int choice, int saved, AlertDialog dialog, OnChanged cb) {
        if (dialog != null) dialog.dismiss();
        if (choice == saved) return;
        save(act, choice);
        cb.onChanged();
    }

    private static View buildRow(Activity act, String title, View preview, boolean selected,
                                 View.OnClickListener click) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(act, 8), dp(act, 8), dp(act, 8), dp(act, 8));
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue tv = new TypedValue();
        act.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setOnClickListener(click);

        LinearLayout.LayoutParams pv = new LinearLayout.LayoutParams(dp(act, 64), dp(act, 44));
        if (preview != null) {
            row.addView(preview, pv);
        } else {
            TextView icon = new TextView(act);
            icon.setText("◐");
            icon.setTextSize(28);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, pv);
        }

        TextView name = new TextView(act);
        name.setText(title);
        name.setTextSize(16);
        name.setPadding(dp(act, 14), 0, dp(act, 8), 0);
        if (selected) name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView check = new TextView(act);
        check.setText(selected ? "✓" : "");
        check.setTextSize(20);
        check.setTypeface(check.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(check, new LinearLayout.LayoutParams(dp(act, 28),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }
}
