package com.barber.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** محاسبه‌ی گزارش‌های مالی (بدون وابستگی به اندروید). تاریخ‌ها میلادی yyyy-MM-dd (مقایسه‌ی رشته‌ای). */
public final class Stats {

    public static final class Totals {
        public int salesCount;
        public long salesAmount;      // مجموع مبلغ نهایی لایسنس‌های فروخته‌شده (غیرباطل، بدون جابجایی)
        public long discountAmount;   // مجموع تخفیف‌ها
        public long received;         // دریافتی نقدی از مشتریان در بازه
        public long commissionAccrued;// پورسانت لایسنس‌های بازه
        public long commissionPaid;   // پورسانت پرداخت‌شده در بازه
        public final Map<Integer, int[]> byTypeCount = new HashMap<Integer, int[]>();   // نوع → [تعداد]
        public final Map<Integer, long[]> byTypeAmount = new HashMap<Integer, long[]>(); // نوع → [مبلغ]
        public final Map<Long, long[]> byVisitor = new HashMap<Long, long[]>();          // ویزیتور → [پورسانت بازه]

        /** سود تقریبی: فروش منهای پورسانت (روش تعهدی) */
        public long netSales() {
            return salesAmount - commissionAccrued;
        }

        /** خالص نقدی: دریافتی منهای پورسانت پرداخت‌شده */
        public long netCash() {
            return received - commissionPaid;
        }
    }

    /** مانده‌های کل (مستقل از بازه) */
    public static final class Position {
        public long receivables;     // مجموع بدهی مشتریان (فقط بدهکارها)
        public long commissionDue;   // مجموع پورسانت پرداخت‌نشده به همه‌ی ویزیتورها (فقط طلبکارها)
        public int debtors;
    }

    private Stats() {
    }

    private static boolean in(String d, String from, String to) {
        return d != null && d.compareTo(from) >= 0 && d.compareTo(to) <= 0;
    }

    public static Totals compute(List<Models.License> lics, List<Models.Payment> pays,
                                 List<Models.Payout> outs, String from, String to) {
        Totals t = new Totals();
        for (Models.License l : lics) {
            if (l.voided || l.type == LicenseCodec.TYPE_TRANSFER || !in(l.issueDate, from, to)) continue;
            t.salesCount++;
            t.salesAmount += l.finalPrice;
            t.discountAmount += l.basePrice - l.finalPrice;
            t.commissionAccrued += l.commissionAmount;
            int[] cnt = t.byTypeCount.get(l.type);
            if (cnt == null) {
                cnt = new int[1];
                t.byTypeCount.put(l.type, cnt);
                t.byTypeAmount.put(l.type, new long[1]);
            }
            cnt[0]++;
            t.byTypeAmount.get(l.type)[0] += l.finalPrice;
            if (l.visitorId > 0 && l.commissionAmount > 0) {
                long[] v = t.byVisitor.get(l.visitorId);
                if (v == null) {
                    v = new long[1];
                    t.byVisitor.put(l.visitorId, v);
                }
                v[0] += l.commissionAmount;
            }
        }
        for (Models.Payment p : pays) if (in(p.payDate, from, to)) t.received += p.amount;
        for (Models.Payout p : outs) if (in(p.payDate, from, to)) t.commissionPaid += p.amount;
        return t;
    }

    public static Position position(List<Models.License> lics, List<Models.Payment> pays, List<Models.Payout> outs) {
        Map<Long, long[]> cust = new HashMap<Long, long[]>();   // مشتری → [فروش، دریافتی]
        Map<Long, long[]> vis = new HashMap<Long, long[]>();    // ویزیتور → [پورسانت، پرداختی]
        for (Models.License l : lics) {
            if (l.voided) continue;
            long[] c = cust.get(l.customerId);
            if (c == null) {
                c = new long[2];
                cust.put(l.customerId, c);
            }
            c[0] += l.finalPrice;
            if (l.visitorId > 0) {
                long[] v = vis.get(l.visitorId);
                if (v == null) {
                    v = new long[2];
                    vis.put(l.visitorId, v);
                }
                v[0] += l.commissionAmount;
            }
        }
        for (Models.Payment p : pays) {
            long[] c = cust.get(p.customerId);
            if (c == null) {
                c = new long[2];
                cust.put(p.customerId, c);
            }
            c[1] += p.amount;
        }
        for (Models.Payout p : outs) {
            long[] v = vis.get(p.visitorId);
            if (v == null) {
                v = new long[2];
                vis.put(p.visitorId, v);
            }
            v[1] += p.amount;
        }
        Position pos = new Position();
        for (long[] c : cust.values()) {
            long due = c[0] - c[1];
            if (due > 0) {
                pos.receivables += due;
                pos.debtors++;
            }
        }
        for (long[] v : vis.values()) {
            long due = v[0] - v[1];
            if (due > 0) pos.commissionDue += due;
        }
        return pos;
    }
}
