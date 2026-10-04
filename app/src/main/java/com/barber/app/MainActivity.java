package com.barber.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Locale;

/** صفحه اصلی: پنج تب (نوبت‌ها، مشتریان، هزینه‌ها، آرایشگران، گزارش) و منوی تنظیمات. */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;
    private static final int REQ_NOTIFICATIONS = 2001;
    private static final int REQ_SMS = 2002;

    /** نتیجه‌ی درخواست مجوز پیامک */
    public interface PermCallback {
        void onResult(boolean granted);
    }

    private PermCallback smsCallback;

    // ---------------- قفل برنامه ----------------

    /**
     * آیا کاربر در این «نشست» قفل را باز کرده؟ استاتیک است تا با تغییر تم/چرخش صفحه دوباره رمز نخواهد،
     * ولی با بسته شدن کامل برنامه (یا ریستارت گوشی) صفر می‌شود.
     */
    private static boolean sessionUnlocked = false;
    private static long lastStopElapsed = 0;
    private LockView lockView;

    /** جهت راست‌به‌چپ را برای کل برنامه (از جمله دیالوگ‌ها) اعمال می‌کند، حتی اگر زبان گوشی فارسی نباشد. */
    @SuppressWarnings("deprecation")
    private void forceRtl() {
        try {
            android.content.res.Configuration cfg = new android.content.res.Configuration(getResources().getConfiguration());
            cfg.setLayoutDirection(new java.util.Locale("fa", "IR"));
            getResources().updateConfiguration(cfg, getResources().getDisplayMetrics());
        } catch (Exception ignored) {
            // اگر نشد، همان رفتار قبلی (ریشه‌ی صفحه RTL) باقی می‌ماند
        }
    }

    public void markSessionUnlocked() {
        sessionUnlocked = true;
    }

    /** با فعال بودن قفل، عکس‌گرفتن از صفحه و پیش‌نمایش در لیست اخیر مسدود می‌شود. */
    public void applySecureFlag() {
        if (LockManager.isEnabled(this)) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void showLockIfNeeded() {
        if (!LockManager.isEnabled(this) || sessionUnlocked || lockView != null) return;
        lockView = new LockView(this, new LockView.Listener() {
            @Override
            public void onUnlocked() {
                sessionUnlocked = true;
                if (lockView != null && lockView.getParent() instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) lockView.getParent()).removeView(lockView);
                }
                lockView = null;
            }
        });
        addContentView(lockView, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // بعد از بیرون رفتن بیش از مدت تعیین‌شده، صفحه را از نو می‌سازیم تا هر پنجره‌ی بازِ حاوی اطلاعات هم بسته شود
        if (LockManager.isEnabled(this) && sessionUnlocked && lockView == null && lastStopElapsed > 0) {
            long away = android.os.SystemClock.elapsedRealtime() - lastStopElapsed;
            if (away >= LockManager.getTimeoutSeconds(this) * 1000L) {
                sessionUnlocked = false;
                recreate();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            lastStopElapsed = android.os.SystemClock.elapsedRealtime();
        }
    }

    @Override
    public void onBackPressed() {
        if (lockView != null) {
            moveTaskToBack(true); // روی صفحه‌ی قفل، برگشت یعنی خروج از برنامه
            return;
        }
        super.onBackPressed();
    }

    /** تاریخ انتخاب‌شده (شمسی) که بین تب‌ها مشترک است: {سال، ماه، روز} */
    public int[] selectedDate = JalaliCalendar.today();

    private View[] tabViews;
    private Refreshable[] tabs;
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        forceRtl();
        applySecureFlag();
        if (savedInstanceState != null) {
            int[] d = savedInstanceState.getIntArray("selected_date");
            if (d != null && d.length == 3) selectedDate = d;
        }
        setContentView(R.layout.activity_main);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        int[] today = JalaliCalendar.today();
        toolbar.setSubtitle(JalaliCalendar.DAY_NAMES[JalaliCalendar.dayOfWeek(today[0], today[1], today[2])]
                + "  " + JalaliCalendar.format(today[0], today[1], today[2]));

        View v0 = findViewById(R.id.tab_appointments);
        View v1 = findViewById(R.id.tab_customers);
        View v2 = findViewById(R.id.tab_expenses);
        View v3 = findViewById(R.id.tab_barbers);
        View v4 = findViewById(R.id.tab_reports);
        tabViews = new View[]{v0, v1, v2, v3, v4};
        tabs = new Refreshable[]{
                new AppointmentsTab(this, v0),
                new CustomersTab(this, v1),
                new ExpensesTab(this, v2),
                new BarbersTab(this, v3),
                new ReportsTab(this, v4)
        };

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(new com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.nav_appointments) {
                    showTab(0);
                } else if (id == R.id.nav_customers) {
                    showTab(1);
                } else if (id == R.id.nav_expenses) {
                    showTab(2);
                } else if (id == R.id.nav_barbers) {
                    showTab(3);
                } else if (id == R.id.nav_reports) {
                    showTab(4);
                } else {
                    return false;
                }
                return true;
            }
        });
        int startTab = savedInstanceState != null ? savedInstanceState.getInt("tab", 0) : 0;
        if (startTab < 0 || startTab >= tabs.length) startTab = 0;
        int[] navIds = {R.id.nav_appointments, R.id.nav_customers, R.id.nav_expenses,
                R.id.nav_barbers, R.id.nav_reports};
        nav.setSelectedItemId(navIds[startTab]);
        showTab(startTab);

        requestNotificationPermission();
        ReminderScheduler.rescheduleAll(this);
        checkExpiry();
        showLockIfNeeded();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", currentTab);
        outState.putIntArray("selected_date", selectedDate);
    }

    private void showTab(int index) {
        currentTab = index;
        for (int i = 0; i < tabViews.length; i++) {
            tabViews[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        tabs[index].refresh();
    }

    /** بعد از تغییر داده‌ها، همه تب‌ها را به‌روز می‌کند. */
    public void refreshAll() {
        for (Refreshable t : tabs) t.refresh();
    }

    // ---------------- اعلان‌ها و انقضای نسخه ----------------

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    public boolean hasSmsPermission() {
        return checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    /** مجوز ارسال پیامک فقط وقتی پرسیده می‌شود که کاربر تیک «پیامک یادآوری» را بزند. */
    public void requestSmsPermission(PermCallback cb) {
        if (hasSmsPermission()) {
            cb.onResult(true);
            return;
        }
        smsCallback = cb;
        requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQ_SMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SMS && smsCallback != null) {
            PermCallback cb = smsCallback;
            smsCallback = null;
            cb.onResult(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        }
    }

    private void checkExpiry() {
        String expiry = AppConfig.BUILD_EXPIRY;
        if (expiry == null || expiry.isEmpty()) return;
        Calendar c = Calendar.getInstance();
        String today = String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        if (today.compareTo(expiry) > 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("نسخه منقضی شده")
                    .setMessage("نسخهٔ این برنامه منقضی شده است.\n"
                            + "برای دریافت نسخهٔ جدید با شماره " + AppConfig.SUPPORT_PHONE + " تماس بگیرید.")
                    .setCancelable(false)
                    .setPositiveButton("خروج", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        }
    }

    // ---------------- منو ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_lock) {
            LockSettings.show(this);
            return true;
        } else if (id == R.id.action_sms) {
            showSmsSettings();
            return true;
        } else if (id == R.id.action_loyalty) {
            showLoyaltySettings();
            return true;
        } else if (id == R.id.action_theme) {
            ThemeManager.showPicker(this, new ThemeManager.OnChanged() {
                @Override
                public void onChanged() {
                    recreate();
                }
            });
            return true;
        } else if (id == R.id.action_prices) {
            showPricesDialog();
            return true;
        } else if (id == R.id.action_backup) {
            startBackup();
            return true;
        } else if (id == R.id.action_restore) {
            confirmRestore();
            return true;
        } else if (id == R.id.action_help) {
            showHelp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void showPricesDialog() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(20), dp(12), dp(20), dp(12));

        final EditText[] fields = new EditText[AppConfig.SERVICES.length];
        for (int i = 0; i < AppConfig.SERVICES.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            TextView label = new TextView(this);
            label.setText(AppConfig.SERVICES[i]);
            label.setTextSize(16);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            EditText et = new EditText(this);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setText(Fmt.plain(AppConfig.getPrice(this, AppConfig.SERVICES[i])));
            fields[i] = et;
            row.addView(et, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));

            box.addView(row);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("تنظیم قیمت خدمات (" + AppConfig.CURRENCY + ")")
                .setView(scroll)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double[] values = new double[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    Double p = Fmt.parsePrice(fields[i].getText().toString());
                    if (p == null) {
                        fields[i].setError("قیمت " + AppConfig.SERVICES[i] + " صحیح نیست");
                        return;
                    }
                    values[i] = p;
                }
                for (int i = 0; i < fields.length; i++) {
                    AppConfig.setPrice(MainActivity.this, AppConfig.SERVICES[i], (long) values[i]);
                }
                dialog.dismiss();
                Ui.toast(MainActivity.this, "قیمت‌ها ذخیره شد");
            }
        });
    }

    /** تنظیم باشگاه مشتریان: هر چندمین مراجعه جایزه دارد (۰ = غیرفعال) */
    private void showLoyaltySettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(20), dp(12), dp(20), dp(12));

        TextView note = new TextView(this);
        note.setText("هر چندمین مراجعه‌ی مشتری جایزه (مثلاً تخفیف) داشته باشد؟\n"
                + "مثلاً ۱۰ یعنی مراجعه‌ی دهم، بیستم و… جایزه‌دار است. عدد ۰ باشگاه را غیرفعال می‌کند.\n"
                + "در پروفایل مشتری و هنگام ثبت نوبت یادآوری نمایش داده می‌شود.");
        note.setTextSize(13);
        box.addView(note);

        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setMaxLines(1);
        et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        et.setText(String.valueOf(AppConfig.getLoyaltyThreshold(this)));
        box.addView(et);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("🎁 باشگاه مشتریان")
                .setView(box)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double n = Fmt.parsePrice(et.getText().toString());
                if (n == null || n != Math.rint(n) || n > 999) {
                    et.setError("یک عدد صحیح (۰ تا ۹۹۹) وارد کنید");
                    return;
                }
                AppConfig.setLoyaltyThreshold(MainActivity.this, (int) (double) n);
                dialog.dismiss();
                Ui.toast(MainActivity.this, n == 0 ? "باشگاه مشتریان غیرفعال شد" : "ذخیره شد");
            }
        });
    }

    private static final int[] SMS_HOURS = {1, 2, 3, 6, 12, 24};

    /** تنظیمات پیامک یادآوری: نام آرایشگاه، چند ساعت قبل، و متن پیام */
    private void showSmsSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(20), dp(12), dp(20), dp(12));

        TextView note = new TextView(this);
        note.setText("پیامک فقط برای نوبت‌هایی ارسال می‌شود که هنگام ثبت نوبت، تیک «پیامک یادآوری» را بزنید. "
                + "هزینه‌ی پیامک طبق تعرفه‌ی سیم‌کارت گوشی شماست.");
        note.setTextSize(13);
        box.addView(note);

        final EditText etSalon = new EditText(this);
        etSalon.setHint("نام آرایشگاه");
        etSalon.setText(AppConfig.getSalonName(this));
        box.addView(etSalon);

        TextView lbl = new TextView(this);
        lbl.setText("زمان ارسال:");
        lbl.setPadding(0, dp(8), 0, 0);
        box.addView(lbl);

        final android.widget.Spinner spHours = new android.widget.Spinner(this);
        java.util.List<String> labels = new java.util.ArrayList<>();
        int sel = 2;
        for (int i = 0; i < SMS_HOURS.length; i++) {
            labels.add(SMS_HOURS[i] + " ساعت قبل از نوبت");
            if (SMS_HOURS[i] == AppConfig.getSmsLeadHours(this)) sel = i;
        }
        spHours.setAdapter(Ui.spinnerAdapter(this, labels));
        spHours.setSelection(sel);
        box.addView(spHours);

        TextView lbl2 = new TextView(this);
        lbl2.setText("متن پیام (می‌توانید از {name} {date} {time} {service} {salon} استفاده کنید):");
        lbl2.setPadding(0, dp(8), 0, 0);
        lbl2.setTextSize(13);
        box.addView(lbl2);

        final EditText etText = new EditText(this);
        etText.setText(AppConfig.getSmsTemplate(this));
        etText.setMinLines(4);
        etText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        etText.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(etText);

        android.widget.Button reset = new android.widget.Button(this);
        reset.setText("بازگردانی متن پیش‌فرض");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etText.setText(AppConfig.DEFAULT_SMS_TEMPLATE);
            }
        });
        box.addView(reset);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        new MaterialAlertDialogBuilder(this)
                .setTitle("پیامک یادآوری")
                .setView(scroll)
                .setPositiveButton("ذخیره", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String text = etText.getText().toString().trim();
                        if (text.isEmpty()) text = AppConfig.DEFAULT_SMS_TEMPLATE;
                        String salon = etSalon.getText().toString().trim();
                        if (salon.isEmpty()) salon = "آرایشگاه";
                        AppConfig.setSmsSettings(MainActivity.this, salon, text,
                                SMS_HOURS[spHours.getSelectedItemPosition()]);
                        ReminderScheduler.rescheduleAll(MainActivity.this);
                        Ui.toast(MainActivity.this, "تنظیمات پیامک ذخیره شد");
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showHelp() {
        String text = "📅 نوبت‌دهی:\n"
                + "• با دکمه + یک نوبت جدید بسازید (مشتری، ساعت و خدمت را انتخاب کنید)\n"
                + "• با فلش‌ها روز را عوض کنید؛ با لمس تاریخ می‌توانید مستقیم تاریخ را انتخاب کنید\n"
                + "• روی هر نوبت بزنید تا ویرایش یا حذف کنید\n"
                + "• نیم ساعت قبل از هر نوبت یک اعلان نمایش داده می‌شود\n\n"
                + "👥 مشتریان: با دکمه + مشتری اضافه کنید؛ با لمس مشتری می‌توانید ویرایش، تماس یا حذف کنید.\n\n"
                + "💰 هزینه‌ها: هزینه‌های روزانه را ثبت کنید. با لمس هر هزینه می‌توانید آن را حذف کنید.\n\n"
                + "💇 آرایشگران: آرایشگران درصدی را با «درصد سهم سالن» اضافه کنید و سرویس‌هایشان را ثبت کنید.\n\n"
                + "📊 گزارش: درآمد، هزینه‌ها، سهم سالن و مانده‌ی هر ماه.\n\n"
                + "🎨 تم: از منوی بالا (⋮) ← «تم و ظاهر برنامه» یکی از ۸ تم را انتخاب کنید یا حالت خودکار را بزنید.\n\n"
                + "✂️ چند خدمت: هنگام ثبت نوبت روی «خدمت‌ها» بزنید و هر چند خدمت می‌خواهید تیک بزنید (مثلاً اصلاح + ریش)؛ مبلغ کل خودکار جمع می‌شود و می‌توانید تغییرش دهید.\n\n"
                + "👤 پروفایل مشتری: در تب مشتریان (یا روی یک نوبت) «پروفایل» را بزنید تا تعداد مراجعه، آخرین مراجعه، مجموع پرداختی، نوبت بعدی، یادداشت و سابقه‌ی نوبت‌ها را ببینید. "
                + "باشگاه مشتریان (هر چندمین مراجعه جایزه) از منوی بالا قابل تنظیم است.\n\n"
                + "📤 خروجی گزارش: در تب گزارش، ماه را انتخاب کنید و «PDF»، «اکسل» یا «متن» را بزنید تا برای حسابدار یا شریک بفرستید. "
                + "با لمس نام هر آرایشگر در بخش «تسویه آرایشگران»، رسید پرداخت او ساخته می‌شود.\n\n"
                + "🔒 قفل برنامه: از منوی بالا رمز ۴ تا ۸ رقمی (و در صورت امکان اثر انگشت) تعیین کنید. "
                + "اگر رمز را فراموش کنید راه بازیابی نیست؛ پیش از فعال‌سازی پشتیبان‌گیری کنید.\n\n"
                + "📩 پیامک یادآوری: هنگام ثبت نوبت، تیک «پیامک یادآوری» را بزنید تا چند ساعت قبل از نوبت برای مشتری پیامک برود. "
                + "اگر تیک را نزنید هیچ پیامکی ارسال نمی‌شود. متن و زمان ارسال از منوی بالا قابل تنظیم است.\n\n"
                + "💾 پشتیبان‌گیری: از منوی بالا فایل دیتابیس را ذخیره کنید یا فایل salon.db نسخه ویندوز را بازیابی کنید.\n\n"
                + "📞 پشتیبانی: " + AppConfig.SUPPORT_PHONE;
        new MaterialAlertDialogBuilder(this)
                .setTitle("راهنمای استفاده")
                .setMessage(text)
                .setPositiveButton("بستن", null)
                .show();
    }

    // ---------------- پشتیبان‌گیری و بازیابی ----------------

    private void startBackup() {
        Calendar c = Calendar.getInstance();
        String name = String.format(Locale.US, "barber_backup_%04d%02d%02d.db",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, REQ_EXPORT);
    }

    private void confirmRestore() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("بازیابی اطلاعات")
                .setMessage("با بازیابی، تمام اطلاعات فعلی برنامه با محتوای فایل انتخابی جایگزین می‌شود.\n"
                        + "می‌توانید فایل salon.db نسخه ویندوز یا فایل پشتیبان قبلی را انتخاب کنید.\n\nادامه می‌دهید؟")
                .setPositiveButton("انتخاب فایل", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        startActivityForResult(i, REQ_IMPORT);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            doExport(uri);
        } else if (requestCode == REQ_IMPORT) {
            doImport(uri);
        }
    }

    private void doExport(Uri uri) {
        try {
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new java.io.IOException("no stream");
            try {
                Db.get(this).exportTo(out);
            } finally {
                out.close();
            }
            Ui.toastLong(this, "فایل پشتیبان ذخیره شد");
        } catch (Exception e) {
            Ui.toastLong(this, "ذخیره فایل پشتیبان ناموفق بود");
        }
    }

    private void doImport(Uri uri) {
        File tmp = new File(getCacheDir(), "import_tmp.db");
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.IOException("no stream");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                Db.copy(in, out);
            } finally {
                in.close();
                out.close();
            }
            if (!Db.isValidBackup(tmp)) {
                Ui.toastLong(this, "این فایل، دیتابیس معتبر برنامه نیست");
                return;
            }
            Db.get(this).replaceWith(tmp);
            ReminderScheduler.rescheduleAll(this);
            refreshAll();
            Ui.toastLong(this, "اطلاعات با موفقیت بازیابی شد");
        } catch (Exception e) {
            Ui.toastLong(this, "بازیابی اطلاعات ناموفق بود");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
