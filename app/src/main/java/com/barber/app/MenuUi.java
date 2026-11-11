package com.barber.app;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** منوی دسته‌بندی‌شده: کاشی‌های آیکن‌دار؛ هر دسته به فهرست کاشی‌های خودش می‌رود. */
final class MenuUi {

    private MenuUi() {
    }

    /** کاشی: {آیکن، عنوان، شناسه‌ی عمل (R.id.action_*) یا 0 برای دسته} */
    private static final class Tile {
        final String icon;
        final String label;
        final int action;
        final Tile[] children;

        Tile(String icon, String label, int action) {
            this.icon = icon;
            this.label = label;
            this.action = action;
            this.children = null;
        }

        Tile(String icon, String label, Tile... children) {
            this.icon = icon;
            this.label = label;
            this.action = 0;
            this.children = children;
        }
    }

    private static Tile[] root() {
        return new Tile[]{
                new Tile("🛍", "محصولات و فروش", R.id.action_products),
                new Tile("💰", "حساب‌وکتاب",
                        new Tile("✍️", "ثبت درآمد روز (بدون نوبت)", R.id.action_manual_income),
                        new Tile("📅", "حساب آخر سال و وام", R.id.action_year),
                        new Tile("🏦", "سقف سالانه درآمد کارتی", R.id.action_cap),
                        new Tile("⏰", "یادآور پرداخت‌ها", R.id.action_reminders)),
                new Tile("👥", "مشتریان",
                        new Tile("🎁", "باشگاه مشتریان", R.id.action_loyalty),
                        new Tile("📩", "پیامک یادآوری", R.id.action_sms),
                        new Tile("💌", "دعوت مشتریان قدیمی", R.id.action_winback)),
                new Tile("⚙️", "تنظیمات",
                        new Tile("⏱", "نوبت‌دهی", R.id.action_schedule),
                        new Tile("💈", "خدمات، قیمت و پکیج", R.id.action_prices),
                        new Tile("🔔", "اعلان‌ها", R.id.action_notify),
                        new Tile("🔒", "قفل برنامه", R.id.action_lock),
                        new Tile("🎨", "تم و ظاهر", R.id.action_theme),
                        new Tile("🔢", "نوع رقم‌ها", R.id.action_digits),
                        new Tile("🔠", "اندازه‌ی نوشته‌ها", R.id.action_fontsize)),
                new Tile("🗂", "اطلاعات",
                        new Tile("💾", "پشتیبان‌گیری", R.id.action_backup),
                        new Tile("♻️", "بازیابی از پشتیبان", R.id.action_restore)),
                new Tile("ℹ️", "لایسنس و راهنما",
                        new Tile("🔑", "لایسنس و فعال‌سازی", R.id.action_license),
                        new Tile("❓", "راهنما", R.id.action_help)),
        };
    }

    static void show(MainActivity act) {
        showTiles(act, "منو", root(), false);
    }

    private static void showTiles(final MainActivity act, String title, final Tile[] tiles, final boolean sub) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 4));

        final AlertDialog[] holder = new AlertDialog[1];
        int accent = Ui.themeColor(act, R.attr.barberAccent, 0xFF1E6BE6);
        LinearLayout row = null;
        for (int i = 0; i < tiles.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                box.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            final Tile t = tiles[i];
            LinearLayout cell = new LinearLayout(act);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(Ui.dp(act, 6), Ui.dp(act, 14), Ui.dp(act, 6), Ui.dp(act, 14));
            cell.setBackground(Ui.tinted(act, accent, 0x1F, 14));
            cell.setClickable(true);
            cell.setFocusable(true);

            TextView ic = new TextView(act);
            ic.setText(t.icon);
            ic.setTextSize(30);
            ic.setGravity(Gravity.CENTER);
            cell.addView(ic);

            TextView lb = new TextView(act);
            lb.setText(t.label);
            lb.setTextSize(13);
            lb.setGravity(Gravity.CENTER);
            lb.setTypeface(lb.getTypeface(), android.graphics.Typeface.BOLD);
            lb.setPadding(0, Ui.dp(act, 4), 0, 0);
            cell.addView(lb);

            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    holder[0].dismiss();
                    if (t.children != null) {
                        showTiles(act, t.icon + " " + t.label, t.children, true);
                    } else {
                        act.runAction(t.action);
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4));
            row.addView(cell, lp);
        }
        // ردیف آخرِ تکی: یک جای خالی برای هم‌اندازه ماندن
        if (tiles.length % 2 == 1 && row != null) {
            View sp = new View(act);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.setMargins(Ui.dp(act, 4), 0, Ui.dp(act, 4), 0);
            row.addView(sp, lp);
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(act)
                .setTitle(title)
                .setView(Ui.scroll(box))
                .setNegativeButton("بستن", null);
        if (sub) {
            b.setNeutralButton("↩ منو", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    show(act);
                }
            });
        }
        holder[0] = b.create();
        holder[0].show();
    }
}
