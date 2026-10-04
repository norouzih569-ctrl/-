package com.barber.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** ثابت‌ها و تنظیمات برنامه (معادل بخش «ثابت‌های برنامه» در نسخه ویندوز). */
public final class AppConfig {

    public static final String CURRENCY = "تومان";
    public static final String SUPPORT_PHONE = "09359759424";

    /**
     * تاریخ انقضای نسخه (میلادی، yyyy-MM-dd) مثل نسخه ویندوز.
     * برای غیرفعال کردن محدودیت، مقدار را خالی ("") بگذارید.
     */
    public static final String BUILD_EXPIRY = "2027-01-21";

    public static final String[] SERVICES = {
            "اصلاح", "اصلاح کودک", "اصلاح داماد", "رنگ", "ریش", "سشوار", "عمومی"
    };

    private static final long[] DEFAULT_PRICES = {
            250000L, 150000L, 350000L, 500000L, 200000L, 100000L, 0L
    };

    /** ساعت‌های نوبت: ۰۹:۰۰ تا ۲۰:۰۰ (مثل نسخه ویندوز) */
    public static final String[] TIME_SLOTS = buildSlots();

    private static final String PREFS = "service_prices";
    private static final String PREFS_SMS = "sms_settings";

    public static final String DEFAULT_SMS_TEMPLATE =
            "سلام {name} عزیز 🌹\n"
                    + "یادآوری نوبت شما در {salon}: {date} ساعت {time} ({service}).\n"
                    + "منتظر شما هستیم.";

    private AppConfig() {
    }

    private static String[] buildSlots() {
        String[] s = new String[12];
        for (int h = 9; h <= 20; h++) {
            s[h - 9] = String.format(Locale.US, "%02d:00", h);
        }
        return s;
    }

    public static int serviceIndex(String service) {
        for (int i = 0; i < SERVICES.length; i++) {
            if (SERVICES[i].equals(service)) return i;
        }
        return -1;
    }

    // ---------------- پیامک یادآوری ----------------

    public static String getSalonName(Context ctx) {
        return ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE).getString("salon_name", "آرایشگاه");
    }

    public static String getSmsTemplate(Context ctx) {
        return ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE)
                .getString("template", DEFAULT_SMS_TEMPLATE);
    }

    /** چند ساعت قبل از نوبت، پیامک یادآوری برای مشتری ارسال شود */
    public static int getSmsLeadHours(Context ctx) {
        return ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE).getInt("lead_hours", 3);
    }

    /** آخرین انتخاب تیک «پیامک یادآوری» برای نوبت جدید (پیش‌فرض: خاموش) */
    public static boolean getSmsDefault(Context ctx) {
        return ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE).getBoolean("default_on", false);
    }

    public static void setSmsDefault(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE).edit().putBoolean("default_on", on).apply();
    }

    public static void setSmsSettings(Context ctx, String salon, String template, int leadHours) {
        ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE).edit()
                .putString("salon_name", salon).putString("template", template)
                .putInt("lead_hours", leadHours).apply();
    }

    // ---------------- باشگاه مشتریان ----------------

    /** هر چندمین مراجعه جایزه دارد؛ ۰ = غیرفعال */
    public static int getLoyaltyThreshold(Context ctx) {
        return ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE).getInt("loyalty_n", 10);
    }

    public static void setLoyaltyThreshold(Context ctx, int n) {
        ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE).edit().putInt("loyalty_n", n).apply();
    }

    public static long getPrice(Context ctx, String service) {
        int idx = serviceIndex(service);
        long def = idx >= 0 ? DEFAULT_PRICES[idx] : 0L;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getLong(service, def);
    }

    public static void setPrice(Context ctx, String service, long price) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(service, price).apply();
    }
}
