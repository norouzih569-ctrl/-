package com.barber.app;

import android.Manifest;
import android.content.Context;


/** نسخه‌ی «direct» (فروش مستقیم با کد): لایسنس فروشگاهی ندارد؛ همه‌چیز بی‌اثر است. */
public final class StoreLicense {

    private StoreLicense() {
    }

    public static void onAppStart(Context ctx) {
    }

    public static LicenseLogic.State state(Context ctx, long today) {
        return null;
    }

    public static void refresh(Context ctx, Runnable onChanged) {
    }

    public static int type(Context ctx) {
        return 0;
    }

    public static long start(Context ctx) {
        return 0;
    }

    public static void showDialog(MainActivity act, Runnable onChanged) {
    }

    // ---- مخاطبین: در نسخه‌ی مستقیم دسترسی خواندن مخاطبین هست (نسخه‌ی بازار ندارد) ----

    public static String contactsPermission() {
        return Manifest.permission.READ_CONTACTS;
    }

}
