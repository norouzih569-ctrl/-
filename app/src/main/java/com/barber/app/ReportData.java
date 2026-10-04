package com.barber.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * داده‌های یک گزارش ماهانه. بدون وابستگی به اندروید است تا هم برای نمایش،
 * هم PDF، هم اکسل و هم متن اشتراکی یک منبع واحد باشد و جداگانه تست شود.
 */
public final class ReportData {

    public static final class Appt {
        public String date;
        public String time;
        public String customer;
        public String service;
        public double price;
    }

    public static final class Exp {
        public String date;
        public String desc;
        public double amount;
    }

    public static final class Svc {
        public String date;
        public String barber;
        public String service;
        public double price;
        public double barberShare;
        public double salonShare;
    }

    /** تسویه‌ی یک آرایشگر درصدی در ماه */
    public static final class Settle {
        public String barber;
        public int count;
        public double total;
        public double barberShare;
        public double salonShare;
    }

    public String salon = "آرایشگاه";
    public String monthName = "";
    public String generatedOn = "";
    public String currency = "تومان";
    public int year;
    public int month;

    public final List<Appt> appts = new ArrayList<>();
    public final List<Exp> exps = new ArrayList<>();
    public final List<Svc> svcs = new ArrayList<>();

    // مجموع‌ها (با computeTotals محاسبه می‌شوند)
    public double income;
    public double salonShare;
    public double expensesTotal;
    public double balance;

    /** مثل نسخه ویندوز: مانده = درآمد نوبت‌ها + سهم سالن از آرایشگران − هزینه‌ها */
    public void computeTotals() {
        income = 0;
        for (Appt a : appts) income += a.price;
        salonShare = 0;
        for (Svc s : svcs) salonShare += s.salonShare;
        expensesTotal = 0;
        for (Exp e : exps) expensesTotal += e.amount;
        balance = income + salonShare - expensesTotal;
    }

    public static String fmt(double v) {
        return String.format(Locale.US, "%,d", Math.round(v));
    }

    public String money(double v) {
        return fmt(v) + " " + currency;
    }

    public String title() {
        return "گزارش مالی " + monthName + " " + year;
    }

    /** نام فایل امن و انگلیسی، مثلاً report_1405_07 */
    public String fileBaseName() {
        return String.format(Locale.US, "report_%04d_%02d", year, month);
    }

    /** تسویه‌ی آرایشگران، مرتب بر اساس نام */
    public List<Settle> settlements() {
        Map<String, Settle> map = new TreeMap<>();
        for (Svc s : svcs) {
            String key = s.barber == null ? "—" : s.barber;
            Settle st = map.get(key);
            if (st == null) {
                st = new Settle();
                st.barber = key;
                map.put(key, st);
            }
            st.count++;
            st.total += s.price;
            st.barberShare += s.barberShare;
            st.salonShare += s.salonShare;
        }
        return new ArrayList<>(map.values());
    }

    /** خلاصه‌ی متنی قابل ارسال در پیام‌رسان‌ها */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 ").append(title()).append("\n");
        sb.append(salon).append("\n");
        sb.append("━━━━━━━━━━━━\n");
        sb.append("💰 درآمد نوبت‌ها: ").append(money(income)).append(" (").append(appts.size()).append(" نوبت)\n");
        sb.append("🏪 سهم سالن از آرایشگران: ").append(money(salonShare)).append("\n");
        sb.append("🧾 هزینه‌ها: ").append(money(expensesTotal)).append("\n");
        sb.append("✅ مانده: ").append(money(balance)).append("\n");
        List<Settle> st = settlements();
        if (!st.isEmpty()) {
            sb.append("\n💇 تسویه آرایشگران:\n");
            for (Settle s : st) {
                sb.append("• ").append(s.barber).append(": ").append(s.count).append(" سرویس | جمع ")
                        .append(fmt(s.total)).append(" | سهم آرایشگر ").append(fmt(s.barberShare))
                        .append(" | سهم سالن ").append(fmt(s.salonShare)).append("\n");
            }
        }
        if (generatedOn != null && !generatedOn.isEmpty()) {
            sb.append("\nتاریخ تهیه: ").append(generatedOn);
        }
        return sb.toString();
    }

    /** رسید تسویه‌ی یک آرایشگر برای ارسال */
    public String slipText(Settle s) {
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 رسید تسویه — ").append(salon).append("\n");
        sb.append("آرایشگر: ").append(s.barber).append("\n");
        sb.append("دوره: ").append(monthName).append(" ").append(year).append("\n");
        sb.append("━━━━━━━━━━━━\n");
        for (Svc v : svcs) {
            String name = v.barber == null ? "—" : v.barber;
            if (!name.equals(s.barber)) continue;
            sb.append(v.date).append(" | ").append(v.service).append(" | ").append(fmt(v.price))
                    .append(" ← سهم آرایشگر ").append(fmt(v.barberShare)).append("\n");
        }
        sb.append("━━━━━━━━━━━━\n");
        sb.append("تعداد سرویس: ").append(s.count).append("\n");
        sb.append("جمع مبلغ سرویس‌ها: ").append(money(s.total)).append("\n");
        sb.append("سهم سالن: ").append(money(s.salonShare)).append("\n");
        sb.append("✅ قابل پرداخت به آرایشگر: ").append(money(s.barberShare));
        return sb.toString();
    }

    /** برگه‌های فایل اکسل */
    public List<XlsxWriter.Sheet> toSheets() {
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        String cur = "(" + currency + ")";

        XlsxWriter.Sheet sum = new XlsxWriter.Sheet("خلاصه");
        sum.widths = new double[]{34, 22};
        sum.add(XlsxWriter.bold(title()));
        sum.add(salon);
        sum.add();
        sum.add("شرح", "مبلغ " + cur);
        sum.headerRow = 3;
        sum.add("درآمد نوبت‌ها", income);
        sum.add("سهم سالن از آرایشگران", salonShare);
        sum.add("هزینه‌ها", expensesTotal);
        sum.add(XlsxWriter.bold("مانده"), XlsxWriter.bold(balance));
        sum.add();
        sum.add("تعداد نوبت‌ها", appts.size());
        sum.add("تعداد سرویس‌های آرایشگران", svcs.size());
        sheets.add(sum);

        XlsxWriter.Sheet a = new XlsxWriter.Sheet("نوبت‌ها");
        a.widths = new double[]{14, 10, 26, 18, 16};
        a.add("تاریخ", "ساعت", "مشتری", "خدمت", "قیمت " + cur);
        a.headerRow = 0;
        for (Appt x : appts) a.add(x.date, x.time, x.customer, x.service, x.price);
        a.add(XlsxWriter.bold("جمع"), null, null, null, XlsxWriter.bold(income));
        sheets.add(a);

        XlsxWriter.Sheet e = new XlsxWriter.Sheet("هزینه‌ها");
        e.widths = new double[]{14, 40, 16};
        e.add("تاریخ", "شرح", "مبلغ " + cur);
        e.headerRow = 0;
        for (Exp x : exps) e.add(x.date, x.desc, x.amount);
        e.add(XlsxWriter.bold("جمع"), null, XlsxWriter.bold(expensesTotal));
        sheets.add(e);

        XlsxWriter.Sheet v = new XlsxWriter.Sheet("سرویس آرایشگران");
        v.widths = new double[]{14, 20, 18, 14, 16, 14};
        v.add("تاریخ", "آرایشگر", "خدمت", "قیمت", "سهم آرایشگر", "سهم سالن");
        v.headerRow = 0;
        double tp = 0, tb = 0, ts = 0;
        for (Svc x : svcs) {
            v.add(x.date, x.barber, x.service, x.price, x.barberShare, x.salonShare);
            tp += x.price;
            tb += x.barberShare;
            ts += x.salonShare;
        }
        v.add(XlsxWriter.bold("جمع"), null, null, XlsxWriter.bold(tp), XlsxWriter.bold(tb), XlsxWriter.bold(ts));
        sheets.add(v);

        XlsxWriter.Sheet t = new XlsxWriter.Sheet("تسویه آرایشگران");
        t.widths = new double[]{22, 14, 16, 16, 16};
        t.add("آرایشگر", "تعداد سرویس", "جمع مبلغ", "سهم آرایشگر", "سهم سالن");
        t.headerRow = 0;
        int cnt = 0;
        for (Settle s : settlements()) {
            t.add(s.barber, s.count, s.total, s.barberShare, s.salonShare);
            cnt += s.count;
        }
        t.add(XlsxWriter.bold("جمع"), XlsxWriter.bold(cnt), XlsxWriter.bold(tp), XlsxWriter.bold(tb), XlsxWriter.bold(ts));
        sheets.add(t);
        return sheets;
    }
}
