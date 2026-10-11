package com.barber.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * هسته‌ی مدیریت لایسنس روی یک «انبار کلید-مقدار» ساده (در اندروید: SharedPreferences).
 * بدون وابستگی به اندروید، تا کامل قابل تست باشد.
 *
 * مقدارهای حساس (شروع/پایان لایسنس، شروع آزمایشی، آخرین روز استفاده) امضا می‌شوند و به شناسه‌ی دستگاه گره خورده‌اند؛
 * پس دستکاری فایل ذخیره یا کپی‌شدن آن روی گوشی دیگر (مثلاً با بازیابی پشتیبان گوگل) لایسنس را معتبر نمی‌کند.
 */
public final class LicenseCore {

    public interface Store {
        long getLong(String key, long def);

        void putLong(String key, long value);

        String getString(String key, String def);

        void putString(String key, String value);
    }

    public static final class Outcome {
        public boolean ok;
        public String message;
        public long start;
        public long end;
        public int type;
    }

    public static final class History {
        public long onDay;
        public int type;
        public long start;
        public long end;
    }

    private static final int MAX_USED = 60;
    private static final int MAX_HISTORY = 30;
    private static final long INVALID = -1;

    private final Store st;
    private final String secret;
    private final String deviceId;

    public LicenseCore(Store store, String secret, String deviceId) {
        this.st = store;
        this.secret = secret;
        this.deviceId = LicenseCodec.normalizeDevice(deviceId);
    }

    // ---------------- مقدارهای امضاشده ----------------

    private String sig(String key, long value) {
        return LicenseCodec.signature(secret, deviceId + "|" + key + "|" + value);
    }

    private void writeSigned(String key, long value) {
        st.putLong(key, value);
        st.putString(key + "_s", sig(key, value));
    }

    /** ۰ = ثبت نشده، -۱ = امضا نامعتبر (دستکاری یا گوشی دیگر) */
    private long readSigned(String key) {
        long v = st.getLong(key, 0);
        if (v == 0) return 0;
        return sig(key, v).equals(st.getString(key + "_s", "")) ? v : INVALID;
    }

    // ---------------- شروع برنامه ----------------

    /** هر بار برنامه باز می‌شود صدا زده شود: شروع دوره‌ی آزمایشی را ثبت و «آخرین روز استفاده» را به‌روز می‌کند. */
    public void onAppStart(long today) {
        long trial = readSigned("trial");
        if (trial == 0) {
            writeSigned("trial", today);                       // اولین اجرا
        } else if (trial == INVALID) {
            writeSigned("trial", today - 1000);                // دستکاری: آزمایشی مصرف‌شده حساب می‌شود
        }
        long ls = readSigned("lic_start");
        long le = readSigned("lic_end");
        if (ls == INVALID || le == INVALID) {                  // لایسنس دستکاری‌شده یا مال گوشی دیگر
            st.putLong("lic_start", 0);
            st.putLong("lic_end", 0);
        }
        long seen = readSigned("seen");
        if (seen == INVALID || seen == 0 || today > seen) writeSigned("seen", today);
    }

    public LicenseLogic.State state(long today) {
        long trial = readSigned("trial");
        if (trial == INVALID) trial = 1;                        // خیلی قدیمی = تمام‌شده
        long le = readSigned("lic_end");
        long ls = readSigned("lic_start");
        if (le == INVALID || ls == INVALID) le = 0;
        long seen = readSigned("seen");
        if (seen == INVALID) seen = 0;
        return LicenseLogic.evaluate(today, trial, le, seen);
    }

    public long licStart() {
        long v = readSigned("lic_start");
        return v > 0 ? v : 0;
    }

    public long licEnd() {
        long v = readSigned("lic_end");
        return v > 0 ? v : 0;
    }

    public long trialStart() {
        long v = readSigned("trial");
        return v > 0 ? v : 0;
    }

    /** نوع آخرین کد فعال‌شده (۱ تا ۴) یا ۰ */
    public int lastType() {
        return (int) st.getLong("type", 0);
    }

    // ---------------- جلوگیری از حدس زدن ----------------

    private static int lockoutSeconds(int failures) {
        if (failures < 5) return 0;
        long s = 30L << Math.min(failures - 5, 10);
        return (int) Math.min(s, 900L);
    }

    public int lockRemainingSeconds(long nowMillis) {
        long left = st.getLong("lock_until", 0) - nowMillis;
        return left > 0 ? (int) ((left + 999) / 1000) : 0;
    }

    private Outcome fail(String msg) {
        Outcome o = new Outcome();
        o.ok = false;
        o.message = msg;
        return o;
    }

    // ---------------- فعال‌سازی ----------------

    public Outcome activate(String rawCode, long today, long nowMillis) {
        int wait = lockRemainingSeconds(nowMillis);
        if (wait > 0) {
            return fail("تلاش‌های ناموفق زیاد بوده؛ " + wait + " ثانیه دیگر دوباره امتحان کنید.");
        }
        if (state(today).status == LicenseLogic.Status.TAMPERED) {
            return fail("ساعت یا تاریخ گوشی با آخرین استفاده‌ی برنامه نمی‌خواند. تاریخ را درست کنید و دوباره تلاش کنید.");
        }
        String code = LicenseCodec.normalizeCode(rawCode);
        if (code.length() != 12) {
            return fail("کد باید ۱۲ کاراکتر باشد (مثل K7M2-9QX4-TH5R).");
        }
        String used = st.getString("used", "");
        for (String u : used.split("\n")) {
            if (u.equals(code)) return fail("این کد قبلاً استفاده شده است.");
        }
        LicenseCodec.Decoded d = LicenseCodec.decode(secret, deviceId, code);
        if (d == null) {
            int fails = (int) st.getLong("fails", 0) + 1;
            st.putLong("fails", fails);
            int secs = lockoutSeconds(fails);
            if (secs > 0) st.putLong("lock_until", nowMillis + secs * 1000L);
            String extra = (fails < 5) ? " (" + (5 - fails) + " تلاش تا قفل موقت)" : "";
            return fail("کد نامعتبر است یا برای این گوشی صادر نشده است." + extra);
        }
        LicenseLogic.Activation a = LicenseLogic.activate(d, today, licStart(), licEnd());
        switch (a.result) {
            case ISSUED_IN_FUTURE:
                return fail("این کد برای تاریخ آینده صادر شده است؛ ساعت و تاریخ گوشی را بررسی کنید.");
            case WINDOW_PASSED:
                return fail("مهلت فعال‌سازی این کد (" + LicenseLogic.ACTIVATION_WINDOW_DAYS
                        + " روز از صدور) گذشته است؛ کد جدید بگیرید.");
            case TRANSFER_ENDED:
                return fail("تاریخ پایان این کد جابجایی گذشته است.");
            case UNKNOWN_TYPE:
                return fail("نوع کد شناخته نشد؛ برنامه را به‌روز کنید یا با پشتیبانی تماس بگیرید.");
            default:
                break;
        }

        writeSigned("lic_start", a.start);
        writeSigned("lic_end", a.end);
        long seen = readSigned("seen");
        if (seen == INVALID || today > seen) writeSigned("seen", today);
        st.putLong("type", d.type);
        st.putLong("fails", 0);
        st.putLong("lock_until", 0);

        // کدهای استفاده‌شده (برای جلوگیری از استفاده‌ی دوباره)
        Set<String> set = new LinkedHashSet<>();
        for (String u : used.split("\n")) if (!u.isEmpty()) set.add(u);
        set.add(code);
        List<String> list = new ArrayList<>(set);
        while (list.size() > MAX_USED) list.remove(0);
        st.putString("used", String.join("\n", list));

        // سابقه (جدیدترین اول)
        String hist = st.getString("hist", "");
        String line = today + "|" + d.type + "|" + a.start + "|" + a.end;
        st.putString("hist", hist.isEmpty() ? line : line + "\n" + hist);
        trimHistory();

        Outcome o = new Outcome();
        o.ok = true;
        o.message = "لایسنس با موفقیت فعال شد.";
        o.start = a.start;
        o.end = a.end;
        o.type = d.type;
        return o;
    }

    private void trimHistory() {
        String[] lines = st.getString("hist", "").split("\n");
        if (lines.length <= MAX_HISTORY) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_HISTORY; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        st.putString("hist", sb.toString());
    }

    public List<History> history() {
        List<History> out = new ArrayList<>();
        for (String line : st.getString("hist", "").split("\n")) {
            if (line.isEmpty()) continue;
            String[] p = line.split("\\|");
            if (p.length != 4) continue;
            try {
                History h = new History();
                h.onDay = Long.parseLong(p[0]);
                h.type = Integer.parseInt(p[1]);
                h.start = Long.parseLong(p[2]);
                h.end = Long.parseLong(p[3]);
                out.add(h);
            } catch (NumberFormatException ignored) {
                // سطر خراب نادیده گرفته می‌شود
            }
        }
        return out;
    }
}
