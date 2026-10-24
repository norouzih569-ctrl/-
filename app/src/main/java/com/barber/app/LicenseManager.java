package com.barber.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/** لایه‌ی اندروید لایسنس: شناسه‌ی دستگاه، ذخیره در SharedPreferences و تاریخ شمسی. منطق اصلی در LicenseCore است. */
public final class LicenseManager {

    private static final String PREFS = "license";

    private LicenseManager() {
    }

    private static final class PrefsStore implements LicenseCore.Store {
        private final SharedPreferences sp;

        PrefsStore(Context ctx) {
            sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        @Override
        public long getLong(String key, long def) {
            return sp.getLong(key, def);
        }

        @Override
        public void putLong(String key, long value) {
            sp.edit().putLong(key, value).commit();
        }

        @Override
        public String getString(String key, String def) {
            return sp.getString(key, def);
        }

        @Override
        public void putString(String key, String value) {
            sp.edit().putString(key, value).commit();
        }
    }

    // ---------------- شناسه‌ی دستگاه ----------------

    /** شناسه‌ی کوتاه و پایدار گوشی (مثل 7K2QX-9TH5R) که مشتری برای دریافت کد به شما می‌دهد. */
    public static String deviceId(Context ctx) {
        String seed = null;
        try {
            seed = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {
            // به شناسه‌ی جایگزین می‌رویم
        }
        // مقدار معروفِ خراب برخی گوشی‌های قدیمی/شبیه‌ساز
        if (seed == null || seed.isEmpty() || "9774d56d682e549c".equals(seed)) {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            seed = sp.getString("fallback_seed", null);
            if (seed == null) {
                seed = UUID.randomUUID().toString();
                sp.edit().putString("fallback_seed", seed).commit();
            }
        }
        return LicenseCodec.deviceIdFromSeed(seed + "|" + ctx.getPackageName());
    }

    // ---------------- دسترسی به هسته ----------------

    private static LicenseCore core(Context ctx) {
        Context app = ctx.getApplicationContext();
        return new LicenseCore(new PrefsStore(app), LicenseKeys.SECRET, deviceId(app));
    }

    /** شماره‌ی روز امروز (با منطقه‌ی زمانی گوشی) */
    public static long today() {
        long now = System.currentTimeMillis();
        return LicenseLogic.epochDay(now, TimeZone.getDefault().getOffset(now));
    }

    /** هر بار برنامه باز می‌شود صدا زده شود */
    public static void onAppStart(Context ctx) {
        core(ctx).onAppStart(today());
    }

    public static LicenseLogic.State state(Context ctx) {
        return core(ctx).state(today());
    }

    /** آیا برنامه الان فقط‌خواندنی است؟ (لایسنس/آزمایشی تمام شده یا ساعت دستکاری شده) */
    public static boolean isReadOnly(Context ctx) {
        return state(ctx).readOnly();
    }

    public static LicenseCore.Outcome activate(Context ctx, String code) {
        return core(ctx).activate(code, today(), System.currentTimeMillis());
    }

    public static long licStart(Context ctx) {
        return core(ctx).licStart();
    }

    public static long licEnd(Context ctx) {
        return core(ctx).licEnd();
    }

    public static long trialStart(Context ctx) {
        return core(ctx).trialStart();
    }

    public static int lastType(Context ctx) {
        return core(ctx).lastType();
    }

    public static List<LicenseCore.History> history(Context ctx) {
        return core(ctx).history();
    }

    public static int lockRemainingSeconds(Context ctx) {
        return core(ctx).lockRemainingSeconds(System.currentTimeMillis());
    }

    // ---------------- نمایش ----------------

    /** تاریخ شمسی (۱۴۰۵/۰۷/۱۰) از شماره‌ی روز */
    public static String dateText(long epochDay) {
        if (epochDay <= 0) return "—";
        int[] g = LicenseLogic.civilFromEpochDay(epochDay);
        int[] j = JalaliCalendar.toJalali(g[0], g[1], g[2]);
        return JalaliCalendar.format(j[0], j[1], j[2]);
    }

    public static String typeText(int type) {
        switch (type) {
            case LicenseCodec.TYPE_6M:
                return "لایسنس ۶ ماهه";
            case LicenseCodec.TYPE_5Y:
                return "لایسنس ۵ ساله";
            case LicenseCodec.TYPE_1Y:
                return "لایسنس ۱ ساله";
            case LicenseCodec.TYPE_2Y:
                return "لایسنس ۲ ساله";
            case LicenseCodec.TYPE_3Y:
                return "لایسنس ۳ ساله";
            case LicenseCodec.TYPE_TRANSFER:
                return "جابجایی دستگاه";
            case LicenseCodec.TYPE_TRIAL:
                return "دوره‌ی آزمایشی";
            default:
                return "—";
        }
    }
}
