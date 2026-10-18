package com.barber.admin;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** تماس، پیامک و واتساپ از طریق برنامه‌های خود گوشی (بدون نیاز به مجوز). */
public final class Contact {

    private Contact() {
    }

    private static void start(Context ctx, Intent i) {
        try {
            ctx.startActivity(i);
        } catch (Exception e) {
            Ui.toast(ctx, "برنامه‌ی مناسبی روی گوشی پیدا نشد");
        }
    }

    public static void dial(Context ctx, String number) {
        String n = Validate.digits(number);
        if (n.isEmpty()) return;
        start(ctx, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + n)));
    }

    public static void sms(Context ctx, String number, String body) {
        String n = Validate.digits(number);
        if (n.isEmpty()) return;
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + n));
        i.putExtra("sms_body", body == null ? "" : body);
        start(ctx, i);
    }

    public static void whatsapp(Context ctx, String number, String body) {
        String n = Validate.whatsappNumber(number);
        if (n.isEmpty()) {
            Ui.toast(ctx, "شماره‌ی موبایل معتبر نیست");
            return;
        }
        String url = "https://wa.me/" + n;
        if (body != null && !body.isEmpty()) url += "?text=" + Uri.encode(body);
        start(ctx, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
