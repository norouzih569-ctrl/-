package com.barber.admin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** پایگاه داده‌ی برنامه‌ی مدیریتی. تاریخ‌ها میلادی yyyy-MM-dd. */
public final class Db extends SQLiteOpenHelper {
    private static final String NAME = "license_admin.db";
    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private Db(Context ctx) {
        super(ctx, NAME, null, Schema.VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String sql : Schema.CREATE) db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // نسخه‌ی ۱؛ مهاجرت‌های بعدی اینجا اضافه می‌شود.
    }

    private static String now() {
        return JalaliCalendar.todayGregorianString();
    }

    private static String s(Cursor c, String col) {
        String v = c.getString(c.getColumnIndexOrThrow(col));
        return v == null ? "" : v;
    }

    private static long l(Cursor c, String col) {
        return c.getLong(c.getColumnIndexOrThrow(col));
    }

    private static double d(Cursor c, String col) {
        return c.getDouble(c.getColumnIndexOrThrow(col));
    }

    private static int i(Cursor c, String col) {
        return c.getInt(c.getColumnIndexOrThrow(col));
    }

    // ================= مشتریان =================

    private static Models.Customer toCustomer(Cursor c) {
        Models.Customer x = new Models.Customer();
        x.id = l(c, "id");
        x.shopName = s(c, "shop_name");
        x.ownerName = s(c, "owner_name");
        x.mobile = s(c, "mobile");
        x.phone = s(c, "phone");
        x.city = s(c, "city");
        x.address = s(c, "address");
        x.nationalId = s(c, "national_id");
        x.economicCode = s(c, "economic_code");
        x.postalCode = s(c, "postal_code");
        x.visitorId = c.isNull(c.getColumnIndexOrThrow("visitor_id")) ? 0 : l(c, "visitor_id");
        x.metDate = s(c, "met_date");
        x.notes = s(c, "notes");
        x.createdAt = s(c, "created_at");
        return x;
    }

    private static ContentValues cv(Models.Customer x) {
        ContentValues v = new ContentValues();
        v.put("shop_name", x.shopName.trim());
        v.put("owner_name", x.ownerName.trim());
        v.put("mobile", x.mobile.trim());
        v.put("phone", x.phone.trim());
        v.put("city", x.city.trim());
        v.put("address", x.address.trim());
        v.put("national_id", x.nationalId.trim());
        v.put("economic_code", x.economicCode.trim());
        v.put("postal_code", x.postalCode.trim());
        if (x.visitorId > 0) v.put("visitor_id", x.visitorId);
        else v.putNull("visitor_id");
        v.put("met_date", x.metDate);
        v.put("notes", x.notes.trim());
        return v;
    }

    public long insertCustomer(Models.Customer x) {
        ContentValues v = cv(x);
        v.put("created_at", now());
        return getWritableDatabase().insert("customers", null, v);
    }

    public void updateCustomer(Models.Customer x) {
        getWritableDatabase().update("customers", cv(x), "id=?", new String[]{String.valueOf(x.id)});
    }

    /** حذف فقط وقتی مشتری هیچ لایسنس یا پرداختی ندارد؛ در غیر این صورت false */
    public boolean deleteCustomer(long id) {
        SQLiteDatabase db = getWritableDatabase();
        String[] a = {String.valueOf(id)};
        if (count(db, "SELECT COUNT(*) FROM licenses WHERE customer_id=?", a) > 0) return false;
        if (count(db, "SELECT COUNT(*) FROM payments WHERE customer_id=?", a) > 0) return false;
        db.delete("customers", "id=?", a);
        return true;
    }

    public Models.Customer getCustomer(long id) {
        Cursor c = getReadableDatabase().query("customers", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? toCustomer(c) : null;
        } finally {
            c.close();
        }
    }

    /** جستجو در نام آرایشگاه، مالک، موبایل، شهر؛ query خالی = همه */
    public List<Models.Customer> listCustomers(String query) {
        String q = query == null ? "" : Fmt.normalizeDigits(query).trim();
        String sel = null;
        String[] args = null;
        if (!q.isEmpty()) {
            String like = "%" + q.replace("%", "").replace("_", "") + "%";
            sel = "shop_name LIKE ? OR owner_name LIKE ? OR mobile LIKE ? OR phone LIKE ? OR city LIKE ?";
            args = new String[]{like, like, like, like, like};
        }
        Cursor c = getReadableDatabase().query("customers", null, sel, args, null, null, "shop_name COLLATE NOCASE");
        List<Models.Customer> out = new ArrayList<Models.Customer>();
        try {
            while (c.moveToNext()) out.add(toCustomer(c));
        } finally {
            c.close();
        }
        return out;
    }

    // ================= ویزیتورها =================

    private static Models.Visitor toVisitor(Cursor c) {
        Models.Visitor x = new Models.Visitor();
        x.id = l(c, "id");
        x.name = s(c, "name");
        x.mobile = s(c, "mobile");
        x.percent = d(c, "percent");
        x.active = i(c, "active") != 0;
        x.notes = s(c, "notes");
        x.createdAt = s(c, "created_at");
        return x;
    }

    private static ContentValues cv(Models.Visitor x) {
        ContentValues v = new ContentValues();
        v.put("name", x.name.trim());
        v.put("mobile", x.mobile.trim());
        v.put("percent", Pricing.clampPercent(x.percent));
        v.put("active", x.active ? 1 : 0);
        v.put("notes", x.notes.trim());
        return v;
    }

    public long insertVisitor(Models.Visitor x) {
        ContentValues v = cv(x);
        v.put("created_at", now());
        return getWritableDatabase().insert("visitors", null, v);
    }

    public void updateVisitor(Models.Visitor x) {
        getWritableDatabase().update("visitors", cv(x), "id=?", new String[]{String.valueOf(x.id)});
    }

    /** حذف فقط وقتی ویزیتور به مشتری/لایسنس/پرداختی وصل نیست؛ وگرنه false (می‌توان غیرفعالش کرد) */
    public boolean deleteVisitor(long id) {
        SQLiteDatabase db = getWritableDatabase();
        String[] a = {String.valueOf(id)};
        if (count(db, "SELECT COUNT(*) FROM customers WHERE visitor_id=?", a) > 0) return false;
        if (count(db, "SELECT COUNT(*) FROM licenses WHERE visitor_id=?", a) > 0) return false;
        if (count(db, "SELECT COUNT(*) FROM payouts WHERE visitor_id=?", a) > 0) return false;
        db.delete("visitors", "id=?", a);
        return true;
    }

    public Models.Visitor getVisitor(long id) {
        Cursor c = getReadableDatabase().query("visitors", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? toVisitor(c) : null;
        } finally {
            c.close();
        }
    }

    public List<Models.Visitor> listVisitors(boolean onlyActive) {
        Cursor c = getReadableDatabase().query("visitors", null, onlyActive ? "active=1" : null, null, null, null,
                "name COLLATE NOCASE");
        List<Models.Visitor> out = new ArrayList<Models.Visitor>();
        try {
            while (c.moveToNext()) out.add(toVisitor(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** کل پورسانت لایسنس‌های معتبر و کل پرداخت‌شده به ویزیتور */
    public Models.Balance visitorBalance(long visitorId) {
        return balance(Schema.Q_VISITOR_BALANCE, visitorId);
    }

    /** مشتریان معرفی‌شده‌ی یک ویزیتور */
    public List<Models.Customer> listCustomersByVisitor(long visitorId) {
        Cursor c = getReadableDatabase().query("customers", null, "visitor_id=?",
                new String[]{String.valueOf(visitorId)}, null, null, "shop_name COLLATE NOCASE");
        List<Models.Customer> out = new ArrayList<Models.Customer>();
        try {
            while (c.moveToNext()) out.add(toCustomer(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** لایسنس‌های معتبر دارای پورسانت برای یک ویزیتور، از قدیمی به جدید (برای تخصیص پرداخت‌ها) */
    public List<Models.License> listCommissionLicenses(long visitorId) {
        Cursor c = getReadableDatabase().query("licenses", null,
                "visitor_id=? AND void=0 AND commission_amount>0", new String[]{String.valueOf(visitorId)},
                null, null, "issue_date ASC, id ASC");
        List<Models.License> out = new ArrayList<Models.License>();
        try {
            while (c.moveToNext()) out.add(toLicense(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** وضعیت پرداخت پورسانت هر لایسنس ویزیتور */
    public Commission.Result visitorCommissionStatus(long visitorId) {
        List<Models.License> ls = listCommissionLicenses(visitorId);
        List<Models.Payout> ps = listPayouts(visitorId);
        long[] ids = new long[ls.size()];
        long[] com = new long[ls.size()];
        for (int k = 0; k < ls.size(); k++) {
            ids[k] = ls.get(k).id;
            com[k] = ls.get(k).commissionAmount;
        }
        // پرداخت‌ها از قدیمی به جدید
        long[] pl = new long[ps.size()];
        long[] pa = new long[ps.size()];
        for (int k = 0; k < ps.size(); k++) {
            Models.Payout p = ps.get(ps.size() - 1 - k);
            pl[k] = p.licenseId;
            pa[k] = p.amount;
        }
        return Commission.allocate(ids, com, pl, pa);
    }

    // ================= لیست قیمت =================

    /** لیست قیمت به ترتیب مدت (۶ماهه تا ۵ساله) */
    public List<Models.PriceRow> getPrices() {
        Cursor c = getReadableDatabase().query("prices", null, null, null, null, null, null);
        List<Models.PriceRow> out = new ArrayList<Models.PriceRow>();
        try {
            while (c.moveToNext()) {
                Models.PriceRow p = new Models.PriceRow();
                p.type = i(c, "type");
                p.price = l(c, "price");
                p.discountPercent = d(c, "discount_percent");
                out.add(p);
            }
        } finally {
            c.close();
        }
        java.util.Collections.sort(out, new java.util.Comparator<Models.PriceRow>() {
            @Override
            public int compare(Models.PriceRow a, Models.PriceRow b) {
                return LicenseCodec.monthsForType(a.type) - LicenseCodec.monthsForType(b.type);
            }
        });
        return out;
    }

    public Models.PriceRow getPrice(int type) {
        for (Models.PriceRow p : getPrices()) if (p.type == type) return p;
        return null;
    }

    public void setPrice(int type, long price, double discountPercent) {
        ContentValues v = new ContentValues();
        v.put("type", type);
        v.put("price", Math.max(0, price));
        v.put("discount_percent", Pricing.clampPercent(discountPercent));
        getWritableDatabase().insertWithOnConflict("prices", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ================= لایسنس‌ها =================

    private static Models.License toLicense(Cursor c) {
        Models.License x = new Models.License();
        x.id = l(c, "id");
        x.customerId = l(c, "customer_id");
        x.deviceId = s(c, "device_id");
        x.type = i(c, "type");
        x.code = s(c, "code");
        x.issueDate = s(c, "issue_date");
        x.months = i(c, "months");
        x.basePrice = l(c, "base_price");
        x.discountPercent = d(c, "discount_percent");
        x.finalPrice = l(c, "final_price");
        x.visitorId = c.isNull(c.getColumnIndexOrThrow("visitor_id")) ? 0 : l(c, "visitor_id");
        x.commissionPercent = d(c, "commission_percent");
        x.commissionAmount = l(c, "commission_amount");
        x.endDate = s(c, "end_date");
        x.voided = i(c, "void") != 0;
        x.notes = s(c, "notes");
        x.createdAt = s(c, "created_at");
        return x;
    }

    /**
     * ثبت لایسنس صادرشده. مبلغ نهایی و پورسانت همیشه اینجا از روی قیمت پایه، تخفیف و درصد ویزیتور
     * دوباره محاسبه می‌شود تا با مقدار ارسالی ناهماهنگ نباشد. برای کد جابجایی (type 4) مبلغ و پورسانت صفر است.
     */
    public long insertLicense(Models.License x) {
        ContentValues v = new ContentValues();
        v.put("customer_id", x.customerId);
        v.put("device_id", x.deviceId);
        v.put("type", x.type);
        v.put("code", x.code);
        v.put("issue_date", x.issueDate);
        v.put("months", LicenseCodec.monthsForType(x.type));
        boolean transfer = x.type == LicenseCodec.TYPE_TRANSFER;
        long base = transfer ? 0 : Math.max(0, x.basePrice);
        double disc = transfer ? 0 : Pricing.clampPercent(x.discountPercent);
        long fin = Pricing.finalPrice(base, disc);
        double pct = (transfer || x.visitorId <= 0) ? 0 : Pricing.clampPercent(x.commissionPercent);
        v.put("base_price", base);
        v.put("discount_percent", disc);
        v.put("final_price", fin);
        if (x.visitorId > 0 && !transfer) v.put("visitor_id", x.visitorId);
        else v.putNull("visitor_id");
        v.put("commission_percent", pct);
        v.put("commission_amount", Pricing.commission(fin, pct));
        v.put("end_date", x.endDate == null ? "" : x.endDate);
        v.put("notes", x.notes.trim());
        v.put("created_at", now());
        return getWritableDatabase().insert("licenses", null, v);
    }

    public Models.License getLicense(long id) {
        Cursor c = getReadableDatabase().query("licenses", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? toLicense(c) : null;
        } finally {
            c.close();
        }
    }

    /** customerId = 0 یعنی همه؛ جدیدترین اول */
    public List<Models.License> listLicenses(long customerId) {
        Cursor c = getReadableDatabase().query("licenses", null,
                customerId > 0 ? "customer_id=?" : null,
                customerId > 0 ? new String[]{String.valueOf(customerId)} : null,
                null, null, "issue_date DESC, id DESC");
        List<Models.License> out = new ArrayList<Models.License>();
        try {
            while (c.moveToNext()) out.add(toLicense(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** لایسنس ابطال‌شده در مجموع درآمد و پورسانت حساب نمی‌شود، ولی در تاریخچه می‌ماند. */
    public void setLicenseVoid(long id, boolean voided) {
        ContentValues v = new ContentValues();
        v.put("void", voided ? 1 : 0);
        getWritableDatabase().update("licenses", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateLicenseNotes(long id, String notes) {
        ContentValues v = new ContentValues();
        v.put("notes", notes == null ? "" : notes.trim());
        getWritableDatabase().update("licenses", v, "id=?", new String[]{String.valueOf(id)});
    }

    // ================= دریافتی از مشتری =================

    public long addPayment(Models.Payment p) {
        ContentValues v = new ContentValues();
        v.put("customer_id", p.customerId);
        if (p.licenseId > 0) v.put("license_id", p.licenseId);
        else v.putNull("license_id");
        v.put("amount", p.amount);
        v.put("pay_date", p.payDate);
        v.put("method", p.method == null ? "" : p.method.trim());
        v.put("note", p.note == null ? "" : p.note.trim());
        return getWritableDatabase().insert("payments", null, v);
    }

    public void deletePayment(long id) {
        getWritableDatabase().delete("payments", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Models.Payment> listPayments(long customerId) {
        Cursor c = getReadableDatabase().query("payments", null, "customer_id=?",
                new String[]{String.valueOf(customerId)}, null, null, "pay_date DESC, id DESC");
        List<Models.Payment> out = new ArrayList<Models.Payment>();
        try {
            while (c.moveToNext()) {
                Models.Payment p = new Models.Payment();
                p.id = l(c, "id");
                p.customerId = l(c, "customer_id");
                p.licenseId = c.isNull(c.getColumnIndexOrThrow("license_id")) ? 0 : l(c, "license_id");
                p.amount = l(c, "amount");
                p.payDate = s(c, "pay_date");
                p.method = s(c, "method");
                p.note = s(c, "note");
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** مجموع مبلغ لایسنس‌های معتبر مشتری و مجموع دریافتی؛ due() = بدهی */
    public Models.Balance customerBalance(long customerId) {
        return balance(Schema.Q_CUSTOMER_BALANCE, customerId);
    }

    // ================= پرداخت پورسانت =================

    public long addPayout(Models.Payout p) {
        ContentValues v = new ContentValues();
        v.put("visitor_id", p.visitorId);
        if (p.licenseId > 0) v.put("license_id", p.licenseId);
        else v.putNull("license_id");
        v.put("amount", p.amount);
        v.put("pay_date", p.payDate);
        v.put("note", p.note == null ? "" : p.note.trim());
        return getWritableDatabase().insert("payouts", null, v);
    }

    public void deletePayout(long id) {
        getWritableDatabase().delete("payouts", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Models.Payout> listPayouts(long visitorId) {
        Cursor c = getReadableDatabase().query("payouts", null, "visitor_id=?",
                new String[]{String.valueOf(visitorId)}, null, null, "pay_date DESC, id DESC");
        List<Models.Payout> out = new ArrayList<Models.Payout>();
        try {
            while (c.moveToNext()) {
                Models.Payout p = new Models.Payout();
                p.id = l(c, "id");
                p.visitorId = l(c, "visitor_id");
                p.licenseId = c.isNull(c.getColumnIndexOrThrow("license_id")) ? 0 : l(c, "license_id");
                p.amount = l(c, "amount");
                p.payDate = s(c, "pay_date");
                p.note = s(c, "note");
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** همه‌ی دریافتی‌ها و پرداخت‌های پورسانت (برای گزارش) */
    public List<Models.Payment> listAllPayments() {
        Cursor c = getReadableDatabase().query("payments", null, null, null, null, null, "pay_date, id");
        List<Models.Payment> out = new ArrayList<Models.Payment>();
        try {
            while (c.moveToNext()) {
                Models.Payment p = new Models.Payment();
                p.id = l(c, "id");
                p.customerId = l(c, "customer_id");
                p.licenseId = c.isNull(c.getColumnIndexOrThrow("license_id")) ? 0 : l(c, "license_id");
                p.amount = l(c, "amount");
                p.payDate = s(c, "pay_date");
                p.method = s(c, "method");
                p.note = s(c, "note");
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public List<Models.Payout> listAllPayouts() {
        Cursor c = getReadableDatabase().query("payouts", null, null, null, null, null, "pay_date, id");
        List<Models.Payout> out = new ArrayList<Models.Payout>();
        try {
            while (c.moveToNext()) {
                Models.Payout p = new Models.Payout();
                p.id = l(c, "id");
                p.visitorId = l(c, "visitor_id");
                p.licenseId = c.isNull(c.getColumnIndexOrThrow("license_id")) ? 0 : l(c, "license_id");
                p.amount = l(c, "amount");
                p.payDate = s(c, "pay_date");
                p.note = s(c, "note");
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ================= پشتیبان =================

    /** فایل پایگاه داده؛ پیش از کپی، تغییرات WAL داخل فایل اصلی نوشته می‌شود. */
    public java.io.File databaseFileForBackup() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            Cursor c = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            c.close();
        } catch (Exception ignored) {
            // اگر WAL فعال نباشد خطایی مهم نیست
        }
        return new java.io.File(db.getPath());
    }

    /** بعد از جایگزینی فایل پایگاه داده: اتصال بسته می‌شود و دفعه‌ی بعد دوباره باز می‌شود. */
    public static synchronized void closeAndReset() {
        if (instance != null) {
            try {
                instance.close();
            } catch (Exception ignored) {
                // مهم نیست
            }
            instance = null;
        }
    }

    // ================= تنظیمات =================

    public String getSetting(String key, String def) {
        Cursor c = getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key=?", new String[]{key});
        try {
            return c.moveToFirst() ? c.getString(0) : def;
        } finally {
            c.close();
        }
    }

    public void setSetting(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value == null ? "" : value);
        getWritableDatabase().insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ================= کمکی =================

    private Models.Balance balance(String sql, long id) {
        String a = String.valueOf(id);
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{a, a});
        Models.Balance b = new Models.Balance();
        try {
            if (c.moveToFirst()) {
                b.total = c.getLong(0);
                b.paid = c.getLong(1);
            }
        } finally {
            c.close();
        }
        return b;
    }

    private static long count(SQLiteDatabase db, String sql, String[] args) {
        Cursor c = db.rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }
}
