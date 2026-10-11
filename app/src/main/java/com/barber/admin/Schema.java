package com.barber.admin;

/**
 * ساختار پایگاه داده (SQLite). تاریخ‌ها به‌صورت میلادی yyyy-MM-dd ذخیره می‌شوند و فقط هنگام نمایش شمسی می‌شوند.
 * مبلغ‌ها به تومان و عدد صحیح هستند.
 * کلاس بدون وابستگی به اندروید است تا ساختار قابل تست باشد.
 */
final class Schema {
    static final int VERSION = 1;

    static final String[] CREATE = {
            // مشتری (آرایشگاه)
            "CREATE TABLE customers ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "shop_name TEXT NOT NULL,"
                    + "owner_name TEXT NOT NULL DEFAULT '',"
                    + "mobile TEXT NOT NULL DEFAULT '',"
                    + "phone TEXT NOT NULL DEFAULT '',"
                    + "city TEXT NOT NULL DEFAULT '',"
                    + "address TEXT NOT NULL DEFAULT '',"
                    + "national_id TEXT NOT NULL DEFAULT '',"
                    + "economic_code TEXT NOT NULL DEFAULT '',"
                    + "postal_code TEXT NOT NULL DEFAULT '',"
                    + "visitor_id INTEGER REFERENCES visitors(id),"
                    + "met_date TEXT NOT NULL DEFAULT '',"
                    + "notes TEXT NOT NULL DEFAULT '',"
                    + "created_at TEXT NOT NULL DEFAULT ''"
                    + ")",
            // ویزیتور
            "CREATE TABLE visitors ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL,"
                    + "mobile TEXT NOT NULL DEFAULT '',"
                    + "percent REAL NOT NULL DEFAULT 0,"
                    + "active INTEGER NOT NULL DEFAULT 1,"
                    + "notes TEXT NOT NULL DEFAULT '',"
                    + "created_at TEXT NOT NULL DEFAULT ''"
                    + ")",
            // لایسنس صادرشده (type: 1..3 سال، 4 جابجایی دستگاه)
            "CREATE TABLE licenses ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "customer_id INTEGER NOT NULL REFERENCES customers(id),"
                    + "device_id TEXT NOT NULL,"
                    + "type INTEGER NOT NULL,"
                    + "code TEXT NOT NULL,"
                    + "issue_date TEXT NOT NULL,"
                    + "months INTEGER NOT NULL DEFAULT 0,"
                    + "base_price INTEGER NOT NULL DEFAULT 0,"
                    + "discount_percent REAL NOT NULL DEFAULT 0,"
                    + "final_price INTEGER NOT NULL DEFAULT 0,"
                    + "visitor_id INTEGER REFERENCES visitors(id),"
                    + "commission_percent REAL NOT NULL DEFAULT 0,"
                    + "commission_amount INTEGER NOT NULL DEFAULT 0,"
                    + "end_date TEXT NOT NULL DEFAULT '',"
                    + "void INTEGER NOT NULL DEFAULT 0,"
                    + "notes TEXT NOT NULL DEFAULT '',"
                    + "created_at TEXT NOT NULL DEFAULT ''"
                    + ")",
            // دریافتی از مشتری (اقساط)؛ license_id می‌تواند خالی باشد (پرداخت عمومی)
            "CREATE TABLE payments ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "customer_id INTEGER NOT NULL REFERENCES customers(id),"
                    + "license_id INTEGER REFERENCES licenses(id),"
                    + "amount INTEGER NOT NULL,"
                    + "pay_date TEXT NOT NULL,"
                    + "method TEXT NOT NULL DEFAULT '',"
                    + "note TEXT NOT NULL DEFAULT ''"
                    + ")",
            // پرداخت پورسانت به ویزیتور
            "CREATE TABLE payouts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "visitor_id INTEGER NOT NULL REFERENCES visitors(id),"
                    + "license_id INTEGER REFERENCES licenses(id),"
                    + "amount INTEGER NOT NULL,"
                    + "pay_date TEXT NOT NULL,"
                    + "note TEXT NOT NULL DEFAULT ''"
                    + ")",
            // لیست قیمت هر نوع لایسنس زمان‌دار
            "CREATE TABLE prices ("
                    + "type INTEGER PRIMARY KEY,"
                    + "price INTEGER NOT NULL,"
                    + "discount_percent REAL NOT NULL DEFAULT 0"
                    + ")",
            "CREATE TABLE settings ("
                    + "key TEXT PRIMARY KEY,"
                    + "value TEXT NOT NULL DEFAULT ''"
                    + ")",
            "CREATE INDEX idx_lic_customer ON licenses(customer_id)",
            "CREATE INDEX idx_lic_visitor ON licenses(visitor_id)",
            "CREATE INDEX idx_pay_customer ON payments(customer_id)",
            "CREATE INDEX idx_pay_license ON payments(license_id)",
            "CREATE INDEX idx_payout_visitor ON payouts(visitor_id)",
            // قیمت‌های اولیه (قابل ویرایش در بخش ۵)
            // کلید = نوع لایسنس (۵: ۶ماهه، ۱: ۱ساله، ۲: ۲ساله، ۳: ۳ساله، ۶: ۵ساله)
            "INSERT INTO prices(type, price, discount_percent) VALUES (5, 12000000, 0)",
            "INSERT INTO prices(type, price, discount_percent) VALUES (1, 20000000, 0)",
            "INSERT INTO prices(type, price, discount_percent) VALUES (2, 36000000, 0)",
            "INSERT INTO prices(type, price, discount_percent) VALUES (3, 50000000, 0)",
            "INSERT INTO prices(type, price, discount_percent) VALUES (6, 80000000, 0)"
    };

    /** مانده‌ی پورسانت ویزیتور: [کل پورسانت لایسنس‌های معتبر, کل پرداخت‌شده] */
    static final String Q_VISITOR_BALANCE =
            "SELECT "
                    + "(SELECT COALESCE(SUM(commission_amount),0) FROM licenses WHERE visitor_id=? AND void=0),"
                    + "(SELECT COALESCE(SUM(amount),0) FROM payouts WHERE visitor_id=?)";

    /** [مبلغ نهایی لایسنس‌های معتبر مشتری, کل دریافتی از او] */
    static final String Q_CUSTOMER_BALANCE =
            "SELECT "
                    + "(SELECT COALESCE(SUM(final_price),0) FROM licenses WHERE customer_id=? AND void=0),"
                    + "(SELECT COALESCE(SUM(amount),0) FROM payments WHERE customer_id=?)";

    private Schema() {
    }
}
