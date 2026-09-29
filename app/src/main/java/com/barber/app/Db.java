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
    }

    public static class Appointment {
        public long id;
        public long customerId;
        public String customerName;
        public String date;
        public String time;
        public String service;
        public double price;
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

    // ---------------- مشتریان ----------------

    public List<Customer> getCustomers() {
        List<Customer> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, phone FROM customers ORDER BY name", null);
        try {
            while (c.moveToNext()) {
                Customer x = new Customer();
                x.id = c.getLong(0);
                x.name = c.getString(1);
                x.phone = c.getString(2);
                list.add(x);
            }
        } finally {
            c.close();
        }
        return list;
    }

    public long addCustomer(String name, String phone) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("phone", phone);
        return getWritableDatabase().insert("customers", null, v);
    }

    public void updateCustomer(long id, String name, String phone) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("phone", phone);
        getWritableDatabase().update("customers", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** حذف مشتری و نوبت‌هایش؛ شناسه نوبت‌های حذف‌شده را برمی‌گرداند (برای لغو یادآورها). */
    public List<Long> deleteCustomer(long id) {
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
        return a;
    }

    private static final String APPT_SELECT =
            "SELECT a.id, c.name, a.time, a.service, a.price, a.customer_id, a.date "
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

    private boolean timeTaken(SQLiteDatabase db, String date, String time, long exceptId) {
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM appointments WHERE date=? AND time=? AND id<>?",
                new String[]{date, time, String.valueOf(exceptId)});
        try {
            return c.moveToFirst() && c.getInt(0) > 0;
        } finally {
            c.close();
        }
    }

    /** شناسه نوبت جدید را برمی‌گرداند؛ اگر آن ساعت پر باشد -1. */
    public long addAppointment(long customerId, String date, String time, String service, double price) {
        SQLiteDatabase db = getWritableDatabase();
        if (timeTaken(db, date, time, -1)) return -1;
        ContentValues v = new ContentValues();
        v.put("customer_id", customerId);
        v.put("date", date);
        v.put("time", time);
        v.put("service", service);
        v.put("price", price);
        return db.insert("appointments", null, v);
    }

    /** در صورت تداخل زمانی false برمی‌گرداند. */
    public boolean updateAppointment(long id, long customerId, String date, String time,
                                     String service, double price) {
        SQLiteDatabase db = getWritableDatabase();
        if (timeTaken(db, date, time, id)) return false;
        ContentValues v = new ContentValues();
        v.put("customer_id", customerId);
        v.put("date", date);
        v.put("time", time);
        v.put("service", service);
        v.put("price", price);
        db.update("appointments", v, "id=?", new String[]{String.valueOf(id)});
        return true;
    }

    public void deleteAppointment(long id) {
        getWritableDatabase().delete("appointments", "id=?", new String[]{String.valueOf(id)});
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

    public double incomeForDate(String isoDate) {
        return scalar("SELECT SUM(price) FROM appointments WHERE date=?", new String[]{isoDate});
    }

    public double incomeBetween(String startIso, String endIso) {
        return scalar("SELECT SUM(price) FROM appointments WHERE date BETWEEN ? AND ?",
                new String[]{startIso, endIso});
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
        ContentValues v = new ContentValues();
        v.put("date", date);
        v.put("description", description);
        v.put("amount", amount);
        return getWritableDatabase().insert("expenses", null, v);
    }

    public void deleteExpense(long id) {
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
