package com.barber.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.TimeZone;

/**
 * نسخه‌ی «bazaar»: وضعیت لایسنس از روی اشتراک بازار تعیین می‌شود (نه کد).
 * پایان اشتراک را خود بازار نگه می‌دارد؛ اینجا فقط «اعتبار تا N روز بعد از آخرین تأیید» ذخیره می‌شود
 * تا اگر گوشی موقتاً اینترنت نداشت، برنامه قفل نشود.
 */
public final class StoreLicense {

    /** چند روز بعد از آخرین تأییدِ آنلاین، برنامه بدون اینترنت هم فعال می‌ماند */
    public static final int GRACE_DAYS = 7;

    private static final String PREFS = "store_license";

    private static BazaarBilling billing;

    private StoreLicense() {
    }

    public static synchronized BazaarBilling billing(Context ctx) {
        if (billing == null) billing = new BazaarBilling(ctx.getApplicationContext());
        return billing;
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long today() {
        long now = System.currentTimeMillis();
        return LicenseLogic.epochDay(now, TimeZone.getDefault().getOffset(now));
    }

    /** آخرین روزی که برنامه باز شده را به‌روز می‌کند (برای تشخیص عقب‌کشیدن ساعت). */
    public static void onAppStart(Context ctx) {
        SharedPreferences p = sp(ctx);
        long today = today();
        long seen = p.getLong("seen", 0);
        if (seen == 0 || today > seen) p.edit().putLong("seen", today).apply();
    }

    public static LicenseLogic.State state(Context ctx, long today) {
        SharedPreferences p = sp(ctx);
        LicenseLogic.State s = LicenseLogic.evaluate(today, 0, p.getLong("valid_until", 0), p.getLong("seen", 0));
        if (s.status == LicenseLogic.Status.ACTIVE) s.daysLeft = 999; // پایان واقعی را بازار مدیریت می‌کند
        return s;
    }

    /** پرسش اشتراک از بازار و به‌روزکردن ذخیره؛ onChanged بعد از دریافت پاسخ (در نخ اصلی) اجرا می‌شود. */
    public static void refresh(final Context ctx, final Runnable onChanged) {
        billing(ctx).querySubscriptions(new BazaarBilling.SubsListener() {
            @Override
            public void onResult(boolean active, String productId, long purchaseTime) {
                SharedPreferences.Editor e = sp(ctx).edit();
                if (active) {
                    long now = System.currentTimeMillis();
                    long off = TimeZone.getDefault().getOffset(now);
                    e.putLong("valid_until", today() + GRACE_DAYS);
                    e.putString("sku", productId);
                    e.putLong("start", LicenseLogic.epochDay(purchaseTime, off));
                } else {
                    e.putLong("valid_until", 0);
                    e.putString("sku", "");
                    e.putLong("start", 0);
                }
                e.apply();
                if (onChanged != null) onChanged.run();
            }

            @Override
            public void onError(String reason) {
                // بدون اینترنت/بازار: وضعیت ذخیره‌شده (مهلت آفلاین) می‌ماند
            }
        });
    }

    /** نوع اشتراک فعلی به‌صورت کد نوع لایسنس (۶ ماهه/۱ ساله) یا ۰ */
    public static int type(Context ctx) {
        String sku = sp(ctx).getString("sku", "");
        if (BazaarBilling.SKU_6M.equals(sku)) return LicenseCodec.TYPE_6M;
        if (BazaarBilling.SKU_1Y.equals(sku)) return LicenseCodec.TYPE_1Y;
        return 0;
    }

    public static long start(Context ctx) {
        return sp(ctx).getLong("start", 0);
    }

    // ---- مخاطبین: نسخه‌ی بازار دسترسی خواندن مخاطبین ندارد (برای جلوگیری از مسدودی Play Protect) ----

    public static String contactsPermission() {
        return null;
    }

    // ---------------- صفحه‌ی اشتراک ----------------

    private static TextView text(Context c, String s, int sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        if (color != 0) t.setTextColor(color);
        return t;
    }

    private static MaterialButton planButton(Context c, String label) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 8);
        b.setLayoutParams(lp);
        return b;
    }

    /** «اشتراک و پرداخت»: وضعیت، دکمه‌های خرید ۶ ماهه/۱ ساله و بازیابی خرید. */
    public static void showDialog(final MainActivity act, final Runnable onChanged) {
        final LicenseLogic.State st = LicenseManager.state(act);
        final int red = 0xFFEF5350;
        final int green = 0xFF43A047;
        final int accent = Ui.themeColor(act, R.attr.barberAccent, 0xFF1E6BE6);
        final int grey = Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), Ui.dp(act, 8));

        if (st.status == LicenseLogic.Status.ACTIVE) {
            box.addView(text(act, "✅ اشتراک فعال است", 18, true, green));
            int t = type(act);
            boolean isTrial = BazaarBilling.SKU_TRIAL.equals(sp(act).getString("sku", ""));
            String name = isTrial ? "🎁 دوره‌ی رایگان ۷ روزه"
                    : t == LicenseCodec.TYPE_6M ? "اشتراک ۶ ماهه" : t == LicenseCodec.TYPE_1Y ? "اشتراک ۱ ساله" : "اشتراک";
            box.addView(text(act, name + "  •  از " + LicenseManager.dateText(start(act)), 14, false, 0));
            box.addView(text(act, "پایان و تمدید اشتراک را خود کافه‌بازار مدیریت می‌کند (برنامه‌ی بازار ← اشتراک‌ها).",
                    12, false, grey));
        } else if (st.status == LicenseLogic.Status.TAMPERED) {
            box.addView(text(act, "⚠️ ساعت گوشی نادرست است", 18, true, red));
            box.addView(text(act, "تاریخ و ساعت گوشی را درست کنید؛ تا آن موقع برنامه فقط‌خواندنی است.", 13, false, 0));
        } else {
            box.addView(text(act, "🔑 اشتراک فعال نیست", 18, true, st.status == LicenseLogic.Status.EXPIRED ? red : accent));
            box.addView(text(act, "اطلاعات شما حفظ شده و قابل مشاهده است. برای ثبت نوبت و تغییر اطلاعات، یکی از اشتراک‌های زیر را بخرید. "
                    + "پرداخت از طریق کافه‌بازار انجام می‌شود و بعد از پرداخت برنامه خودش فعال می‌شود.", 13, false, 0));
        }

        final TextView msg = text(act, "", 13, true, red);
        msg.setPadding(0, Ui.dp(act, 8), 0, 0);

        final AlertDialog[] holder = new AlertDialog[1];
        final Runnable done = new Runnable() {
            @Override
            public void run() {
                if (holder[0] != null) holder[0].dismiss();
                if (onChanged != null) onChanged.run();
            }
        };

        final MaterialButton b6 = planButton(act, "اشتراک ۶ ماهه — ۲۰٬۰۰۰٬۰۰۰ تومان");
        final MaterialButton b1 = planButton(act, "اشتراک ۱ ساله — ۴۰٬۰۰۰٬۰۰۰ تومان");
        // تا وقتی اشتراک آزمایشی در دسترس است فقط دکمه‌ی آن نشان داده می‌شود (طبق راهنمای بازار)

        // ۷ روز رایگان: فقط اگر برای این کاربر در دسترس باشد (هر کاربر یک بار) دکمه‌ی جدا نشان داده می‌شود
        final MaterialButton bTrial = planButton(act, "🎁 شروع ۷ روز رایگان");
        bTrial.setVisibility(View.GONE);
        if (st.status != LicenseLogic.Status.ACTIVE) {
            final TextView trial = text(act, "", 13, true, green);
            trial.setPadding(0, Ui.dp(act, 8), 0, 0);
            box.addView(trial);
            box.addView(bTrial);
            b6.setVisibility(View.GONE);
            b1.setVisibility(View.GONE);
            billing(act).checkTrial(new BazaarBilling.TrialListener() {
                @Override
                public void onResult(boolean available, int days) {
                    if (available) {
                        int d = days > 0 ? days : 7;
                        trial.setText("🎁 " + d + " روز رایگان و بدون هزینه؛ بعد از آن خودتان تصمیم می‌گیرید اشتراک بخرید یا نه.");
                        bTrial.setText("🎁 شروع " + d + " روز رایگان");
                        bTrial.setVisibility(View.VISIBLE);
                    } else {
                        b6.setVisibility(View.VISIBLE);
                        b1.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onError(String reason) {
                    b6.setVisibility(View.VISIBLE);
                    b1.setVisibility(View.VISIBLE);
                }
            });
            bTrial.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    msg.setTextColor(red);
                    msg.setText("");
                    billing(act).buy(act, BazaarBilling.SKU_TRIAL, new BazaarBilling.BuyListener() {
                        @Override
                        public void onSuccess(String productId) {
                            refresh(act, done);
                            Ui.toastLong(act, "✅ دوره‌ی رایگان شروع شد");
                        }

                        @Override
                        public void onCancel() {
                            msg.setText("لغو شد.");
                        }

                        @Override
                        public void onError(String reason) {
                            msg.setText("شروع دوره‌ی رایگان انجام نشد. مطمئن شوید برنامه‌ی کافه‌بازار نصب است و وارد حساب خود شده‌اید.\n(" + reason + ")");
                        }
                    });
                }
            });
        }

        View.OnClickListener buy = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String sku = v == b6 ? BazaarBilling.SKU_6M : BazaarBilling.SKU_1Y;
                msg.setText("");
                billing(act).buy(act, sku, new BazaarBilling.BuyListener() {
                    @Override
                    public void onSuccess(String productId) {
                        refresh(act, done);
                        Ui.toastLong(act, "✅ پرداخت انجام شد؛ اشتراک فعال می‌شود");
                    }

                    @Override
                    public void onCancel() {
                        msg.setText("خرید لغو شد.");
                    }

                    @Override
                    public void onError(String reason) {
                        msg.setText("خرید انجام نشد. مطمئن شوید برنامه‌ی کافه‌بازار نصب است و وارد حساب خود شده‌اید.\n(" + reason + ")");
                    }
                });
            }
        };
        b6.setOnClickListener(buy);
        b1.setOnClickListener(buy);
        box.addView(b6);
        box.addView(b1);

        MaterialButton restore = planButton(act, "🔄 بازیابی خرید (قبلاً خریده‌ام)");
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                msg.setTextColor(grey);
                msg.setText("در حال بررسی…");
                billing(act).querySubscriptions(new BazaarBilling.SubsListener() {
                    @Override
                    public void onResult(boolean active, String productId, long purchaseTime) {
                        if (active) {
                            refresh(act, done);
                            Ui.toastLong(act, "✅ اشتراک شما پیدا و فعال شد");
                        } else {
                            msg.setTextColor(red);
                            msg.setText("اشتراک فعالی برای این حساب بازار پیدا نشد.");
                        }
                    }

                    @Override
                    public void onError(String reason) {
                        msg.setTextColor(red);
                        msg.setText("اتصال به بازار برقرار نشد. برنامه‌ی کافه‌بازار و اینترنت را بررسی کنید.");
                    }
                });
            }
        });
        box.addView(restore);
        box.addView(msg);
        box.addView(text(act, "قیمت نهایی در صفحه‌ی پرداخت بازار نمایش داده می‌شود.", 11, false, grey));

        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);
        holder[0] = new MaterialAlertDialogBuilder(act)
                .setTitle("🔑 اشتراک و پرداخت")
                .setView(scroll)
                .setNegativeButton("بستن", null)
                .create();
        holder[0].show();
    }
}
