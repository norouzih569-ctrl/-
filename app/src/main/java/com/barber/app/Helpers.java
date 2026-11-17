package com.barber.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// ---- کلاس‌های کمکی کوچک (برای کم‌شدن تعداد فایل‌ها یک‌جا جمع شده‌اند) ----

/** هر تب برنامه باید بتواند اطلاعاتش را دوباره از دیتابیس بخواند. */
interface Refreshable {
    void refresh();
}

/** محاسبات خالص فروش محصول (قابل تست) */
final class ProductMath {
    private ProductMath() {
    }

    /** سود هر واحد */
    public static double unitProfit(double sell, double buy) {
        return sell - buy;
    }

    /** درصد سود نسبت به قیمت خرید؛ اگر خرید صفر باشد 0 */
    public static double markupPercent(double sell, double buy) {
        return buy <= 0 ? 0 : (sell - buy) * 100.0 / buy;
    }

    public static double saleProfit(int qty, double unitPrice, double unitCost) {
        return qty * (unitPrice - unitCost);
    }

    /** null اگر درست است، وگرنه پیام خطا */
    public static String validateSale(int qty, int stock) {
        if (qty <= 0) return "تعداد باید حداقل ۱ باشد";
        if (qty > stock) return "موجودی کافی نیست (موجودی: " + stock + ")";
        return null;
    }
}

/** محاسبات خالص نمودار و مقایسه‌ی گزارش (قابل تست) */
final class ReportMath {
    private ReportMath() {
    }

    /** جمع مبلغ هر روز ماه؛ days[i] شماره‌ی روز (۱ تا monthLen) */
    public static double[] dailyTotals(int[] days, double[] amounts, int monthLen) {
        double[] out = new double[monthLen];
        for (int i = 0; i < days.length; i++) {
            int d = days[i];
            if (d >= 1 && d <= monthLen) out[d - 1] += amounts[i];
        }
        return out;
    }

    /** درصد تغییر نسبت به ماه قبل؛ null اگر ماه قبل صفر بود (مقایسه معنی ندارد) */
    public static Double changePercent(double cur, double prev) {
        if (prev <= 0) return null;
        return (cur - prev) * 100.0 / prev;
    }

    /** اندیس بیشترین مقدار؛ -1 اگر همه صفر */
    public static int maxIndex(double[] v) {
        int best = -1;
        double m = 0;
        for (int i = 0; i < v.length; i++) {
            if (v[i] > m) {
                m = v[i];
                best = i;
            }
        }
        return best;
    }

    public static String describeChange(double cur, double prev) {
        Double p = changePercent(cur, prev);
        if (p == null) return cur > 0 ? "ماه قبل درآمدی ثبت نشده بود" : "";
        long r = Math.round(Math.abs(p));
        if (r == 0) return "تقریباً برابر با ماه قبل";
        return (p > 0 ? "▲ " : "▼ ") + r + "٪ " + (p > 0 ? "بیشتر" : "کمتر") + " از ماه قبل";
    }
}

/** منطق خالص سقف سالانه‌ی درآمد کارتی/واریزی (قابل تست) */
final class TaxCap {
    public static final int OK = 0, WARN = 1, OVER = 2;

    private TaxCap() {
    }

    /** cap <= 0 یعنی غیرفعال */
    public static int level(double received, double cap, int warnPercent) {
        if (cap <= 0) return OK;
        if (received >= cap) return OVER;
        if (received >= cap * warnPercent / 100.0) return WARN;
        return OK;
    }

    public static double percentUsed(double received, double cap) {
        return cap <= 0 ? 0 : received * 100.0 / cap;
    }
}

/** منطق خالص لیست خدمات (بدون وابستگی به اندروید؛ قابل تست) */
final class ServiceList {

    public static final int MAX_NAME = 30;
    public static final int MAX_COUNT = 40;

    private ServiceList() {
    }

    public static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(n);
        }
        return sb.toString();
    }

    public static List<String> parse(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split("\n")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** نام تمیز: فاصله‌های اضافه و خط جدید حذف می‌شود */
    public static String clean(String name) {
        if (name == null) return "";
        return name.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * @param self نام فعلی هنگام ویرایش (یا null هنگام افزودن)
     * @return متن خطا یا null اگر درست است
     */
    public static String validateName(String name, List<String> existing, String self) {
        String n = clean(name);
        if (n.isEmpty()) return "نام خدمت را وارد کنید";
        if (n.length() > MAX_NAME) return "نام خدمت حداکثر " + MAX_NAME + " حرف";
        if (n.indexOf('+') >= 0 || n.indexOf('|') >= 0) return "از + و | در نام استفاده نکنید";
        for (String e : existing) {
            if (e.equals(n) && (self == null || !e.equals(self))) return "این خدمت قبلاً وجود دارد";
        }
        if (self == null && existing.size() >= MAX_COUNT) return "حداکثر " + MAX_COUNT + " خدمت";
        return null;
    }
}

/** روش‌های پرداخت و محاسبه‌ی بدهی (منطق خالص؛ قابل تست) */
final class Pay {

    public static final String CASH = "cash";
    public static final String CARD = "card";
    public static final String TRANSFER = "transfer";

    public static final String[] METHODS = {CASH, CARD, TRANSFER};
    public static final String[] LABELS = {"💵 نقد", "💳 کارت", "🏦 واریز"};

    private Pay() {
    }

    public static String label(String method) {
        if ("pkg".equals(method)) return "📦 پکیج";
        for (int i = 0; i < METHODS.length; i++) {
            if (METHODS[i].equals(method)) return LABELS[i];
        }
        return "—";
    }

    public static String methodAt(int index) {
        return (index >= 0 && index < METHODS.length) ? METHODS[index] : CASH;
    }

    /**
     * بدهی یک نوبت. نوبت قدیمی (tracked=false) یا در انتظار، بدهی ندارد.
     * @param earned مبلغ حساب‌شده‌ی نوبت (final_price یا price)
     */
    public static double debt(boolean tracked, boolean done, double earned, double paid) {
        if (!tracked || !done) return 0;
        double d = earned - paid;
        return d > 0.5 ? d : 0; // کمتر از نیم تومان = تسویه
    }

    /**
     * مبلغ دریافتی را از متن فیلد می‌خواند. خالی = کل مبلغ.
     * @return null اگر نامعتبر باشد (منفی یا بیشتر از کل)
     */
    public static Double resolvePaid(String fieldText, double total) {
        String t = fieldText == null ? "" : fieldText.trim();
        if (t.isEmpty()) return total;
        Double v = Fmt.parsePrice(t);
        if (v == null || v < 0 || v > total) return null;
        return v;
    }
}

/** ارسال دستی پیامک با برنامه‌ی پیامک گوشی (بدون نیاز به مجوز؛ خود کاربر دکمه‌ی ارسال را می‌زند). */
final class Messenger {

    private Messenger() {
    }

    /** پیج اینستاگرام را باز می‌کند (اول برنامه‌ی اینستاگرام، وگرنه مرورگر). */
    public static void openInstagram(Activity act, String id) {
        String u = AppConfig.cleanInstagram(id);
        if (u.isEmpty()) {
            Ui.toastLong(act, "آیدی اینستاگرام ثبت نشده است");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/_u/" + u));
            i.setPackage("com.instagram.android");
            act.startActivity(i);
        } catch (Exception e) {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/" + u)));
            } catch (Exception e2) {
                Ui.toastLong(act, "برنامه‌ی اینستاگرام یا مرورگر پیدا نشد");
            }
        }
    }

    public static void openSms(Activity act, String phone, String text) {
        String d = Logic.digitsOnly(phone);
        if (d.isEmpty()) {
            Ui.toastLong(act, "شماره‌ی تلفن مشتری ثبت نشده است");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + d));
            i.putExtra("sms_body", text);
            act.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Ui.toastLong(act, "برنامه‌ی پیامک روی گوشی پیدا نشد");
        }
    }
}

/** منطق خالص یادآورهای پرداخت (اجاره، قسط، خرید): تاریخ بعدی و روزهای مانده */
final class ReminderMath {
    private ReminderMath() {
    }

    /** سررسید ماه شمسی بعد، با همان روز (اگر ماه بعد آن روز را ندارد، آخرین روز ماه) */
    public static String nextMonthDue(String dueIso, int anchorDay) {
        int[] j = JalaliCalendar.jalaliOfIso(dueIso);
        int y = j[0], m = j[1] + 1;
        if (m > 12) {
            m = 1;
            y++;
        }
        int d = Math.min(anchorDay > 0 ? anchorDay : j[2], JalaliCalendar.monthLength(y, m));
        return JalaliCalendar.jalaliToGregorianString(y, m, d);
    }

    /** مثبت = روز مانده؛ ۰ = امروز؛ منفی = روز گذشته از سررسید */
    public static long daysLeft(String dueIso, String todayIso) {
        return ChronoUnit.DAYS.between(LocalDate.parse(todayIso), LocalDate.parse(dueIso));
    }

    /** آیا باید در برنامه هشدار داد؟ (گذشته یا در بازه‌ی leadDays روز مانده) */
    public static boolean isDueSoon(String dueIso, String todayIso, int leadDays) {
        return daysLeft(dueIso, todayIso) <= leadDays;
    }

    public static String describePlain(long days) {
        if (days < 0) return (-days) + " روز گذشته";
        if (days == 0) return "امروز";
        if (days == 1) return "فردا";
        return days + " روز دیگر";
    }

    public static String describe(long days) {
        String icon = days < 0 ? "🔴 " : days == 0 ? "🟠 " : days == 1 ? "🟡 " : "";
        return icon + describePlain(days);
    }
}

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

/** عکس مدل مو در پروفایل مشتری: دوربین یا گالری، کوچک‌سازی، ذخیره در فایل جدا */
final class PhotoUi {

    /** مشتری‌ای که عکسش در حال گرفتن/انتخاب است (بین بازگشت از دوربین/گالری نگه داشته می‌شود) */
    static long pendingCustomer = -1;
    static java.io.File pendingCamera;

    private static final int MAX_SIDE = 1024;

    private PhotoUi() {
    }

    interface Opener {
        java.io.InputStream open() throws java.io.IOException;
    }

    static boolean canAdd(MainActivity act, long customerId) {
        if (!act.canWrite()) return false;
        if (Db.get(act).getCustomerPhotos(customerId).size() >= Db.MAX_PHOTOS) {
            Ui.toastLong(act, "حداکثر " + Db.MAX_PHOTOS + " عکس برای هر مشتری. اول یکی را حذف کنید.");
            return false;
        }
        return true;
    }

    static void startCamera(MainActivity act, long customerId) {
        if (!canAdd(act, customerId)) return;
        try {
            java.io.File dir = new java.io.File(act.getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "tmp.jpg");
            pendingCustomer = customerId;
            pendingCamera = f;
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    act, act.getPackageName() + ".fileprovider", f);
            act.cameraLauncher.launch(uri);
        } catch (Exception e) {
            Ui.toastLong(act, "دوربین باز نشد");
        }
    }

    static void startGallery(MainActivity act, long customerId) {
        if (!canAdd(act, customerId)) return;
        pendingCustomer = customerId;
        try {
            act.galleryLauncher.launch("image/*");
        } catch (Exception e) {
            Ui.toastLong(act, "گالری باز نشد");
        }
    }

    static void onCameraResult(final MainActivity act, boolean ok) {
        final java.io.File f = pendingCamera;
        if (!ok || f == null || !f.exists()) {
            reopen(act);
            return;
        }
        process(act, new Opener() {
            @Override
            public java.io.InputStream open() throws java.io.IOException {
                return new java.io.FileInputStream(f);
            }
        }, f);
    }

    static void onGalleryResult(final MainActivity act, final android.net.Uri uri) {
        if (uri == null) {
            reopen(act);
            return;
        }
        process(act, new Opener() {
            @Override
            public java.io.InputStream open() throws java.io.IOException {
                java.io.InputStream in = act.getContentResolver().openInputStream(uri);
                if (in == null) throw new java.io.IOException("no stream");
                return in;
            }
        }, null);
    }

    private static void reopen(MainActivity act) {
        if (pendingCustomer >= 0) CustomerProfile.show(act, pendingCustomer);
    }

    /** خواندن، چرخاندن طبق EXIF، کوچک‌سازی تا ۱۰۲۴ پیکسل و ذخیره (در رشته‌ی پس‌زمینه) */
    private static void process(final MainActivity act, final Opener src, final java.io.File tempToDelete) {
        final long cid = pendingCustomer;
        Ui.toast(act, "در حال ذخیره‌ی عکس…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String saved = null;
                try {
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    java.io.InputStream in = src.open();
                    try {
                        android.graphics.BitmapFactory.decodeStream(in, null, o);
                    } finally {
                        in.close();
                    }
                    int sample = 1;
                    while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
                    android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                    o2.inSampleSize = sample;
                    android.graphics.Bitmap bmp;
                    in = src.open();
                    try {
                        bmp = android.graphics.BitmapFactory.decodeStream(in, null, o2);
                    } finally {
                        in.close();
                    }
                    if (bmp == null) throw new java.io.IOException("decode");
                    int rot = 0;
                    try {
                        in = src.open();
                        try {
                            int ori = new android.media.ExifInterface(in).getAttributeInt(
                                    android.media.ExifInterface.TAG_ORIENTATION,
                                    android.media.ExifInterface.ORIENTATION_NORMAL);
                            if (ori == android.media.ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                            else if (ori == android.media.ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                            else if (ori == android.media.ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
                        } finally {
                            in.close();
                        }
                    } catch (Exception ignored) {
                        // بدون EXIF: چرخش نمی‌دهیم
                    }
                    float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bmp.getWidth(), bmp.getHeight()));
                    android.graphics.Matrix m = new android.graphics.Matrix();
                    m.postRotate(rot);
                    m.postScale(scale, scale);
                    android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(
                            bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                    String name = "p" + cid + "_" + System.currentTimeMillis() + ".jpg";
                    java.io.File dest = Db.get(act).photoFile(name);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
                    try {
                        out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, fos);
                    } finally {
                        fos.close();
                    }
                    long id = Db.get(act).addCustomerPhoto(cid, name,
                            JalaliCalendar.todayGregorianString(), "");
                    if (id < 0) {
                        dest.delete();
                    } else {
                        saved = name;
                    }
                } catch (Throwable t) {
                    saved = null;
                }
                if (tempToDelete != null) tempToDelete.delete();
                final boolean ok = saved != null;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Ui.toast(act, ok ? "عکس ذخیره شد ✓" : "ذخیره‌ی عکس ناموفق بود");
                        reopen(act);
                    }
                });
            }
        }).start();
    }

    /** بندانگشتی: نسخه‌ی کوچک‌شده‌ی فایل (برای لیست) */
    static android.graphics.Bitmap thumb(java.io.File f, int target) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= target) s *= 2;
            android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
            o2.inSampleSize = s;
            return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    /** نمایش بزرگ یک عکس با امکان حذف؛ با بستن، دوباره پروفایل باز می‌شود */
    static void viewer(final MainActivity act, final Db.CustomerPhoto p) {
        final long cid = p.customerId;
        android.widget.LinearLayout box = new android.widget.LinearLayout(act);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 4));
        android.widget.ImageView iv = new android.widget.ImageView(act);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        android.graphics.Bitmap b = thumb(Db.get(act).photoFile(p.file), 1024);
        if (b != null) {
            iv.setImageBitmap(b);
        } else {
            iv.setImageDrawable(null);
        }
        box.addView(iv, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.TextView cap = new android.widget.TextView(act);
        cap.setText("📅 " + JalaliCalendar.gregorianStringToJalali(p.taken)
                + (p.note == null || p.note.isEmpty() ? "" : "\n📝 " + p.note)
                + (b == null ? "\n⚠️ فایل این عکس در دسترس نیست (احتمالاً در پشتیبان نبوده)" : ""));
        cap.setPadding(0, Ui.dp(act, 6), 0, 0);
        box.addView(cap);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                .setTitle("📷 عکس مدل مو")
                .setView(Ui.scroll(box))
                .setPositiveButton("بستن", null)
                .setNeutralButton("🗑 حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        confirmDelete(act, p);
                    }
                })
                .setNegativeButton("✏️ تاریخ و توضیح", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        editMeta(act, p);
                    }
                })
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(android.content.DialogInterface d) {
                        if (!deleting) CustomerProfile.show(act, cid);
                        deleting = false;
                    }
                })
                .show();
    }

    private static boolean deleting = false;

    /** ویرایش تاریخ و توضیح (مثلاً «فید ۲ از کنار، ریش خط‌دار») */
    private static void editMeta(final MainActivity act, final Db.CustomerPhoto p) {
        if (!act.canWrite()) {
            viewer(act, p);
            return;
        }
        deleting = true; // با بسته شدن پنجره‌ی عکس، پروفایل باز نشود؛ خودمان دوباره پنجره‌ی عکس را باز می‌کنیم
        final String[] date = {p.taken};
        android.widget.LinearLayout box = new android.widget.LinearLayout(act);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), 0);
        final android.widget.Button bd = new android.widget.Button(act);
        bd.setText("تاریخ: " + JalaliCalendar.gregorianStringToJalali(date[0]));
        bd.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                JalaliPicker.show(act, JalaliCalendar.jalaliOfIso(date[0]), new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int y, int m, int d) {
                        date[0] = JalaliCalendar.jalaliToGregorianString(y, m, d);
                        bd.setText("تاریخ: " + JalaliCalendar.format(y, m, d));
                    }
                });
            }
        });
        box.addView(bd);
        final android.widget.EditText et = new android.widget.EditText(act);
        et.setHint("توضیح کوتاه (مثلاً فید ۲ از کنار)");
        et.setText(p.note);
        et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        box.addView(et);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                .setTitle("تاریخ و توضیح عکس")
                .setView(Ui.scroll(box))
                .setPositiveButton("ذخیره", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Db db = Db.get(act);
                        db.updateCustomerPhoto(p.id, date[0], et.getText().toString());
                        deleting = false;
                        Db.CustomerPhoto fresh = db.getCustomerPhoto(p.id);
                        viewer(act, fresh == null ? p : fresh);
                    }
                })
                .setNegativeButton("انصراف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        deleting = false;
                        viewer(act, p);
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface d) {
                        deleting = false;
                        viewer(act, p);
                    }
                })
                .show();
    }

    static String mb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f", bytes / 1048576.0);
    }

    /** پشتیبان‌گیری: انتخاب اینکه عکس‌ها هم شامل شوند یا نه (پیش‌فرض خاموش) */
    static void backupDialog(final MainActivity act) {
        long[] r = Db.get(act).photoScan(false);
        long usedFiles = r[3] - r[0];
        long usedBytes = r[4] - r[1];
        android.widget.LinearLayout box = new android.widget.LinearLayout(act);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), 0);
        final android.widget.CheckBox cb = new android.widget.CheckBox(act);
        cb.setText("عکس‌های مدل مو هم در پشتیبان باشد");
        cb.setChecked(AppConfig.getBackupPhotos(act) && usedFiles > 0);
        cb.setEnabled(usedFiles > 0);
        box.addView(cb);
        android.widget.TextView info = new android.widget.TextView(act);
        info.setTextSize(13);
        info.setText(usedFiles > 0
                ? "عکس‌های ذخیره‌شده: " + usedFiles + " فایل، حدود " + mb(usedBytes)
                + " مگابایت. با روشن بودن این گزینه، فایل پشتیبان به‌صورت zip و بزرگ‌تر ذخیره می‌شود. "
                + "این فایل شامل عکس مشتری‌هاست؛ آن را فقط جای امن نگه دارید."
                : "هنوز عکسی ذخیره نکرده‌اید؛ پشتیبان فقط اطلاعات برنامه را دارد.");
        info.setPadding(0, Ui.dp(act, 8), 0, 0);
        box.addView(info);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                .setTitle("💾 پشتیبان‌گیری")
                .setView(Ui.scroll(box))
                .setPositiveButton("پشتیبان‌گیری", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        boolean with = cb.isEnabled() && cb.isChecked();
                        if (cb.isEnabled()) AppConfig.setBackupPhotos(act, with);
                        act.startBackup(with);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    /** پاک‌سازی: فایل‌های عکسِ بی‌صاحب و ردیف‌های عکسی که فایلش نیست */
    static void cleanupDialog(final MainActivity act) {
        long[] r = Db.get(act).photoScan(false);
        String msg = "عکس‌های ذخیره‌شده: " + r[3] + " فایل، " + mb(r[4]) + " مگابایت\n\n"
                + "فایل بی‌صاحب (به هیچ مشتری وصل نیست): " + r[0] + " (" + mb(r[1]) + " مگابایت)\n"
                + "ردیف عکس بدون فایل (مثلاً بعد از بازیابی پشتیبان بدون عکس): " + r[2];
        com.google.android.material.dialog.MaterialAlertDialogBuilder b =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                        .setTitle("🧹 پاک‌سازی عکس‌ها")
                        .setMessage(msg)
                        .setNegativeButton("بستن", null);
        if (r[0] + r[2] > 0) {
            b.setPositiveButton("پاک‌سازی", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    if (!act.canWrite()) return;
                    long[] x = Db.get(act).photoScan(true);
                    Ui.toast(act, "پاک شد: " + x[0] + " فایل و " + x[2] + " ردیف");
                }
            });
        } else {
            b.setMessage(msg + "\n\n✓ چیزی برای پاک‌سازی نیست.");
        }
        b.show();
    }

    private static void confirmDelete(final MainActivity act, final Db.CustomerPhoto p) {
        if (!act.canWrite()) {
            return;
        }
        deleting = true;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                .setTitle("حذف عکس")
                .setMessage("این عکس برای همیشه حذف شود؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Db.get(act).deleteCustomerPhoto(p.id);
                        deleting = false;
                        CustomerProfile.show(act, p.customerId);
                    }
                })
                .setNegativeButton("انصراف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        deleting = false;
                        viewer(act, p);
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface d) {
                        deleting = false;
                        viewer(act, p);
                    }
                })
                .show();
    }
}
