package com.barber.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// ---- کلاس‌های کمکی کوچک (برای کم‌شدن تعداد فایل‌ها یک‌جا جمع شده‌اند) ----

/** هر تب برنامه باید بتواند اطلاعاتش را دوباره از دیتابیس بخواند. */
interface Refreshable {
    void refresh();
}

/** محاسبات خالص فروش محصول (قابل تست) */
final class ProductMath {
    private ProductMath() {
    }

    /** سود هر واحد */
    public static double unitProfit(double sell, double buy) {
        return sell - buy;
    }

    /** درصد سود نسبت به قیمت خرید؛ اگر خرید صفر باشد 0 */
    public static double markupPercent(double sell, double buy) {
        return buy <= 0 ? 0 : (sell - buy) * 100.0 / buy;
    }

    public static double saleProfit(int qty, double unitPrice, double unitCost) {
        return qty * (unitPrice - unitCost);
    }

    /** null اگر درست است، وگرنه پیام خطا */
    public static String validateSale(int qty, int stock) {
        if (qty <= 0) return "تعداد باید حداقل ۱ باشد";
        if (qty > stock) return "موجودی کافی نیست (موجودی: " + stock + ")";
        return null;
    }
}

/** محاسبات خالص نمودار و مقایسه‌ی گزارش (قابل تست) */
final class ReportMath {
    private ReportMath() {
    }

    /** جمع مبلغ هر روز ماه؛ days[i] شماره‌ی روز (۱ تا monthLen) */
    public static double[] dailyTotals(int[] days, double[] amounts, int monthLen) {
        double[] out = new double[monthLen];
        for (int i = 0; i < days.length; i++) {
            int d = days[i];
            if (d >= 1 && d <= monthLen) out[d - 1] += amounts[i];
        }
        return out;
    }

    /** درصد تغییر نسبت به ماه قبل؛ null اگر ماه قبل صفر بود (مقایسه معنی ندارد) */
    public static Double changePercent(double cur, double prev) {
        if (prev <= 0) return null;
        return (cur - prev) * 100.0 / prev;
    }

    /** اندیس بیشترین مقدار؛ -1 اگر همه صفر */
    public static int maxIndex(double[] v) {
        int best = -1;
        double m = 0;
        for (int i = 0; i < v.length; i++) {
            if (v[i] > m) {
                m = v[i];
                best = i;
            }
        }
        return best;
    }

    public static String describeChange(double cur, double prev) {
        Double p = changePercent(cur, prev);
        if (p == null) return cur > 0 ? "ماه قبل درآمدی ثبت نشده بود" : "";
        long r = Math.round(Math.abs(p));
        if (r == 0) return "تقریباً برابر با ماه قبل";
        return (p > 0 ? "▲ " : "▼ ") + r + "٪ " + (p > 0 ? "بیشتر" : "کمتر") + " از ماه قبل";
    }
}

/** منطق خالص سقف سالانه‌ی درآمد کارتی/واریزی (قابل تست) */
final class TaxCap {
    public static final int OK = 0, WARN = 1, OVER = 2;

    private TaxCap() {
    }

    /** cap <= 0 یعنی غیرفعال */
    public static int level(double received, double cap, int warnPercent) {
        if (cap <= 0) return OK;
        if (received >= cap) return OVER;
        if (received >= cap * warnPercent / 100.0) return WARN;
        return OK;
    }

    public static double percentUsed(double received, double cap) {
        return cap <= 0 ? 0 : received * 100.0 / cap;
    }
}

/** منطق خالص لیست خدمات (بدون وابستگی به اندروید؛ قابل تست) */
final class ServiceList {

    public static final int MAX_NAME = 30;
    public static final int MAX_COUNT = 40;

    private ServiceList() {
    }

    public static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(n);
        }
        return sb.toString();
    }

    public static List<String> parse(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split("\n")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** نام تمیز: فاصله‌های اضافه و خط جدید حذف می‌شود */
    public static String clean(String name) {
        if (name == null) return "";
        return name.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * @param self نام فعلی هنگام ویرایش (یا null هنگام افزودن)
     * @return متن خطا یا null اگر درست است
     */
    public static String validateName(String name, List<String> existing, String self) {
        String n = clean(name);
        if (n.isEmpty()) return "نام خدمت را وارد کنید";
        if (n.length() > MAX_NAME) return "نام خدمت حداکثر " + MAX_NAME + " حرف";
        if (n.indexOf('+') >= 0 || n.indexOf('|') >= 0) return "از + و | در نام استفاده نکنید";
        for (String e : existing) {
            if (e.equals(n) && (self == null || !e.equals(self))) return "این خدمت قبلاً وجود دارد";
        }
        if (self == null && existing.size() >= MAX_COUNT) return "حداکثر " + MAX_COUNT + " خدمت";
        return null;
    }
}

/** روش‌های پرداخت و محاسبه‌ی بدهی (منطق خالص؛ قابل تست) */
final class Pay {

    public static final String CASH = "cash";
    public static final String CARD = "card";
    public static final String TRANSFER = "transfer";

    public static final String[] METHODS = {CASH, CARD, TRANSFER};
    public static final String[] LABELS = {"💵 نقد", "💳 کارت", "🏦 واریز"};

    private Pay() {
    }

    public static String label(String method) {
        if ("pkg".equals(method)) return "📦 پکیج";
        for (int i = 0; i < METHODS.length; i++) {
            if (METHODS[i].equals(method)) return LABELS[i];
        }
        return "—";
    }

    public static String methodAt(int index) {
        return (index >= 0 && index < METHODS.length) ? METHODS[index] : CASH;
    }

    /**
     * بدهی یک نوبت. نوبت قدیمی (tracked=false) یا در انتظار، بدهی ندارد.
     * @param earned مبلغ حساب‌شده‌ی نوبت (final_price یا price)
     */
    public static double debt(boolean tracked, boolean done, double earned, double paid) {
        if (!tracked || !done) return 0;
        double d = earned - paid;
        return d > 0.5 ? d : 0; // کمتر از نیم تومان = تسویه
    }

    /**
     * مبلغ دریافتی را از متن فیلد می‌خواند. خالی = کل مبلغ.
     * @return null اگر نامعتبر باشد (منفی یا بیشتر از کل)
     */
    public static Double resolvePaid(String fieldText, double total) {
        String t = fieldText == null ? "" : fieldText.trim();
        if (t.isEmpty()) return total;
        Double v = Fmt.parsePrice(t);
        if (v == null || v < 0 || v > total) return null;
        return v;
    }
}

/** ارسال دستی پیامک با برنامه‌ی پیامک گوشی (بدون نیاز به مجوز؛ خود کاربر دکمه‌ی ارسال را می‌زند). */
final class Messenger {

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

/** منطق خالص یادآورهای پرداخت (اجاره، قسط، خرید): تاریخ بعدی و روزهای مانده */
final class ReminderMath {
    private ReminderMath() {
    }

    /** سررسید ماه شمسی بعد، با همان روز (اگر ماه بعد آن روز را ندارد، آخرین روز ماه) */
    public static String nextMonthDue(String dueIso, int anchorDay) {
        int[] j = JalaliCalendar.jalaliOfIso(dueIso);
        int y = j[0], m = j[1] + 1;
        if (m > 12) {
            m = 1;
            y++;
        }
        int d = Math.min(anchorDay > 0 ? anchorDay : j[2], JalaliCalendar.monthLength(y, m));
        return JalaliCalendar.jalaliToGregorianString(y, m, d);
    }

    /** مثبت = روز مانده؛ ۰ = امروز؛ منفی = روز گذشته از سررسید */
    public static long daysLeft(String dueIso, String todayIso) {
        return ChronoUnit.DAYS.between(LocalDate.parse(todayIso), LocalDate.parse(dueIso));
    }

    /** آیا باید در برنامه هشدار داد؟ (گذشته یا در بازه‌ی leadDays روز مانده) */
    public static boolean isDueSoon(String dueIso, String todayIso, int leadDays) {
        return daysLeft(dueIso, todayIso) <= leadDays;
    }

    public static String describePlain(long days) {
        if (days < 0) return (-days) + " روز گذشته";
        if (days == 0) return "امروز";
        if (days == 1) return "فردا";
        return days + " روز دیگر";
    }

    public static String describe(long days) {
        String icon = days < 0 ? "🔴 " : days == 0 ? "🟠 " : days == 1 ? "🟡 " : "";
        return icon + describePlain(days);
    }
}

/**
 * کلید مخفی سازنده/بررسی‌کننده‌ی کد لایسنس.
 * ⚠️ دقیقاً همین مقدار باید در «برنامه‌ی مدیریتی» هم باشد، وگرنه کدها قبول نمی‌شوند.
 * مخزن (Repository) GitHub را حتماً خصوصی (Private) نگه دارید و این مقدار را جایی منتشر نکنید.
 */
final class LicenseKeys {
    static final String SECRET = "5b0752dcd05010fcfe835076876430e76784702ba4b45b224c5abf1585e1a1e6";

    private LicenseKeys() {
    }
}
