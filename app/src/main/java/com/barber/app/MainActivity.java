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

    /** تاریخ انتخاب‌شده (شمسی) که بین تب‌ها مشترک است: {سال، ماه، روز} */
    public int[] selectedDate = JalaliCalendar.today();

    private View[] tabViews;
    private Refreshable[] tabs;
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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
        showTab(0);

        requestNotificationPermission();
        ReminderScheduler.rescheduleAll(this);
        checkExpiry();
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

    private void checkExpiry() {
        String expiry = AppConfig.BUILD_EXPIRY;
        if (expiry == null || expiry.isEmpty()) return;
        Calendar c = Calendar.getInstance();
        String today = String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        if (today.compareTo(expiry) > 0) {
            new AlertDialog.Builder(this)
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
        if (id == R.id.action_prices) {
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

        final AlertDialog dialog = new AlertDialog.Builder(this)
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
                + "💾 پشتیبان‌گیری: از منوی بالا فایل دیتابیس را ذخیره کنید یا فایل salon.db نسخه ویندوز را بازیابی کنید.\n\n"
                + "📞 پشتیبانی: " + AppConfig.SUPPORT_PHONE;
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
