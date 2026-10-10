package com.barber.admin;

/** برچسب‌های فارسی نمایشی. */
public final class Labels {

    private Labels() {
    }

    public static String licenseType(int type) {
        switch (type) {
            case LicenseCodec.TYPE_6M:
                return "لایسنس ۶ ماهه";
            case LicenseCodec.TYPE_1Y:
                return "لایسنس ۱ ساله";
            case LicenseCodec.TYPE_2Y:
                return "لایسنس ۲ ساله";
            case LicenseCodec.TYPE_3Y:
                return "لایسنس ۳ ساله";
            case LicenseCodec.TYPE_5Y:
                return "لایسنس ۵ ساله";
            case LicenseCodec.TYPE_TRANSFER:
                return "جابجایی دستگاه";
            default:
                return "نوع نامشخص";
        }
    }

    /** نوع‌های قابل فروش، از کوتاه به بلند */
    public static final int[] SELLABLE_TYPES = {
            LicenseCodec.TYPE_6M, LicenseCodec.TYPE_1Y, LicenseCodec.TYPE_2Y,
            LicenseCodec.TYPE_3Y, LicenseCodec.TYPE_5Y};

    public static final String[] PAY_METHODS = {"نقد", "کارت به کارت", "واریز بانکی", "چک", "سایر"};
}
