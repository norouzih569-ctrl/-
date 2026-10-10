package com.barber.admin;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;

import java.security.MessageDigest;

public class MainActivity extends AppCompatActivity {

    private Tab[] tabs;

    /** انتخاب فایل پشتیبان برای بازیابی */
    private final androidx.activity.result.ActivityResultLauncher<String[]> restorePicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                    new androidx.activity.result.ActivityResultCallback<android.net.Uri>() {
                        @Override
                        public void onActivityResult(android.net.Uri uri) {
                            BackupManager.restoreFrom(MainActivity.this, uri);
                        }
                    });
    private int currentTab = 0;

    private void forceRtl() {
        try {
            android.content.res.Configuration cfg = new android.content.res.Configuration(getResources().getConfiguration());
            cfg.setLayoutDirection(new java.util.Locale("fa", "IR"));
            getResources().updateConfiguration(cfg, getResources().getDisplayMetrics());
        } catch (Exception ignored) {
            // اگر نشد، ریشه‌ی صفحه RTL می‌ماند
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        forceRtl();
        setContentView(R.layout.activity_main);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        int[] today = JalaliCalendar.today();
        toolbar.setSubtitle(JalaliCalendar.DAY_NAMES[JalaliCalendar.dayOfWeek(today[0], today[1], today[2])]
                + "  " + JalaliCalendar.format(today[0], today[1], today[2]));

        Db.get(this).getWritableDatabase(); // ساخت پایگاه داده در اولین اجرا

        FrameLayout content = findViewById(R.id.content);
        tabs = new Tab[]{
                new CustomersTab(this),
                new LicensesTab(this),
                new VisitorsTab(this),
                new ReportsTab(this)
        };
        for (Tab t : tabs) {
            content.addView(t.view(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.nav_customers) {
                    showTab(0);
                } else if (id == R.id.nav_licenses) {
                    showTab(1);
                } else if (id == R.id.nav_visitors) {
                    showTab(2);
                } else if (id == R.id.nav_reports) {
                    showTab(3);
                } else {
                    return false;
                }
                return true;
            }
        });
        int startTab = savedInstanceState != null ? savedInstanceState.getInt("tab", 0) : 0;
        if (startTab < 0 || startTab >= tabs.length) startTab = 0;
        int[] navIds = {R.id.nav_customers, R.id.nav_licenses, R.id.nav_visitors, R.id.nav_reports};
        nav.setSelectedItemId(navIds[startTab]);
        showTab(startTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", currentTab);
    }

    /** بعد از تغییر داده‌ها، همه تب‌ها را به‌روز می‌کند. */
    public void refreshAll() {
        for (Tab t : tabs) t.refresh();
    }

    private void showTab(int index) {
        currentTab = index;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].view().setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        tabs[index].refresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_theme) {
            ThemeManager.showPicker(this, new ThemeManager.OnChanged() {
                @Override
                public void onChanged() {
                    recreate();
                }
            });
            return true;
        } else if (id == R.id.action_backup) {
            BackupManager.backup(this);
            return true;
        } else if (id == R.id.action_restore) {
            restorePicker.launch(new String[]{"*/*"});
            return true;
        } else if (id == R.id.action_pricelist_pdf) {
            DocMaker.priceListPdf(this);
            return true;
        } else if (id == R.id.action_seller) {
            DocMaker.sellerInfo(this);
            return true;
        } else if (id == R.id.action_prices) {
            PriceList.show(this);
            return true;
        } else if (id == R.id.action_key_info) {
            showKeyInfo();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** اثر انگشت کلید لایسنس؛ باید با مقدار نمایش‌داده‌شده در برنامه‌ی آرایشگاه یکی باشد. */
    static String keyFingerprint() {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(LicenseKeys.SECRET.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (i > 0) sb.append('-');
                sb.append(String.format("%02X%02X", h[2 * i] & 0xFF, h[2 * i + 1] & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            return "؟";
        }
    }

    private void showKeyInfo() {
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle("اطلاعات کلید لایسنس")
                .setMessage("اثر انگشت کلید:\n" + keyFingerprint()
                        + "\n\nاین کد باید با کد برنامه‌ی آرایشگاه (منوی لایسنس) یکی باشد؛ "
                        + "در غیر این صورت کدهای صادرشده در آن برنامه قبول نمی‌شوند.")
                .setPositiveButton("باشه", null)
                .create();
        d.show();
    }
}
