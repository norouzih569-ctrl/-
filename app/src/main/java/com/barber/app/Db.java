package com.barber.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * لایه دیتابیس (SQLite). ساختار جدول‌ها دقیقاً مثل نسخه ویندوز (salon.db) است،
 * پس فایل salon.db دسکتاپ را می‌توان مستقیم در گوشی بازیابی کرد.
 * تاریخ‌ها به‌صورت میلادی و با فرمت yyyy-MM-dd ذخیره می‌شوند.
 */
public class Db extends SQLiteOpenHelper {

    public static final String NAME = "salon.db";
    private static final int VERSION = 1;

    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) {
            instance = new Db(ctx.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;

    private Db(Context ctx) {
        super(ctx, NAME, null, VERSION);
        this.appContext = ctx;
    }

    // ---------------- مدل‌ها ----------------

    public static class Customer {
        public long id;
        public String name;
        public String phone;
        /** یادداشت آرایشگر درباره‌ی مشتری */
        public String notes;
    }

    public static class Appointment {
        public long id;
        public long customerId;
        public String customerName;
        public String date;
        public String time;
        public String service;
        public double price;
        public String phone;
        /** آیا برای این نوبت پیامک یادآوری به مشتری انتخاب شده است؟ */
        public boolean smsReminder;
        /** آیا پیامک یادآوری قبلاً ارسال شده است؟ */
        public boolean smsSent;
        /** مدت نوبت به دقیقه (برای تشخیص تداخل نوبت‌ها) */
        public int duration = 30;
        /**
         * آیا کار انجام شده و پولش دریافت شده؟ فقط نوبت‌های «انجام‌شده» در درآمد روز و ماه حساب می‌شوند.
         * نوبت‌های قدیمی (و فایل ویندوز) انجام‌شده فرض می‌شوند.
         */
        public boolean done = true;
        /** مبلغ واقعاً دریافت‌شده (اگر null باشد همان price است) */
        public Double finalPrice;

        /** آیا پرداخت این نوبت (روش و بدهی) ثبت می‌شود؟ نوبت‌های قدیمی نه: کاملاً پرداخت‌شده فرض می‌شوند. */
        public boolean tracked = false;
        /** جمع پرداخت‌های ثبت‌شده برای این نوبت */
        public double paid = 0;

        /** مانده‌ی بدهی این نوبت */
        public double debt() {
            return Pay.debt(tracked, done, finalPrice != null ? finalPrice : price, paid);
        }

        /** مبلغی که از این نوبت در درآمد حساب می‌شود: نوبت در انتظار = ۰ */
        public double earned() {
            return done ? (finalPrice != null ? finalPrice : price) : 0;
        }

        /** ساعت پایان نوبت، مثلاً 10:00 */
        public String endTime() {
            int s = Logic.toMinutes(time);
            return s < 0 ? "" : Logic.fromMinutes(s + duration);
        }
    }

    public static class Expense {
        public long id;
        public String date;
        public String description;
        public double amount;
    }

    public static class Barber {
        public long id;
        public String name;
        public double percent;
    }

    public static class BarberService {
        public long id;
        public String barberName;
        public String date;
        public String service;
        public double price;
        public double barberShare;
        public double salonShare;
    }

    // ---------------- ساخت دیتابیس ----------------

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // حالت ژورنال ساده، تا فایل دیتابیس برای پشتیبان‌گیری یک فایل کامل باشد
        db.disableWriteAheadLogging();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS customers ("
                + "id INTEGER PRIMARY KEY, name TEXT, phone TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS appointments ("
                + "id INTEGER PRIMARY KEY, customer_id INTEGER, date TEXT, time TEXT, "
                + "service TEXT, price REAL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS expenses ("
                + "id INTEGER PRIMARY KEY, date TEXT, description TEXT, amount REAL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS barbers ("
                + "id INTEGER PRIMARY KEY, name TEXT, percent REAL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS barber_services ("
                + "id INTEGER PRIMARY KEY, barber_id INTEGER, date TEXT, service TEXT, "
                + "price REAL, barber_share REAL, salon_share REAL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // فعلاً نسخه‌ی دیگری وجود ندارد
    }

    /** ستون‌های پیامک را (فقط در صورت نبودن) اضافه می‌کند؛ حتی برای فایل salon.db نسخه ویندوز. */
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (db.isReadOnly()) return;
        addColumn(db, "customers", "notes", "TEXT");
        addColumn(db, "appointments", "sms_reminder", "INTEGER DEFAULT 0");
        addColumn(db, "appointments", "sms_sent", "INTEGER DEFAULT 0");
        addColumn(db, "appointments", "duration", "INTEGER DEFAULT 30");
        // نوبت‌های موجود «انجام‌شده» می‌مانند (DEFAULT 1) تا درآمد قبلی تغییر نکند؛ نوبت‌های جدید 0 ثبت می‌شوند
        addColumn(db, "appointments", "done", "INTEGER DEFAULT 1");
        addColumn(db, "appointments", "final_price", "REAL");
        // پرداخت: نوبت‌های قبلی tracked=NULL می‌مانند و «کاملاً پرداخت‌شده» حساب می‌شوند
        addColumn(db, "appointments", "pay_tracked", "INTEGER");
        db.execSQL("CREATE TABLE IF NOT EXISTS payments ("
                + "id INTEGER PRIMARY KEY, appt_id INTEGER, customer_id INTEGER, "
                + "date TEXT, amount REAL, method TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS products ("
                + "id INTEGER PRIMARY KEY, name TEXT, buy_price REAL, sell_price REAL, stock INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS product_sales ("
                + "id INTEGER PRIMARY KEY, product_id INTEGER, name TEXT, date TEXT, qty INTEGER, "
                + "unit_price REAL, unit_cost REAL, method TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS reminders ("
                + "id INTEGER PRIMARY KEY, title TEXT, amount REAL, due TEXT, "
                + "monthly INTEGER, anchor_day INTEGER, lead_days INTEGER)");
        createIndexes(db);
    }

    /** ایندکس‌ها برای سریع ماندن برنامه با حجم زیاد دیتا (چند سال کار)؛ فقط در صورت نبودن ساخته می‌شوند. */
    private static void createIndexes(SQLiteDatabase db) {
        String[] sql = {
                "CREATE INDEX IF NOT EXISTS idx_appt_date ON appointments(date)",
                "CREATE INDEX IF NOT EXISTS idx_appt_cust ON appointments(customer_id, date, done)",
                "CREATE INDEX IF NOT EXISTS idx_exp_date ON expenses(date)",
                "CREATE INDEX IF NOT EXISTS idx_bs_date ON barber_services(date)",
                "CREATE INDEX IF NOT EXISTS idx_bs_barber ON barber_services(barber_id)",
                "CREATE INDEX IF NOT EXISTS idx_cust_name ON customers(name)",
                "CREATE INDEX IF NOT EXISTS idx_psale_date ON product_sales(date)",
                "CREATE INDEX IF NOT EXISTS idx_pay_appt ON payments(appt_id)",
                "CREATE INDEX IF NOT EXISTS idx_pay_date ON payments(date)",
                "CREATE INDEX IF NOT EXISTS idx_pay_cust ON payments(customer_id)"
        };
        for (String q : sql) {
            try {
                db.execSQL(q);
            } catch (Exception ignored) {
                // اگر ساخت ایندکسی شکست خورد، برنامه بدون آن هم درست کار می‌کند
            }
        }
    }

    private static void addColumn(SQLiteDatabase db, String table, String col, String decl) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean found = false;
        try {
            while (c.moveToNext()) {
                if (col.equalsIgnoreCase(c.getString(1))) {
                    found = true;
                    break;
                }
            }
        } finally {
            c.close();
        }
        if (!found) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + col + " " + decl);
        }
    }

    // ---------------- مشتریان ----------------

    private Customer readCustomer(Cursor c) {
        Customer x = new Customer();
        x.id = c.getLong(0);
        x.name = c.getString(1);
        x.phone = c.getString(2);
        x.notes = c.getString(3);
        return x;
    }

    public List<Customer> getCustomers() {
        List<Customer> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, phone, notes FROM customers ORDER BY name", null);
        try {
            while (c.moveToNext()) list.add(readCustomer(c));
        } finally {
            c.close();
        }
        return list;
    }

    public Customer getCustomer(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, phone, notes FROM customers WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readCustomer(c) : null;
        } finally {
            c.close();
        }
    }

    /**
     * شبکه‌ی ایمنی لایسنس: وقتی لایسنس تمام شده (یا ساعت دستکاری شده) هیچ نوشتنی روی دیتابیس انجام نمی‌شود،
     * حتی اگر جایی از رابط کاربری فراموش شده باشد جلویش را بگیرد.
     */
    private boolean writeBlocked() {
        try {
            return LicenseManager.isReadOnly(appContext);
        } catch (Exception e) {
            return false; // خطای خواندن لایسنس نباید داده‌ی کاربر را قفل کند
        }
    }

    public void updateCustomerNotes(long id, String notes) {
        if (writeBlocked()) { return; }
        ContentValues v = new ContentValues();
        v.put("notes", notes);
        getWritableDatabase().update("customers", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** تعداد مراجعات هر مشتری تا امروز: شناسه مشتری ← تعداد */
    public java.util.Map<Long, Integer> visitCounts(String todayIso) {
        java.util.Map<Long, Integer> map = new java.util.HashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT customer_id, COUNT(*) FROM appointments WHERE date <= ? AND COALESCE(done,1)=1 GROUP BY customer_id",
                new String[]{todayIso});
        try {
            while (c.moveToNext()) map.put(c.getLong(0), c.getInt(1));
        } finally {
            c.close();
        }
        return map;
    }

    /** تعداد مراجعات یک مشتری تا امروز (نوبت‌های آینده حساب نمی‌شوند) */
    public int visitCount(long customerId, String todayIso) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM appointments WHERE customer_id=? AND date<=? AND COALESCE(done,1)=1",
                new String[]{String.valueOf(customerId), todayIso});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** همه‌ی نوبت‌های یک مشتری، جدید به قدیم */
    public List<Appointment> getCustomerAppointments(long customerId) {
        List<Appointment> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                APPT_SELECT + "WHERE a.customer_id = ? ORDER BY a.date DESC, a.time DESC",
                new String[]{String.valueOf(customerId)});
        try {
            while (c.moveToNext()) list.add(readAppointment(c));
        } finally {
            c.close();
        }
        return list;
    }

    public long addCustomer(String name, String phone) {
        if (writeBlocked()) { return -1; }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("phone", phone);
        return getWritableDatabase().insert("customers", null, v);
    }

    public void updateCustomer(long id, String name, String phone) {
        if (writeBlocked()) { return; }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("phone", phone);
        getWritableDatabase().update("customers", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** حذف مشتری و نوبت‌هایش؛ شناسه نوبت‌های حذف‌شده را برمی‌گرداند (برای لغو یادآورها). */
    public List<Long> deleteCustomer(long id) {
        if (writeBlocked()) { return new ArrayList<>(); }
        List<Long> removed = new ArrayList<>();
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM appointments WHERE customer_id=?",
                new String[]{String.valueOf(id)});
        try {
            while (c.moveToNext()) removed.add(c.getLong(0));
        } finally {
            c.close();
        }
        db.beginTransaction();
        try {
            db.delete("payments", "customer_id=?", new String[]{String.valueOf(id)});
            db.delete("appointments", "customer_id=?", new String[]{String.valueOf(id)});
            db.delete("customers", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return removed;
    }

    // ---------------- نوبت‌ها ----------------

    private Appointment readAppointment(Cursor c) {
        Appointment a = new Appointment();
        a.id = c.getLong(0);
        a.customerName = c.getString(1) == null ? "—" : c.getString(1);
        a.time = c.getString(2);
        a.service = c.getString(3);
        a.price = c.getDouble(4);
        a.customerId = c.getLong(5);
        a.date = c.getString(6);
        a.phone = c.getString(7);
        a.smsReminder = !c.isNull(8) && c.getInt(8) != 0;
        a.smsSent = !c.isNull(9) && c.getInt(9) != 0;
        a.duration = (c.isNull(10) || c.getInt(10) <= 0) ? 30 : c.getInt(10);
        a.done = c.isNull(11) || c.getInt(11) != 0;
        a.finalPrice = c.isNull(12) ? null : c.getDouble(12);
        a.tracked = !c.isNull(13) && c.getInt(13) != 0;
        a.paid = c.isNull(14) ? 0 : c.getDouble(14);
        return a;
    }

    /** همه‌ی نوبت‌های یک بازه (برای گزارش ماهانه) */
    public List<Appointment> getAppointmentsBetween(String startIso, String endIso) {
        List<Appointment> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                APPT_SELECT + "WHERE a.date BETWEEN ? AND ? ORDER BY a.date, a.time",
                new String[]{startIso, endIso});
        try {
            while (c.moveToNext()) list.add(readAppointment(c));
        } finally {
            c.close();
        }
        return list;
    }

    public Appointment getAppointmentById(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                APPT_SELECT + "WHERE a.id = ?", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readAppointment(c) : null;
        } finally {
            c.close();
        }
    }

    public void markSmsSent(long id) {
        ContentValues v = new ContentValues();
        v.put("sms_sent", 1);
        getWritableDatabase().update("appointments", v, "id=?", new String[]{String.valueOf(id)});
    }

    private static final String APPT_SELECT =
            "SELECT a.id, c.name, a.time, a.service, a.price, a.customer_id, a.date, "
                    + "c.phone, a.sms_reminder, a.sms_sent, a.duration, a.done, a.final_price, "
                    + "a.pay_tracked, (SELECT SUM(p.amount) FROM payments p WHERE p.appt_id = a.id) "
                    + "FROM appointments a LEFT JOIN customers c ON a.customer_id = c.id ";

    /** نوبت‌های یک روز (با فیلتر اختیاری نام مشتری) */
    public List<Appointment> getAppointments(String isoDate, String nameQuery) {
        List<Appointment> list = new ArrayList<>();
        Cursor c;
        if (nameQuery == null || nameQuery.trim().isEmpty()) {
            c = getReadableDatabase().rawQuery(
                    APPT_SELECT + "WHERE a.date = ? ORDER BY a.time",
                    new String[]{isoDate});
        } else {
            c = getReadableDatabase().rawQuery(
                    APPT_SELECT + "WHERE a.date = ? AND c.name LIKE ? ORDER BY a.time",
                    new String[]{isoDate, "%" + nameQuery.trim() + "%"});
        }
        try {
            while (c.moveToNext()) list.add(readAppointment(c));
        } finally {
            c.close();
        }
        return list;
    }

    /** نوبت‌های از یک تاریخ به بعد (برای زمان‌بندی یادآورها) */
    public List<Appointment> getAppointmentsFrom(String isoDate) {
        List<Appointment> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                APPT_SELECT + "WHERE a.date >= ? ORDER BY a.date, a.time",
                new String[]{isoDate});
        try {
            while (c.moveToNext()) list.add(readAppointment(c));
        } finally {
            c.close();
        }
        return list;
    }

    public boolean appointmentExists(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM appointments WHERE id=?", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() && c.getInt(0) > 0;
        } finally {
            c.close();
        }
    }

    /**
     * اولین نوبتِ هم‌پوشان با بازه‌ی [time, time+duration) در آن روز (به‌جز نوبت exceptId)،
     * یا null اگر آزاد باشد. نوبتی که دقیقاً همان لحظه‌ای شروع شود که قبلی تمام شده تداخل نیست.
     */
    public Appointment findConflict(String date, String time, int duration, long exceptId) {
        int start = Logic.toMinutes(time);
        if (start < 0) return null;
        List<Appointment> day = getAppointments(date, null);
        List<Appointment> others = new ArrayList<>();
        for (Appointment x : day) {
            if (x.id != exceptId) others.add(x);
        }
        int[] starts = new int[others.size()];
        int[] durs = new int[others.size()];
        for (int i = 0; i < others.size(); i++) {
            starts[i] = Logic.toMinutes(others.get(i).time);
            durs[i] = others.get(i).duration;
        }
        int idx = Logic.conflictIndex(start, duration, starts, durs);
        return idx < 0 ? null : others.get(idx);
    }

    /** شناسه نوبت جدید را برمی‌گرداند؛ اگر آن ساعت پر باشد -1. */
    public long addAppointment(long customerId, String date, String time, String service,
                               double price, boolean smsReminder, int duration, boolean done,
                               String method, double paid) {
        if (writeBlocked()) { return -1; }
        SQLiteDatabase db = getWritableDatabase();
        if (findConflict(date, time, duration, -1) != null) return -1;
        ContentValues v = new ContentValues();
        v.put("duration", duration);
        v.put("customer_id", customerId);
        v.put("date", date);
        v.put("time", time);
        v.put("service", service);
        v.put("price", price);
        v.put("sms_reminder", smsReminder ? 1 : 0);
        v.put("sms_sent", 0);
        v.put("done", done ? 1 : 0);
        if (done) v.put("final_price", price); else v.putNull("final_price");
        if (done) v.put("pay_tracked", 1);
        long id = db.insert("appointments", null, v);
        if (id >= 0 && done) insertPayment(db, id, customerId, payDate(date), paid, method);
        return id;
    }

    /** تاریخ ثبت پرداخت اولیه: روز نوبت، ولی هرگز آینده نه */
    static String payDate(String apptDate) {
        String today = JalaliCalendar.todayGregorianString();
        return apptDate != null && apptDate.compareTo(today) < 0 ? apptDate : today;
    }

    private void insertPayment(SQLiteDatabase db, long apptId, long customerId, String date,
                               double amount, String method) {
        if (amount <= 0) return;
        ContentValues p = new ContentValues();
        p.put("appt_id", apptId);
        p.put("customer_id", customerId);
        p.put("date", date);
        p.put("amount", amount);
        p.put("method", method == null ? Pay.CASH : method);
        db.insert("payments", null, p);
    }

    /** در صورت تداخل زمانی false برمی‌گرداند. */
    public boolean updateAppointment(long id, long customerId, String date, String time,
                                     String service, double price, boolean smsReminder, int duration,
                                     boolean done, String method, double paid) {
        if (writeBlocked()) { return false; }
        SQLiteDatabase db = getWritableDatabase();
        if (findConflict(date, time, duration, id) != null) return false;
        Appointment old = getAppointmentById(id);
        // اگر زمان نوبت عوض نشده و پیامک قبلاً رفته، دوباره ارسال نمی‌شود
        boolean keepSent = old != null && old.smsSent && old.smsReminder && smsReminder
                && date.equals(old.date) && time.equals(old.time);
        ContentValues v = new ContentValues();
        v.put("duration", duration);
        v.put("customer_id", customerId);
        v.put("date", date);
        v.put("time", time);
        v.put("service", service);
        v.put("price", price);
        v.put("sms_reminder", smsReminder ? 1 : 0);
        v.put("sms_sent", keepSent ? 1 : 0);
        v.put("done", done ? 1 : 0);
        if (done) v.put("final_price", price); else v.putNull("final_price");
        boolean keepPayments = old != null && old.done && old.tracked && done;
        boolean legacyDone = old != null && old.done && !old.tracked && done;
        db.beginTransaction();
        try {
            if (!done) {
                v.putNull("pay_tracked");
                db.delete("payments", "appt_id=?", new String[]{String.valueOf(id)});
            } else if (!keepPayments && !legacyDone) {
                v.put("pay_tracked", 1);
                db.delete("payments", "appt_id=?", new String[]{String.valueOf(id)});
                insertPayment(db, id, customerId, payDate(date), paid, method);
            } else if (keepPayments) {
                // مشتری نوبت عوض شد: پرداخت‌ها هم به مشتری جدید می‌روند
                ContentValues pc = new ContentValues();
                pc.put("customer_id", customerId);
                db.update("payments", pc, "appt_id=?", new String[]{String.valueOf(id)});
            }
            db.update("appointments", v, "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return true;
    }

    /** ثبت «انجام شد»: مبلغ حساب‌شده در درآمد می‌آید؛ مبلغ پرداخت‌شده (کمتر یا مساوی) با روشش ثبت می‌شود و باقی بدهی است. */
    public void markDone(long id, double amount, String method, double paid) {
        if (writeBlocked()) { return; }
        Appointment a = getAppointmentById(id);
        if (a == null) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("done", 1);
            v.put("final_price", amount);
            v.put("pay_tracked", 1);
            db.update("appointments", v, "id=?", new String[]{String.valueOf(id)});
            db.delete("payments", "appt_id=?", new String[]{String.valueOf(id)});
            insertPayment(db, id, a.customerId, payDate(a.date), paid, method);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** برگرداندن نوبت به حالت «در انتظار» (از درآمد کم می‌شود و پرداخت‌هایش پاک می‌شود). */
    public void markPending(long id) {
        if (writeBlocked()) { return; }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("done", 0);
            v.putNull("final_price");
            v.putNull("pay_tracked");
            db.update("appointments", v, "id=?", new String[]{String.valueOf(id)});
            db.delete("payments", "appt_id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteAppointment(long id) {
        if (writeBlocked()) { return; }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("payments", "appt_id=?", new String[]{String.valueOf(id)});
            db.delete("appointments", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ---------------- پرداخت و بدهی ----------------

    /** عبارت SQL مانده‌ی هر نوبت (فقط نوبت‌های انجام‌شده‌ی ثبت‌پرداخت‌دار) */
    private static final String DEBT_EXPR =
            "MAX(0, COALESCE(a.final_price, a.price) - "
                    + "COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.appt_id = a.id), 0))";
    private static final String DEBT_WHERE =
            "a.pay_tracked = 1 AND COALESCE(a.done,1) = 1";

    /** بدهی کل یک مشتری */
    public double customerDebt(long customerId) {
        return scalar("SELECT SUM(" + DEBT_EXPR + ") FROM appointments a WHERE a.customer_id = ? AND "
                + DEBT_WHERE, new String[]{String.valueOf(customerId)});
    }

    /** بدهی همه‌ی مشتری‌های بدهکار: شناسه → مبلغ */
    public java.util.Map<Long, Double> allDebts() {
        java.util.Map<Long, Double> map = new java.util.HashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT a.customer_id, SUM(" + DEBT_EXPR + ") d FROM appointments a WHERE " + DEBT_WHERE
                        + " GROUP BY a.customer_id HAVING d > 0.5", null);
        try {
            while (c.moveToNext()) map.put(c.getLong(0), c.getDouble(1));
        } finally {
            c.close();
        }
        return map;
    }

    public double totalDebt() {
        double t = 0;
        for (double d : allDebts().values()) t += d;
        return t;
    }

    /** نوبت‌های بدهکار یک مشتری، قدیمی به جدید */
    public List<Appointment> debtAppointments(long customerId) {
        List<Appointment> out = new ArrayList<>();
        for (Appointment a : getCustomerAppointments(customerId)) {
            if (a.debt() > 0) out.add(a);
        }
        java.util.Collections.reverse(out); // getCustomerAppointments جدید به قدیم است
        return out;
    }

    /**
     * ثبت پرداخت بدهی یک مشتری: از قدیمی‌ترین نوبت بدهکار کم می‌شود.
     * @return مبلغی که واقعاً ثبت شد (حداکثر کل بدهی)
     */
    public double payDebt(long customerId, double amount, String method) {
        if (writeBlocked() || amount <= 0) return 0;
        SQLiteDatabase db = getWritableDatabase();
        String today = JalaliCalendar.todayGregorianString();
        double left = amount, applied = 0;
        db.beginTransaction();
        try {
            for (Appointment a : debtAppointments(customerId)) {
                if (left <= 0) break;
                double part = Math.min(left, a.debt());
                insertPayment(db, a.id, customerId, today, part, method);
                left -= part;
                applied += part;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return applied;
    }

    /** ثبت پرداخت برای یک نوبت مشخص (حداکثر به اندازه‌ی بدهی‌اش) */
    public double payAppointment(long apptId, double amount, String method) {
        if (writeBlocked() || amount <= 0) return 0;
        Appointment a = getAppointmentById(apptId);
        if (a == null) return 0;
        double part = Math.min(amount, a.debt());
        if (part <= 0) return 0;
        insertPayment(getWritableDatabase(), apptId, a.customerId,
                JalaliCalendar.todayGregorianString(), part, method);
        return part;
    }

    /** دریافتی‌های یک بازه به تفکیک روش: [نقد, کارت, واریز] */
    public double[] receivedByMethod(String startIso, String endIso) {
        double[] out = new double[Pay.METHODS.length];
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT method, SUM(amount) FROM payments WHERE date BETWEEN ? AND ? GROUP BY method",
                new String[]{startIso, endIso});
        try {
            while (c.moveToNext()) {
                String m = c.getString(0);
                for (int i = 0; i < Pay.METHODS.length; i++) {
                    if (Pay.METHODS[i].equals(m)) out[i] += c.getDouble(1);
                }
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** جمع دریافتی‌های کارتی و واریزی (حساب بانکی) در یک بازه */
    public double bankReceived(String startIso, String endIso) {
        String[] a = {startIso, endIso};
        return scalar("SELECT SUM(amount) FROM payments WHERE method IN ('card','transfer') "
                + "AND date BETWEEN ? AND ?", a)
                + scalar("SELECT SUM(qty * unit_price) FROM product_sales WHERE method IN ('card','transfer') "
                + "AND date BETWEEN ? AND ?", a);
    }

    // ---------------- یادآور پرداخت‌ها (اجاره، قسط، خرید) ----------------

    public static class Reminder {
        public long id;
        public String title;
        public double amount;
        public String due; // میلادی yyyy-MM-dd
        public boolean monthly;
        public int anchorDay;
        public int leadDays = 2;
    }

    private static final String REM_COLS = "id, title, amount, due, monthly, anchor_day, lead_days";

    private static Reminder readReminder(Cursor c) {
        Reminder r = new Reminder();
        r.id = c.getLong(0);
        r.title = c.getString(1);
        r.amount = c.isNull(2) ? 0 : c.getDouble(2);
        r.due = c.getString(3);
        r.monthly = !c.isNull(4) && c.getInt(4) != 0;
        r.anchorDay = c.isNull(5) ? 0 : c.getInt(5);
        r.leadDays = c.isNull(6) ? 2 : c.getInt(6);
        return r;
    }

    public List<Reminder> getReminders() {
        List<Reminder> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + REM_COLS + " FROM reminders ORDER BY due, id", null);
        try {
            while (c.moveToNext()) list.add(readReminder(c));
        } finally {
            c.close();
        }
        return list;
    }

    public Reminder getReminder(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + REM_COLS + " FROM reminders WHERE id=?", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readReminder(c) : null;
        } finally {
            c.close();
        }
    }

    private static ContentValues reminderValues(String title, double amount, String due,
                                                boolean monthly, int anchorDay, int leadDays) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("amount", amount);
        v.put("due", due);
        v.put("monthly", monthly ? 1 : 0);
        v.put("anchor_day", anchorDay);
        v.put("lead_days", leadDays);
        return v;
    }

    public long addReminder(String title, double amount, String due, boolean monthly, int anchorDay, int leadDays) {
        if (writeBlocked()) { return -1; }
        return getWritableDatabase().insert("reminders", null,
                reminderValues(title, amount, due, monthly, anchorDay, leadDays));
    }

    public void updateReminder(long id, String title, double amount, String due, boolean monthly,
                               int anchorDay, int leadDays) {
        if (writeBlocked()) { return; }
        getWritableDatabase().update("reminders", reminderValues(title, amount, due, monthly, anchorDay, leadDays),
                "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteReminder(long id) {
        if (writeBlocked()) { return; }
        getWritableDatabase().delete("reminders", "id=?", new String[]{String.valueOf(id)});
    }

    /**
     * «پرداخت شد»: مبلغ در هزینه‌ها (به تاریخ امروز) ثبت می‌شود.
     * یادآور ماهانه به ماه بعد می‌رود؛ یادآور یک‌بار مصرف حذف می‌شود.
     * @return شناسه‌ی هزینه یا -1
     */
    public long payReminder(long id, double amount) {
        if (writeBlocked()) { return -1; }
        Reminder r = getReminder(id);
        if (r == null) return -1;
        SQLiteDatabase db = getWritableDatabase();
        long exp = -1;
        db.beginTransaction();
        try {
            ContentValues e = new ContentValues();
            e.put("date", JalaliCalendar.todayGregorianString());
            e.put("description", r.title);
            e.put("amount", amount);
            exp = db.insert("expenses", null, e);
            if (r.monthly) {
                ContentValues v = new ContentValues();
                v.put("due", ReminderMath.nextMonthDue(r.due, r.anchorDay));
                db.update("reminders", v, "id=?", new String[]{String.valueOf(id)});
            } else {
                db.delete("reminders", "id=?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return exp;
    }

    /** یادآورهایی که سررسیدشان گذشته یا نزدیک است */
    public List<Reminder> dueReminders() {
        List<Reminder> out = new ArrayList<>();
        String today = JalaliCalendar.todayGregorianString();
        for (Reminder r : getReminders()) {
            if (ReminderMath.isDueSoon(r.due, today, r.leadDays)) out.add(r);
        }
        return out;
    }

    // ---------------- محصولات و فروش ----------------

    public static class Product {
        public long id;
        public String name;
        public double buyPrice;
        public double sellPrice;
        public int stock;
    }

    public static class ProductSale {
        public long id;
        public long productId;
        public String name;
        public String date;
        public int qty;
        public double unitPrice;
        public double unitCost;
        public String method;

        public double total() {
            return qty * unitPrice;
        }

        public double profit() {
            return ProductMath.saleProfit(qty, unitPrice, unitCost);
        }
    }

    public List<Product> getProducts() {
        List<Product> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, buy_price, sell_price, stock FROM products ORDER BY name", null);
        try {
            while (c.moveToNext()) list.add(readProduct(c));
        } finally {
            c.close();
        }
        return list;
    }

    private static Product readProduct(Cursor c) {
        Product p = new Product();
        p.id = c.getLong(0);
        p.name = c.getString(1);
        p.buyPrice = c.getDouble(2);
        p.sellPrice = c.getDouble(3);
        p.stock = c.getInt(4);
        return p;
    }

    public Product getProduct(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, buy_price, sell_price, stock FROM products WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readProduct(c) : null;
        } finally {
            c.close();
        }
    }

    public long addProduct(String name, double buy, double sell, int stock) {
        if (writeBlocked()) { return -1; }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("buy_price", buy);
        v.put("sell_price", sell);
        v.put("stock", Math.max(0, stock));
        return getWritableDatabase().insert("products", null, v);
    }

    public void updateProduct(long id, String name, double buy, double sell, int stock) {
        if (writeBlocked()) { return; }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("buy_price", buy);
        v.put("sell_price", sell);
        v.put("stock", Math.max(0, stock));
        getWritableDatabase().update("products", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** حذف محصول؛ فروش‌های قبلی با نام و قیمت خودشان در گزارش می‌مانند */
    public void deleteProduct(long id) {
        if (writeBlocked()) { return; }
        getWritableDatabase().delete("products", "id=?", new String[]{String.valueOf(id)});
    }

    /** ثبت فروش: موجودی کم می‌شود. false اگر موجودی کافی نباشد. قیمت خرید همان لحظه ثبت می‌شود. */
    public boolean sellProduct(long productId, int qty, double unitPrice, String method, String dateIso) {
        if (writeBlocked()) { return false; }
        Product p = getProduct(productId);
        if (p == null || ProductMath.validateSale(qty, p.stock) != null) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("product_id", productId);
            v.put("name", p.name);
            v.put("date", dateIso);
            v.put("qty", qty);
            v.put("unit_price", unitPrice);
            v.put("unit_cost", p.buyPrice);
            v.put("method", method == null ? Pay.CASH : method);
            db.insert("product_sales", null, v);
            db.execSQL("UPDATE products SET stock = stock - ? WHERE id = ?", new Object[]{qty, productId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return true;
    }

    /** لغو یک فروش: موجودی به محصول برمی‌گردد (اگر محصول هنوز هست) */
    public void deleteProductSale(long saleId) {
        if (writeBlocked()) { return; }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor c = db.rawQuery("SELECT product_id, qty FROM product_sales WHERE id=?",
                    new String[]{String.valueOf(saleId)});
            try {
                if (c.moveToFirst()) {
                    db.execSQL("UPDATE products SET stock = stock + ? WHERE id = ?",
                            new Object[]{c.getInt(1), c.getLong(0)});
                }
            } finally {
                c.close();
            }
            db.delete("product_sales", "id=?", new String[]{String.valueOf(saleId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<ProductSale> getProductSalesBetween(String startIso, String endIso) {
        List<ProductSale> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, product_id, name, date, qty, unit_price, unit_cost, method FROM product_sales "
                        + "WHERE date BETWEEN ? AND ? ORDER BY date DESC, id DESC",
                new String[]{startIso, endIso});
        try {
            while (c.moveToNext()) {
                ProductSale s = new ProductSale();
                s.id = c.getLong(0);
                s.productId = c.getLong(1);
                s.name = c.getString(2);
                s.date = c.getString(3);
                s.qty = c.getInt(4);
                s.unitPrice = c.getDouble(5);
                s.unitCost = c.getDouble(6);
                s.method = c.getString(7);
                list.add(s);
            }
        } finally {
            c.close();
        }
        return list;
    }

    // ---------------- درآمد ----------------

    private double scalar(String sql, String[] args) {
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0.0;
        } finally {
            c.close();
        }
    }

    /** جمع مبلغ نوبت‌های «انجام‌شده» (مبلغ دریافتی؛ در نبودش قیمت نوبت) */
    private static final String EARNED_SUM =
            "SUM(CASE WHEN COALESCE(done,1)=1 THEN COALESCE(final_price, price) ELSE 0 END)";

    public double incomeForDate(String isoDate) {
        return scalar("SELECT " + EARNED_SUM + " FROM appointments WHERE date=?", new String[]{isoDate});
    }

    public double incomeBetween(String startIso, String endIso) {
        return scalar("SELECT " + EARNED_SUM + " FROM appointments WHERE date BETWEEN ? AND ?",
                new String[]{startIso, endIso});
    }

    /** جمع قیمت نوبت‌های «در انتظار» یک روز (هنوز در درآمد حساب نشده) */
    public double pendingForDate(String isoDate) {
        return scalar("SELECT SUM(price) FROM appointments WHERE date=? AND COALESCE(done,1)=0",
                new String[]{isoDate});
    }

    // ---------------- هزینه‌ها ----------------

    private Expense readExpense(Cursor c) {
        Expense e = new Expense();
        e.id = c.getLong(0);
        e.date = c.getString(1);
        e.description = c.getString(2);
        e.amount = c.getDouble(3);
        return e;
    }

    public long addExpense(String date, String description, double amount) {
        if (writeBlocked()) { return -1; }
        ContentValues v = new ContentValues();
        v.put("date", date);
        v.put("description", description);
        v.put("amount", amount);
        return getWritableDatabase().insert("expenses", null, v);
    }

    public void deleteExpense(long id) {
        if (writeBlocked()) { return; }
        getWritableDatabase().delete("expenses", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Expense> getRecentExpenses(int limit) {
        List<Expense> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, date, description, amount FROM expenses ORDER BY date DESC, id DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) list.add(readExpense(c));
        } finally {
            c.close();
        }
        return list;
    }

    public List<Expense> getExpensesBetween(String startIso, String endIso) {
        List<Expense> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, date, description, amount FROM expenses "
                        + "WHERE date BETWEEN ? AND ? ORDER BY date",
                new String[]{startIso, endIso});
        try {
            while (c.moveToNext()) list.add(readExpense(c));
        } finally {
            c.close();
        }
        return list;
    }

    // ---------------- آرایشگران درصدی ----------------

    public long addBarber(String name, double percent) {
        if (writeBlocked()) { return -1; }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("percent", percent);
        return getWritableDatabase().insert("barbers", null, v);
    }

    public List<Barber> getBarbers() {
        List<Barber> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, percent FROM barbers ORDER BY name", null);
        try {
            while (c.moveToNext()) {
                Barber b = new Barber();
                b.id = c.getLong(0);
                b.name = c.getString(1);
                b.percent = c.getDouble(2);
                list.add(b);
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** حذف آرایشگر و سرویس‌های ثبت‌شده‌اش */
    public void deleteBarber(long id) {
        if (writeBlocked()) { return; }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("barber_services", "barber_id=?", new String[]{String.valueOf(id)});
            db.delete("barbers", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * ثبت سرویس آرایشگر درصدی.
     * مثل نسخه ویندوز: سهم سالن = قیمت × درصد ÷ ۱۰۰ و سهم آرایشگر = بقیه.
     * خروجی: {سهم آرایشگر، سهم سالن}
     */
    public double[] addBarberService(long barberId, String date, String service, double price) {
        if (writeBlocked()) { return new double[]{0, 0}; }
        SQLiteDatabase db = getWritableDatabase();
        double percent = scalar("SELECT percent FROM barbers WHERE id=?",
                new String[]{String.valueOf(barberId)});
        double salonShare = price * (percent / 100.0);
        double barberShare = price - salonShare;
        ContentValues v = new ContentValues();
        v.put("barber_id", barberId);
        v.put("date", date);
        v.put("service", service);
        v.put("price", price);
        v.put("barber_share", barberShare);
        v.put("salon_share", salonShare);
        db.insert("barber_services", null, v);
        return new double[]{barberShare, salonShare};
    }

    private BarberService readBarberService(Cursor c) {
        BarberService s = new BarberService();
        s.id = c.getLong(0);
        s.barberName = c.getString(1) == null ? "—" : c.getString(1);
        s.date = c.getString(2);
        s.service = c.getString(3);
        s.price = c.getDouble(4);
        s.barberShare = c.getDouble(5);
        s.salonShare = c.getDouble(6);
        return s;
    }

    private static final String BS_SELECT =
            "SELECT bs.id, b.name, bs.date, bs.service, bs.price, bs.barber_share, bs.salon_share "
                    + "FROM barber_services bs LEFT JOIN barbers b ON bs.barber_id = b.id ";

    public List<BarberService> getRecentBarberServices(int limit) {
        List<BarberService> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                BS_SELECT + "ORDER BY bs.date DESC, bs.id DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) list.add(readBarberService(c));
        } finally {
            c.close();
        }
        return list;
    }

    public List<BarberService> getBarberServicesBetween(String startIso, String endIso) {
        List<BarberService> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                BS_SELECT + "WHERE bs.date BETWEEN ? AND ? ORDER BY bs.date",
                new String[]{startIso, endIso});
        try {
            while (c.moveToNext()) list.add(readBarberService(c));
        } finally {
            c.close();
        }
        return list;
    }

    public void deleteBarberService(long id) {
        if (writeBlocked()) { return; }
        getWritableDatabase().delete("barber_services", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------------- پشتیبان‌گیری و بازیابی ----------------

    public File getDbFile() {
        return appContext.getDatabasePath(NAME);
    }

    /** فایل دیتابیس را به یک OutputStream کپی می‌کند. */
    public void exportTo(OutputStream out) throws IOException {
        getWritableDatabase(); // مطمئن می‌شویم فایل ساخته شده است
        InputStream in = new FileInputStream(getDbFile());
        try {
            copy(in, out);
        } finally {
            in.close();
        }
    }

    /** بررسی می‌کند فایل یک دیتابیس معتبر برنامه باشد. */
    public static boolean isValidBackup(File f) {
        SQLiteDatabase d = null;
        Cursor c = null;
        try {
            d = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            c = d.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
            Set<String> names = new HashSet<>();
            while (c.moveToNext()) names.add(c.getString(0));
            return names.contains("customers") && names.contains("appointments");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
            if (d != null) d.close();
        }
    }

    /** جایگزینی دیتابیس فعلی با یک فایل معتبر. */
    public synchronized void replaceWith(File src) throws IOException {
        if (writeBlocked()) { throw new IOException("read-only license"); }
        close();
        File dbFile = getDbFile();
        new File(dbFile.getPath() + "-wal").delete();
        new File(dbFile.getPath() + "-shm").delete();
        new File(dbFile.getPath() + "-journal").delete();
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dbFile);
        try {
            copy(in, out);
        } finally {
            in.close();
            out.close();
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }
}
