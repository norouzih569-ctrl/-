package com.barber.admin;

import java.util.TimeZone;

/** منطق صدور کد (بدون وابستگی به اندروید). */
public final class IssueLogic {

    /** تعداد روز مهلت فعال‌سازی کد؛ مطابق LicenseLogic برنامه‌ی آرایشگاه */
    public static final int ACTIVATION_WINDOW_DAYS = 30;

    private IssueLogic() {
    }

    /** شماره‌ی روز (از ۱۹۷۰) برای زمان و منطقه‌ی زمانی داده‌شده؛ مثل برنامه‌ی آرایشگاه */
    public static long epochDay(long millis, long tzOffsetMillis) {
        return Math.floorDiv(millis + tzOffsetMillis, 86400000L);
    }

    public static long todayEpochDay() {
        long now = System.currentTimeMillis();
        return epochDay(now, TimeZone.getDefault().getOffset(now));
    }

    /** مقدار issueDay داخل کد (روز از ۱ ژانویه‌ی ۲۰۲۵)؛ ‎-1 اگر در بازه‌ی مجاز نیست */
    public static int issueDayOf(long epochDay) {
        long d = epochDay - LicenseCodec.BASE_EPOCH_DAY;
        return (d < 0 || d > 16383) ? -1 : (int) d;
    }

    /**
     * شناسه‌ی دستگاه را نرمال می‌کند و به شکل XXXXX-XXXXX برمی‌گرداند؛
     * اگر ۱۰ کاراکتر معتبر نباشد null.
     */
    public static String parseDevice(String raw) {
        String d = LicenseCodec.normalizeDevice(raw);
        if (d.length() != 10) return null;
        for (int i = 0; i < d.length(); i++) {
            if (LicenseCodec.ALPHABET.indexOf(d.charAt(i)) < 0) return null;
        }
        return d.substring(0, 5) + "-" + d.substring(5);
    }

    /** ساخت کد برای یک نوع لایسنس زمان‌دار؛ null اگر ورودی نامعتبر باشد */
    public static String makeCode(String secret, String deviceRaw, int type, long todayEpochDay) {
        String dev = parseDevice(deviceRaw);
        if (dev == null || LicenseCodec.daysForType(type) == 0) return null;
        int day = issueDayOf(todayEpochDay);
        if (day < 0) return null;
        return LicenseCodec.formatCode(LicenseCodec.generate(secret, dev, type, day, 0));
    }

    /** حداکثر روز باقی‌مانده‌ای که در کد جابجایی جا می‌شود (۱۱ بیت) */
    public static final int MAX_TRANSFER_DAYS = 2047;

    /** شماره‌ی روز از تاریخ میلادی yyyy-MM-dd؛ ‎-1 در صورت نامعتبر بودن */
    public static long epochDayOfIso(String iso) {
        try {
            String[] p = iso.split("-");
            return java.time.LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2])).toEpochDay();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * فاصله‌ی روز صدور تا پایان لایسنس برای کد جابجایی؛
     * ‎-1 اگر پایان گذشته باشد، -2 اگر بیش از ۲۰۴۷ روز مانده باشد.
     */
    public static int transferExtra(long todayEpochDay, long endEpochDay) {
        long extra = endEpochDay - todayEpochDay;
        if (extra < 0) return -1;
        if (extra > MAX_TRANSFER_DAYS) return -2;
        return (int) extra;
    }

    /** کد جابجایی دستگاه با تاریخ پایان ثابت؛ null اگر ورودی نامعتبر باشد */
    public static String makeTransferCode(String secret, String deviceRaw, long todayEpochDay, long endEpochDay) {
        String dev = parseDevice(deviceRaw);
        int day = issueDayOf(todayEpochDay);
        int extra = transferExtra(todayEpochDay, endEpochDay);
        if (dev == null || day < 0 || extra < 0) return null;
        return LicenseCodec.formatCode(LicenseCodec.generate(secret, dev, LicenseCodec.TYPE_TRANSFER, day, extra));
    }

    /** متن آماده‌ی ارسال برای کد جابجایی */
    public static String transferMessage(String code, String device, String endJalali) {
        return "کد جابجایی نرم‌افزار مدیریت آرایشگاه به گوشی جدید:\n\n"
                + code + "\n\n"
                + "مسیر: منوی ⋮ ← «لایسنس و فعال‌سازی» ← وارد کردن کد.\n"
                + (endJalali == null ? "" : "تاریخ پایان لایسنس شما تغییری نمی‌کند: " + endJalali + "\n")
                + "این کد فقط روی گوشی با شناسه‌ی " + device + " کار می‌کند و تا "
                + ACTIVATION_WINDOW_DAYS + " روز بعد از امروز قابل استفاده است.";
    }

    /** متن آماده‌ی ارسال برای مشتری */
    public static String message(String code, int type, String device) {
        if (type == LicenseCodec.TYPE_TRANSFER) return transferMessage(code, device, null);
        return "کد فعال‌سازی نرم‌افزار مدیریت آرایشگاه (" + Labels.licenseType(type) + "):\n\n"
                + code + "\n\n"
                + "مسیر: منوی ⋮ ← «لایسنس و فعال‌سازی» ← وارد کردن کد.\n"
                + "این کد فقط روی گوشی با شناسه‌ی " + device + " کار می‌کند و تا "
                + ACTIVATION_WINDOW_DAYS + " روز بعد از امروز قابل استفاده است.";
    }

    /** خلاصه‌ی مالی یک فروش */
    public static final class Summary {
        public long base;
        public double discountPercent;
        public long finalPrice;
        public long discountAmount;
        public double commissionPercent;
        public long commission;
    }

    public static Summary summarize(long base, double discountPercent, double commissionPercent) {
        Summary s = new Summary();
        s.base = Math.max(0, base);
        s.discountPercent = Pricing.clampPercent(discountPercent);
        s.finalPrice = Pricing.finalPrice(s.base, s.discountPercent);
        s.discountAmount = s.base - s.finalPrice;
        s.commissionPercent = Pricing.clampPercent(commissionPercent);
        s.commission = Pricing.commission(s.finalPrice, s.commissionPercent);
        return s;
    }
}
