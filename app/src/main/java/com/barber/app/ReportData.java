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
        /** روز ماه شمسی (برای نمودار) */
        public int day;
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
        public long barberId;
        public String date;
        public String barber;
        public String service;
        public double price;
        public double barberShare;
        public double salonShare;
    }

    /** تسویه‌ی یک آرایشگر درصدی در ماه */
    public static final class Settle {
        public long barberId;
        public String card = "";
        /** پرداخت‌شده به او در همین ماه */
        public double paid;
        /** مانده‌ی حساب او از ماه‌های قبل (سهم − پرداخت‌ها) */
        public double prev;
        public String barber;
        public int count;
        public double total;
        public double barberShare;
        public double salonShare;

        /** مانده‌ی همین ماه */
        public double remainingMonth() {
            return barberShare - paid;
        }

        /** مانده‌ی کل با احتساب ماه‌های قبل */
        public double remainingTotal() {
            return prev + barberShare - paid;
        }
    }

    /** اطلاعات هر آرایشگر برای تسویه (نام، کارت، مانده‌ی قبلی و پرداخت‌های ماه) */
    public static final class BarberInfo {
        public String name;
        public String card = "";
        public double prev;
        public double paid;
    }

    public static final class PayRec {
        public long barberId;
        public String date;
        public double amount;
        public String note;
    }

    /** اگر ≥ ۰ باشد این گزارش فقط مخصوص همین آرایشگر است (رسید و خروجی‌هایش فقط کارکرد خودش را دارد) */
    public long onlyBarberId = -1;
    public String onlyBarberName = "";

    public final Map<Long, BarberInfo> info = new java.util.LinkedHashMap<>();
    public final List<PayRec> pays = new ArrayList<>();

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
        if (onlyBarberId >= 0) return "تسویه " + onlyBarberName + " — " + monthName + " " + year;
        return "گزارش مالی " + monthName + " " + year;
    }

    /** نسخه‌ی مخصوص یک آرایشگر: فقط سرویس‌ها، پرداخت‌ها و حساب خودِ او؛ بدون نوبت‌ها و هزینه‌های سالن */
    public ReportData forBarber(Settle s) {
        ReportData o = new ReportData();
        o.salon = salon;
        o.monthName = monthName;
        o.generatedOn = generatedOn;
        o.currency = currency;
        o.year = year;
        o.month = month;
        o.onlyBarberId = s.barberId;
        o.onlyBarberName = s.barber;
        for (Svc v : svcs) if (v.barberId == s.barberId) o.svcs.add(v);
        for (PayRec p : pays) if (p.barberId == s.barberId) o.pays.add(p);
        BarberInfo bi = info.get(s.barberId);
        if (bi != null) o.info.put(s.barberId, bi);
        o.computeTotals();
        return o;
    }

    /** نام فایل امن و انگلیسی، مثلاً report_1405_07 */
    public String fileBaseName() {
        if (onlyBarberId >= 0) return String.format(Locale.US, "settle_%d_%04d_%02d", onlyBarberId, year, month);
        return String.format(Locale.US, "report_%04d_%02d", year, month);
    }

    /** تسویه‌ی آرایشگران، مرتب بر اساس نام */
    public List<Settle> settlements() {
        Map<Long, Settle> map = new java.util.LinkedHashMap<>();
        for (Svc s : svcs) {
            Settle st = map.get(s.barberId);
            if (st == null) {
                st = new Settle();
                st.barberId = s.barberId;
                st.barber = s.barber == null ? "—" : s.barber;
                map.put(s.barberId, st);
            }
            st.count++;
            st.total += s.price;
            st.barberShare += s.barberShare;
            st.salonShare += s.salonShare;
        }
        // آرایشگری که این ماه سرویس ندارد ولی مانده‌ی قبلی یا پرداخت دارد هم باید دیده شود
        for (Map.Entry<Long, BarberInfo> e : info.entrySet()) {
            if (!map.containsKey(e.getKey()) && (Math.abs(e.getValue().prev) > 0.5 || e.getValue().paid > 0.5)) {
                Settle st = new Settle();
                st.barberId = e.getKey();
                st.barber = e.getValue().name;
                map.put(e.getKey(), st);
            }
        }
        for (Settle st : map.values()) {
            BarberInfo bi = info.get(st.barberId);
            if (bi != null) {
                st.card = bi.card == null ? "" : bi.card;
                st.prev = bi.prev;
                st.paid = bi.paid;
            }
        }
        List<Settle> out = new ArrayList<>(map.values());
        java.util.Collections.sort(out, new java.util.Comparator<Settle>() {
            @Override
            public int compare(Settle a, Settle b) {
                return a.barber.compareTo(b.barber);
            }
        });
        return out;
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
                        .append(" | پرداخت‌شده ").append(fmt(s.paid))
                        .append(" | مانده ").append(fmt(s.remainingTotal())).append("\n");
            }
        }
        if (generatedOn != null && !generatedOn.isEmpty()) {
            sb.append("\nتاریخ تهیه: ").append(generatedOn);
        }
        return sb.toString();
    }

    /** رسید تسویه‌ی یک آرایشگر برای ارسال (فقط کارکرد و حساب خودِ او) */
    public String slipText(Settle s) {
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 رسید تسویه — ").append(salon).append("\n");
        sb.append("آرایشگر: ").append(s.barber).append("\n");
        sb.append("دوره: ").append(monthName).append(" ").append(year).append("\n");
        sb.append("━━━━━━━━━━━━\n");
        for (Svc v : svcs) {
            if (v.barberId != s.barberId) continue;
            sb.append(v.date).append(" | ").append(v.service).append(" | ").append(fmt(v.price))
                    .append(" ← سهم آرایشگر ").append(fmt(v.barberShare)).append("\n");
        }
        sb.append("━━━━━━━━━━━━\n");
        sb.append("تعداد سرویس: ").append(s.count).append("\n");
        sb.append("جمع مبلغ سرویس‌ها: ").append(money(s.total)).append("\n");
        sb.append("سهم سالن: ").append(money(s.salonShare)).append("\n");
        sb.append("سهم آرایشگر این ماه: ").append(money(s.barberShare)).append("\n");
        boolean any = false;
        for (PayRec p : pays) {
            if (p.barberId != s.barberId) continue;
            if (!any) sb.append("\nپرداخت‌ها:\n");
            any = true;
            sb.append("• ").append(p.date).append(" — ").append(money(p.amount))
                    .append(p.note == null || p.note.isEmpty() ? "" : " (" + p.note + ")").append("\n");
        }
        sb.append("جمع پرداخت‌شده: ").append(money(s.paid)).append("\n");
        sb.append("مانده‌ی این ماه: ").append(money(s.remainingMonth())).append("\n");
        if (Math.abs(s.prev) > 0.5) {
            sb.append("مانده از ماه‌های قبل: ").append(money(s.prev)).append("\n");
        }
        sb.append("✅ مانده‌ی کل قابل پرداخت: ").append(money(s.remainingTotal()));
        if (s.card != null && !s.card.isEmpty()) {
            sb.append("\nشماره کارت/حساب: ").append(s.card);
        }
        return sb.toString();
    }

    /** برگه‌های فایل اکسل */
    public List<XlsxWriter.Sheet> toSheets() {
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        String cur = "(" + currency + ")";
        if (onlyBarberId >= 0) return barberSheets(cur);

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
        t.widths = new double[]{22, 14, 16, 16, 16, 16, 16, 24};
        t.add("آرایشگر", "تعداد سرویس", "جمع مبلغ", "سهم آرایشگر", "سهم سالن", "پرداخت‌شده", "مانده‌ی ماه", "شماره کارت");
        t.headerRow = 0;
        int cnt = 0;
        for (Settle s : settlements()) {
            t.add(s.barber, s.count, s.total, s.barberShare, s.salonShare, s.paid, s.remainingMonth(), s.card);
            cnt += s.count;
        }
        t.add(XlsxWriter.bold("جمع"), XlsxWriter.bold(cnt), XlsxWriter.bold(tp), XlsxWriter.bold(tb), XlsxWriter.bold(ts));
        sheets.add(t);
        return sheets;
    }

    /** برگه‌های اکسل مخصوص یک آرایشگر (فقط حساب خودش) */
    private List<XlsxWriter.Sheet> barberSheets(String cur) {
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        Settle s = null;
        for (Settle x : settlements()) if (x.barberId == onlyBarberId) s = x;
        XlsxWriter.Sheet sum = new XlsxWriter.Sheet("حساب");
        sum.widths = new double[]{30, 22};
        sum.add(XlsxWriter.bold(title()));
        sum.add(salon);
        sum.add();
        sum.add("شرح", "مبلغ " + cur);
        sum.headerRow = 3;
        if (s != null) {
            sum.add("تعداد سرویس", s.count);
            sum.add("جمع مبلغ سرویس‌ها", s.total);
            sum.add("سهم آرایشگر این ماه", s.barberShare);
            sum.add("مانده از ماه‌های قبل", s.prev);
            sum.add("پرداخت‌شده", s.paid);
            sum.add(XlsxWriter.bold("مانده‌ی کل"), XlsxWriter.bold(s.remainingTotal()));
            if (s.card != null && !s.card.isEmpty()) sum.add("شماره کارت/حساب", s.card);
        }
        sheets.add(sum);

        XlsxWriter.Sheet v = new XlsxWriter.Sheet("سرویس‌ها");
        v.widths = new double[]{14, 22, 16, 16};
        v.add("تاریخ", "خدمت", "مبلغ", "سهم آرایشگر");
        v.headerRow = 0;
        double tp = 0, tb = 0;
        for (Svc x : svcs) {
            v.add(x.date, x.service, x.price, x.barberShare);
            tp += x.price;
            tb += x.barberShare;
        }
        v.add(XlsxWriter.bold("جمع"), null, XlsxWriter.bold(tp), XlsxWriter.bold(tb));
        sheets.add(v);

        XlsxWriter.Sheet p = new XlsxWriter.Sheet("پرداخت‌ها");
        p.widths = new double[]{14, 16, 30};
        p.add("تاریخ", "مبلغ", "توضیح");
        p.headerRow = 0;
        double tpay = 0;
        for (PayRec x : pays) {
            p.add(x.date, x.amount, x.note == null ? "" : x.note);
            tpay += x.amount;
        }
        p.add(XlsxWriter.bold("جمع"), XlsxWriter.bold(tpay), null);
        sheets.add(p);
        return sheets;
    }
}
