package com.barber.app;

/** منطق خالص (بدون وابستگی به اندروید) تا بتوان جداگانه تست کرد. */
public final class Logic {

    private Logic() {
    }

    /** فقط ارقام (ارقام فارسی/عربی هم به انگلیسی تبدیل می‌شوند) */
    public static String digitsOnly(String s) {
        String n = Fmt.normalizeDigits(s == null ? "" : s);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    /** آیا این شماره برای ارسال پیامک قابل قبول است؟ (حداقل ۱۰ رقم) */
    public static boolean isUsablePhone(String raw) {
        return digitsOnly(raw).length() >= 10;
    }

    public static String fillTemplate(String tpl, String name, String date, String time,
                                      String service, String salon) {
        return tpl.replace("{name}", name == null ? "" : name)
                .replace("{date}", date == null ? "" : date)
                .replace("{time}", time == null ? "" : time)
                .replace("{service}", service == null ? "" : service)
                .replace("{salon}", salon == null ? "" : salon);
    }

    // ---------------- ساعت و مدت نوبت ----------------

    /** حالت‌های ساعت نوبت: ساعتی، نیم‌ساعتی، آزاد (هر دقیقه) */
    public static final int MODE_HOURLY = 60;
    public static final int MODE_HALF = 30;
    public static final int MODE_FREE = 0;

    /** "09:30" را به دقیقه از نیمه‌شب تبدیل می‌کند؛ نامعتبر = -1 */
    public static int toMinutes(String hhmm) {
        try {
            String[] p = hhmm.trim().split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String fromMinutes(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    /** آیا دو بازه‌ی [start, start+dur) هم‌پوشانی دارند؟ (پایان یکی = شروع دیگری، تداخل نیست) */
    public static boolean overlaps(int start1, int dur1, int start2, int dur2) {
        if (dur1 <= 0) dur1 = 1;
        if (dur2 <= 0) dur2 = 1;
        return start1 < start2 + dur2 && start2 < start1 + dur1;
    }

    /** اندیس اولین نوبتِ متداخل با بازه‌ی داده‌شده، یا -1 اگر آزاد باشد */
    public static int conflictIndex(int start, int dur, int[] starts, int[] durs) {
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] >= 0 && overlaps(start, dur, starts[i], durs[i])) return i;
        }
        return -1;
    }

    /** دقیقه‌هایی که در هر حالت قابل انتخاب است: ساعتی {0}، نیم‌ساعتی {0,30}، آزاد 0..59 */
    public static int[] allowedMinutes(int mode) {
        if (mode == MODE_HOURLY) return new int[]{0};
        if (mode == MODE_HALF) return new int[]{0, 30};
        int[] all = new int[60];
        for (int i = 0; i < 60; i++) all[i] = i;
        return all;
    }

    /** نزدیک‌ترین مقدار مجاز به minute */
    public static int snapMinute(int minute, int[] allowed) {
        int best = allowed[0];
        for (int a : allowed) {
            if (Math.abs(a - minute) < Math.abs(best - minute)) best = a;
        }
        return best;
    }

    /**
     * اولین زمان آزاد برای نوبتی به مدت duration دقیقه.
     * در حالت ساعتی/نیم‌ساعتی گام ۶۰/۳۰ دقیقه و در حالت آزاد گام ۵ دقیقه است.
     * اگر تا پایان ساعت کاری جایی نباشد -1.
     */
    public static int firstFreeStart(int workStart, int workEnd, int duration, int mode,
                                     int[] starts, int[] durs) {
        int step = mode == MODE_HOURLY ? 60 : (mode == MODE_HALF ? 30 : 5);
        for (int t = workStart; t < workEnd; t += step) {
            if (conflictIndex(t, duration, starts, durs) < 0) return t;
        }
        return -1;
    }

    // ---------------- چند خدمت در یک نوبت ----------------

    /** جداکننده‌ی نام خدمت‌ها در ستون service (مثلاً «اصلاح + ریش») */
    public static final String SERVICE_SEP = " + ";

    public static String joinServices(java.util.List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (n == null || n.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(SERVICE_SEP);
            sb.append(n.trim());
        }
        return sb.toString();
    }

    /** نام خدمت‌ها را از متن ذخیره‌شده جدا می‌کند؛ نوبت‌های قدیمی (تک‌خدمتی) یک عضو برمی‌گردانند. */
    public static java.util.List<String> splitServices(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;
        for (String part : s.split("\\s*\\+\\s*")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** آمار مراجعات یک مشتری */
    public static final class Stats {
        public int visits;
        public double spent;
        /** آخرین مراجعه‌ی گذشته (yyyy-MM-dd) یا null */
        public String lastVisit;
        /** اندیس نزدیک‌ترین نوبت آینده در آرایه‌های ورودی، یا -1 */
        public int nextIndex = -1;

        public double average() {
            return visits > 0 ? spent / visits : 0;
        }
    }

    /**
     * محاسبه‌ی آمار از لیست نوبت‌های یک مشتری. نوبت‌های امروز و قبل از آن «مراجعه» حساب می‌شوند
     * و نوبت‌های بعد از امروز «نوبت آینده» هستند.
     */
    public static Stats stats(String[] dates, String[] times, double[] prices, String today) {
        Stats s = new Stats();
        for (int i = 0; i < dates.length; i++) {
            String d = dates[i];
            if (d == null) continue;
            if (d.compareTo(today) <= 0) {
                s.visits++;
                s.spent += prices[i];
                if (s.lastVisit == null || d.compareTo(s.lastVisit) > 0) s.lastVisit = d;
            } else if (s.nextIndex < 0) {
                s.nextIndex = i;
            } else {
                int c = d.compareTo(dates[s.nextIndex]);
                if (c < 0 || (c == 0 && times[i] != null && times[s.nextIndex] != null
                        && times[i].compareTo(times[s.nextIndex]) < 0)) {
                    s.nextIndex = i;
                }
            }
        }
        return s;
    }

    /** اگر تعداد مراجعات قبلی visits باشد، آیا مراجعه‌ی بعدی جایزه دارد؟ (هر threshold‌امین مراجعه) */
    public static boolean isRewardVisit(int visits, int threshold) {
        return threshold > 0 && (visits + 1) % threshold == 0;
    }

    /** چند مراجعه‌ی دیگر (با احتساب خود مراجعه‌ی جایزه‌دار) تا جایزه‌ی بعدی؛ ۱ یعنی مراجعه‌ی بعدی جایزه دارد */
    public static int visitsUntilReward(int visits, int threshold) {
        if (threshold <= 0) return 0;
        return threshold - (visits % threshold);
    }

    // ---------------- قفل برنامه ----------------

    public static final int PIN_MIN = 4;
    public static final int PIN_MAX = 8;

    /**
     * رمز معتبر: فقط ارقام (فارسی/عربی هم قبول و به انگلیسی تبدیل می‌شود)، ۴ تا ۸ رقم.
     * هر کاراکتر دیگری (فاصله، حرف، ویرگول…) = نامعتبر (null).
     */
    public static String normalizePin(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (c >= '\u06F0' && c <= '\u06F9') sb.append((char) ('0' + (c - '\u06F0')));
            else if (c >= '\u0660' && c <= '\u0669') sb.append((char) ('0' + (c - '\u0660')));
            else return null;
        }
        int n = sb.length();
        return (n >= PIN_MIN && n <= PIN_MAX) ? sb.toString() : null;
    }

    /** نمک تصادفی ۱۶ بایتی به‌صورت hex */
    public static String newSalt() {
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(java.util.Locale.US, "%02x", x));
        return sb.toString();
    }

    /** هش رمز: SHA-256 با نمک، ۲۰هزار دور (کند کردن حدس‌زدن) */
    public static String hashPin(String salt, String pin) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = (salt + ":" + pin).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (int i = 0; i < 20000; i++) {
                md.reset();
                md.update(d);
                md.update(salt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                d = md.digest();
            }
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format(java.util.Locale.US, "%02x", x));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** مقایسه‌ی رمز واردشده با هش ذخیره‌شده (زمان ثابت) */
    public static boolean verifyPin(String salt, String pin, String expectedHash) {
        if (salt == null || pin == null || expectedHash == null) return false;
        return java.security.MessageDigest.isEqual(
                hashPin(salt, pin).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * مدت قفل شدن بعد از failures بار رمز اشتباه پشت‌سرهم (ثانیه):
     * ۴ بار اول بدون محدودیت، از بار پنجم ۳۰ ثانیه و بعد هر بار دو برابر تا سقف ۱۵ دقیقه.
     */
    public static int lockoutSeconds(int failures) {
        if (failures < 5) return 0;
        long s = 30L << Math.min(failures - 5, 10);
        return (int) Math.min(s, 900L);
    }

    /**
     * زمان ارسال پیامک یادآوری: leadHours ساعت قبل از نوبت.
     * اگر به آن زمان رسیده‌ایم (نوبت نزدیک است) به‌زودی ارسال می‌شود؛ اگر نوبت گذشته باشد -1.
     */
    public static long smsTriggerTime(long apptMs, long nowMs, int leadHours) {
        if (apptMs <= nowMs) return -1;
        long t = apptMs - leadHours * 3600_000L;
        return Math.max(t, nowMs + 2000L);
    }
}
