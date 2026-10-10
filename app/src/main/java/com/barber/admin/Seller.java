package com.barber.admin;

/** مشخصات فروشنده برای پیش‌فاکتور و قرارداد (ذخیره در جدول تنظیمات). */
public final class Seller {
    public String name = "";        // نام شخص/شرکت
    public String brand = "نرم‌افزار مدیریت آرایشگاه";
    public String phone = "";
    public String address = "";
    public String nationalId = "";  // کد/شناسه ملی
    public String economicCode = "";
    public String bank = "";        // شماره کارت / شبا / نام بانک
    public String signer = "";      // نام امضاکننده
    public double vatPercent = 0;   // مالیات بر ارزش افزوده (۰ = نمایش داده نشود)

    public static final String[] KEYS = {"seller_name", "seller_brand", "seller_phone", "seller_address",
            "seller_nid", "seller_eco", "seller_bank", "seller_signer", "seller_vat"};

    public static Seller load(Db db) {
        Seller s = new Seller();
        s.name = db.getSetting("seller_name", "");
        s.brand = db.getSetting("seller_brand", s.brand);
        s.phone = db.getSetting("seller_phone", "");
        s.address = db.getSetting("seller_address", "");
        s.nationalId = db.getSetting("seller_nid", "");
        s.economicCode = db.getSetting("seller_eco", "");
        s.bank = db.getSetting("seller_bank", "");
        s.signer = db.getSetting("seller_signer", "");
        try {
            s.vatPercent = Pricing.clampPercent(Double.parseDouble(db.getSetting("seller_vat", "0")));
        } catch (NumberFormatException e) {
            s.vatPercent = 0;
        }
        return s;
    }

    public void save(Db db) {
        db.setSetting("seller_name", name.trim());
        db.setSetting("seller_brand", brand.trim());
        db.setSetting("seller_phone", phone.trim());
        db.setSetting("seller_address", address.trim());
        db.setSetting("seller_nid", nationalId.trim());
        db.setSetting("seller_eco", economicCode.trim());
        db.setSetting("seller_bank", bank.trim());
        db.setSetting("seller_signer", signer.trim());
        db.setSetting("seller_vat", String.valueOf(Pricing.clampPercent(vatPercent)));
    }
}
