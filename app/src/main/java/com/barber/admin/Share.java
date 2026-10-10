package com.barber.admin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

/** کپی در کلیپ‌بورد و اشتراک‌گذاری متن. */
public final class Share {

    private Share() {
    }

    public static void copy(Context ctx, String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Ui.toast(ctx, "کپی شد");
        } catch (Exception e) {
            Ui.toast(ctx, "کپی نشد");
        }
    }

    public static void text(Context ctx, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        try {
            ctx.startActivity(Intent.createChooser(i, "ارسال"));
        } catch (Exception e) {
            Ui.toast(ctx, "برنامه‌ای برای ارسال پیدا نشد");
        }
    }

    private static android.net.Uri uriFor(Context ctx, java.io.File f) {
        return androidx.core.content.FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
    }

    /** ارسال فایل (مثلاً PDF) با برنامه‌های گوشی */
    public static void file(Context ctx, java.io.File f, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, "ارسال فایل"));
        } catch (Exception e) {
            Ui.toast(ctx, "ارسال انجام نشد");
        }
    }

    /** باز کردن فایل با برنامه‌ی نمایش‌دهنده */
    public static void open(Context ctx, java.io.File f, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uriFor(ctx, f), mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(i);
        } catch (Exception e) {
            Ui.toast(ctx, "برنامه‌ای برای باز کردن PDF پیدا نشد؛ از «ارسال» استفاده کنید");
        }
    }
}
