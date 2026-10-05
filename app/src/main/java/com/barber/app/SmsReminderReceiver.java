package com.barber.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

import java.util.ArrayList;

/**
 * ارسال پیامک یادآوری به مشتری. فقط برای نوبت‌هایی اجرا می‌شود که صاحب آرایشگاه
 * تیک «پیامک یادآوری» را برایشان زده باشد؛ در غیر این صورت هیچ پیامکی ارسال نمی‌شود.
 */
public class SmsReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_ID = "id";

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, -1);
        if (id < 0) return;

        Db.Appointment a;
        try {
            a = Db.get(context).getAppointmentById(id);
        } catch (Exception e) {
            return;
        }
        // نوبت حذف شده، یا پیامک برایش انتخاب نشده، یا قبلاً ارسال شده: هیچ کاری نکن
        if (a == null || !a.smsReminder || a.smsSent) return;

        long apptMs = ReminderScheduler.toMillis(a.date, a.time);
        if (apptMs > 0 && apptMs < System.currentTimeMillis()) return; // نوبت گذشته

        if (!Logic.isUsablePhone(a.phone)) {
            notify(context, id, "⚠️ پیامک ارسال نشد",
                    "برای مشتری «" + a.customerName + "» شماره‌ی معتبری ثبت نشده است.");
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            notify(context, id, "⚠️ پیامک ارسال نشد",
                    "مجوز ارسال پیامک به برنامه داده نشده است (تنظیمات گوشی ← برنامه‌ها).");
            return;
        }

        String date = JalaliCalendar.DAY_NAMES[dayIndex(a.date)] + " "
                + JalaliCalendar.gregorianStringToJalali(a.date);
        String text = Logic.fillTemplate(AppConfig.getSmsTemplate(context), a.customerName, date,
                a.time, a.service, AppConfig.getSalonName(context));

        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 31
                    ? context.getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(text);
            sm.sendMultipartTextMessage(Logic.digitsOnly(a.phone), null, parts, null, null);
            Db.get(context).markSmsSent(id);
            notify(context, id, "📩 پیامک یادآوری ارسال شد",
                    "برای " + a.customerName + " (نوبت ساعت " + a.time + ")");
        } catch (Exception e) {
            notify(context, id, "⚠️ پیامک ارسال نشد",
                    "ارسال پیامک برای " + a.customerName + " با خطا مواجه شد.");
        }
    }

    private static int dayIndex(String iso) {
        try {
            int[] j = JalaliCalendar.jalaliOfIso(iso);
            return JalaliCalendar.dayOfWeek(j[0], j[1], j[2]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void notify(Context context, long id, String title, String text) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    ReminderReceiver.CHANNEL_ID, "یادآور نوبت‌ها", NotificationManager.IMPORTANCE_HIGH));
        }
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, ReminderReceiver.CHANNEL_ID)
                : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify((int) (id % 1000000L) + 2000000, b.build());
    }
}
