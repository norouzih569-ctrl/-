package com.barber.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

/** ارسال دستی پیامک با برنامه‌ی پیامک گوشی (بدون نیاز به مجوز؛ خود کاربر دکمه‌ی ارسال را می‌زند). */
public final class Messenger {

    private Messenger() {
    }

    public static void openSms(Activity act, String phone, String text) {
        String d = Logic.digitsOnly(phone);
        if (d.isEmpty()) {
            Ui.toastLong(act, "شماره‌ی تلفن مشتری ثبت نشده است");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + d));
            i.putExtra("sms_body", text);
            act.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Ui.toastLong(act, "برنامه‌ی پیامک روی گوشی پیدا نشد");
        }
    }
}
