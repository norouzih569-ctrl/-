package com.barber.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.List;

/** زمان‌بندی اعلان «نیم ساعت تا نوبت» با AlarmManager (معادل نخ اعلان‌ها در نسخه ویندوز). */
public final class ReminderScheduler {

    private static final long LEAD_MS = 30L * 60L * 1000L;

    private ReminderScheduler() {
    }

    /** زمان نوبت به میلی‌ثانیه؛ در صورت خطا -1 */
    static long toMillis(String isoDate, String time) {
        try {
            String[] d = isoDate.split("-");
            String[] t = time.split(":");
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, Integer.parseInt(d[0]));
            c.set(Calendar.MONTH, Integer.parseInt(d[1]) - 1);
            c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(d[2]));
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0]));
            c.set(Calendar.MINUTE, Integer.parseInt(t[1]));
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    private static PendingIntent pending(Context ctx, long id, String name, String time, int flags) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra(ReminderReceiver.EXTRA_ID, id);
        i.putExtra(ReminderReceiver.EXTRA_NAME, name);
        i.putExtra(ReminderReceiver.EXTRA_TIME, time);
        return PendingIntent.getBroadcast(ctx, (int) id, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context ctx, long id, String customerName, String isoDate, String time) {
        long apptMs = toMillis(isoDate, time);
        if (apptMs < 0) return;
        long now = System.currentTimeMillis();
        if (apptMs <= now) {
            cancel(ctx, id);
            return;
        }
        long trigger = Math.max(apptMs - LEAD_MS, now + 2000L);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(ctx, id, customerName, time, PendingIntent.FLAG_UPDATE_CURRENT);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
    }

    public static void cancel(Context ctx, long id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(ctx, id, "", "", PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
        cancelSms(ctx, id);
    }

    // ---------------- پیامک یادآوری برای مشتری (انتخابی) ----------------

    /** کد درخواست جدا از اعلان نیم‌ساعته، تا دو آلارم با هم قاطی نشوند */
    private static int smsRequestCode(long id) {
        return (int) (id % 100000000L) + 100000000;
    }

    private static PendingIntent smsPending(Context ctx, long id, int flags) {
        Intent i = new Intent(ctx, SmsReminderReceiver.class);
        i.putExtra(SmsReminderReceiver.EXTRA_ID, id);
        return PendingIntent.getBroadcast(ctx, smsRequestCode(id), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * پیامک یادآوری را فقط وقتی زمان‌بندی می‌کند که برای این نوبت انتخاب شده و هنوز ارسال نشده باشد.
     * در غیر این صورت آلارم قبلی (اگر بود) لغو می‌شود.
     */
    public static void scheduleSms(Context ctx, Db.Appointment a) {
        if (a == null || !a.smsReminder || a.smsSent) {
            if (a != null) cancelSms(ctx, a.id);
            return;
        }
        long apptMs = toMillis(a.date, a.time);
        if (apptMs < 0) return;
        long trigger = Logic.smsTriggerTime(apptMs, System.currentTimeMillis(),
                AppConfig.getSmsLeadHours(ctx));
        if (trigger < 0) {
            cancelSms(ctx, a.id);
            return;
        }
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger,
                smsPending(ctx, a.id, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void cancelSms(Context ctx, long id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = smsPending(ctx, id, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    /** همه نوبت‌های امروز و آینده را دوباره زمان‌بندی می‌کند. */
    public static void rescheduleAll(Context ctx) {
        try {
            List<Db.Appointment> list = Db.get(ctx).getAppointmentsFrom(JalaliCalendar.todayGregorianString());
            for (Db.Appointment a : list) {
                schedule(ctx, a.id, a.customerName, a.date, a.time);
                scheduleSms(ctx, a);
            }
        } catch (Exception ignored) {
            // یادآور اختیاری است؛ خطا نباید برنامه را متوقف کند
        }
    }
}
