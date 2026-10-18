package com.barber.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * وضعیت پرداخت پورسانت هر لایسنس (بدون وابستگی به اندروید).
 * قاعده: پرداختی که به یک لایسنس وصل شده، اول به همان لایسنس می‌رسد (تا سقف پورسانتش)؛
 * مازاد آن و پرداخت‌های «عمومی» به‌ترتیب تاریخ صدور از قدیمی‌ترین لایسنسِ پرداخت‌نشده تسویه می‌کنند.
 */
public final class Commission {

    public static final int UNPAID = 0;
    public static final int PARTIAL = 1;
    public static final int PAID = 2;

    public static final class Item {
        public final long licenseId;
        public final long commission;
        public long paid;

        Item(long licenseId, long commission) {
            this.licenseId = licenseId;
            this.commission = commission;
        }

        public long due() {
            return commission - paid;
        }

        public int status() {
            if (paid <= 0) return UNPAID;
            return paid >= commission ? PAID : PARTIAL;
        }
    }

    public static final class Result {
        public final List<Item> items = new ArrayList<Item>();
        /** پرداختی که بیشتر از کل پورسانت‌ها بوده (بستانکاری ویزیتور) */
        public long credit;
    }

    private Commission() {
    }

    /**
     * @param licenseIds   شناسه‌ی لایسنس‌ها از قدیمی به جدید
     * @param commissions  پورسانت هر لایسنس (هم‌اندازه با licenseIds)
     * @param payoutLicense شناسه‌ی لایسنس هر پرداخت (۰ = عمومی)
     * @param payoutAmount مبلغ هر پرداخت
     */
    public static Result allocate(long[] licenseIds, long[] commissions, long[] payoutLicense, long[] payoutAmount) {
        Result r = new Result();
        for (int i = 0; i < licenseIds.length; i++) {
            if (commissions[i] > 0) r.items.add(new Item(licenseIds[i], commissions[i]));
        }
        long pool = 0;
        for (int p = 0; p < payoutAmount.length; p++) {
            long amt = payoutAmount[p];
            if (amt <= 0) continue;
            Item target = null;
            if (payoutLicense[p] > 0) {
                for (Item it : r.items) if (it.licenseId == payoutLicense[p]) target = it;
            }
            if (target == null) {
                pool += amt;
            } else {
                long room = target.commission - target.paid;
                long use = Math.min(room, amt);
                target.paid += use;
                pool += amt - use;
            }
        }
        for (Item it : r.items) {
            if (pool <= 0) break;
            long room = it.commission - it.paid;
            long use = Math.min(room, pool);
            it.paid += use;
            pool -= use;
        }
        r.credit = pool;
        return r;
    }

    public static String statusLabel(int status) {
        switch (status) {
            case PAID:
                return "✅ پرداخت‌شده";
            case PARTIAL:
                return "🟡 بخشی پرداخت‌شده";
            default:
                return "❌ پرداخت‌نشده";
        }
    }
}
