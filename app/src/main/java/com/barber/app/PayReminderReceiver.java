package com.barber.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** اعلان یادآور پرداخت (اجاره، قسط، خرید) */
public class PayReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_ID = "id";
    private static final String CHANNEL = "pay_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, -1);
        if (id < 0) return;
        Db.Reminder r;
        try {
            r = Db.get(context).getReminder(id);
        } catch (Exception e) {
            return;
        }
        if (r == null) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "یادآور پرداخت‌ها", NotificationManager.IMPORTANCE_HIGH));
        }
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long days = ReminderMath.daysLeft(r.due, JalaliCalendar.todayGregorianString());
        String text = r.title + (r.amount > 0 ? " — " + Fmt.money(r.amount) : "")
                + " (" + ReminderMath.describePlain(days) + ")";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⏰ یادآور پرداخت")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify((int) (id % 1000000L) + 3000000, b.build());
    }
}
