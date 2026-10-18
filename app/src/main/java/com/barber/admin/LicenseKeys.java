package com.barber.admin;

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
