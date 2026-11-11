package com.barber.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.os.Build;

/** ذخیره و بررسی رمز قفل برنامه (هیچ‌وقت خود رمز ذخیره نمی‌شود؛ فقط هش با نمک). */
public final class LockManager {

    private static final String PREFS = "app_lock";

    /** گزینه‌های «قفل خودکار بعد از»: ثانیه */
    public static final int[] TIMEOUTS = {0, 30, 60, 300};
    public static final String[] TIMEOUT_LABELS = {"بلافاصله", "۳۰ ثانیه", "۱ دقیقه", "۵ دقیقه"};

    private LockManager() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context ctx) {
        SharedPreferences p = sp(ctx);
        return p.getBoolean("enabled", false) && p.getString("hash", null) != null;
    }

    /** تنظیم (یا تغییر) رمز؛ قفل را فعال می‌کند و شمارنده‌ی تلاش‌های ناموفق را صفر می‌کند. */
    public static void setPin(Context ctx, String pin) {
        String salt = Logic.newSalt();
        sp(ctx).edit()
                .putString("salt", salt)
                .putString("hash", Logic.hashPin(salt, pin))
                .putInt("len", pin.length())
                .putBoolean("enabled", true)
                .putInt("failures", 0)
                .putLong("lock_until", 0)
                .apply();
    }

    /** غیرفعال کردن کامل قفل (رمز، اثر انگشت و تنظیمات پاک می‌شود) */
    public static void disable(Context ctx) {
        sp(ctx).edit().clear().apply();
    }

    public static boolean checkPin(Context ctx, String pin) {
        SharedPreferences p = sp(ctx);
        return Logic.verifyPin(p.getString("salt", null), pin, p.getString("hash", null));
    }

    public static int getPinLength(Context ctx) {
        return sp(ctx).getInt("len", 4);
    }

    // ---------------- تلاش‌های ناموفق ----------------

    /** ثبت یک رمز اشتباه؛ تعداد ثانیه‌ی قفل را برمی‌گرداند (۰ = بدون قفل). */
    public static int recordFailure(Context ctx) {
        SharedPreferences p = sp(ctx);
        int f = p.getInt("failures", 0) + 1;
        int secs = Logic.lockoutSeconds(f);
        p.edit().putInt("failures", f)
                .putLong("lock_until", secs > 0 ? System.currentTimeMillis() + secs * 1000L : 0)
                .apply();
        return secs;
    }

    public static int getFailures(Context ctx) {
        return sp(ctx).getInt("failures", 0);
    }

    public static void resetFailures(Context ctx) {
        sp(ctx).edit().putInt("failures", 0).putLong("lock_until", 0).apply();
    }

    /** ثانیه‌های باقی‌مانده از قفل موقت (۰ = آزاد) */
    public static int lockRemainingSeconds(Context ctx) {
        long left = sp(ctx).getLong("lock_until", 0) - System.currentTimeMillis();
        return left > 0 ? (int) ((left + 999) / 1000) : 0;
    }

    // ---------------- زمان قفل خودکار ----------------

    public static int getTimeoutSeconds(Context ctx) {
        return sp(ctx).getInt("timeout", 30);
    }

    public static void setTimeoutSeconds(Context ctx, int s) {
        sp(ctx).edit().putInt("timeout", s).apply();
    }

    public static String timeoutLabel(int seconds) {
        for (int i = 0; i < TIMEOUTS.length; i++) {
            if (TIMEOUTS[i] == seconds) return TIMEOUT_LABELS[i];
        }
        return seconds + " ثانیه";
    }

    // ---------------- اثر انگشت / چهره ----------------

    /** آیا گوشی اثر انگشت یا چهره‌ی ثبت‌شده دارد؟ (اندروید ۱۰ به بالا) */
    public static boolean biometricAvailable(Context ctx) {
        if (Build.VERSION.SDK_INT < 29) return false;
        try {
            BiometricManager bm = ctx.getSystemService(BiometricManager.class);
            if (bm == null) return false;
            int r = Build.VERSION.SDK_INT >= 30
                    ? bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    : bm.canAuthenticate();
            return r == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isBiometricEnabled(Context ctx) {
        return sp(ctx).getBoolean("biometric", false) && biometricAvailable(ctx);
    }

    public static void setBiometricEnabled(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean("biometric", on).apply();
    }
}
