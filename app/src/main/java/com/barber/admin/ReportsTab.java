package com.barber.admin;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** تب گزارش: درآمد دوره، مانده‌ها، پورسانت ویزیتورها و یادآوری تمدید. */
public class ReportsTab implements Tab {

    private final MainActivity act;
    private final LinearLayout root;
    private final LinearLayout content;
    private final Spinner spPeriod;

    private static final String[] PERIODS = {"این ماه", "ماه قبل", "امسال", "کل زمان"};

    public ReportsTab(MainActivity activity) {
        this.act = activity;
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        spPeriod = new Spinner(act);
        List<String> items = new ArrayList<String>();
        for (String p : PERIODS) items.add(p);
        spPeriod.setAdapter(Ui.spinnerAdapter(act, items));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(Ui.dp(act, 16), Ui.dp(act, 8), Ui.dp(act, 16), 0);
        root.addView(spPeriod, sp);

        ScrollView scroll = new ScrollView(act);
        content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(act, 12), Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        spPeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    public View view() {
        return root;
    }

    // ---------------- بازه‌ی زمانی ----------------

    private String[] range() {
        int[] j = JalaliCalendar.today();
        switch (spPeriod.getSelectedItemPosition()) {
            case 1: {
                int y = j[0];
                int m = j[1] - 1;
                if (m < 1) {
                    m = 12;
                    y--;
                }
                return JalaliCalendar.monthRangeIso(y, m);
            }
            case 2:
                return new String[]{JalaliCalendar.jalaliToGregorianString(j[0], 1, 1),
                        JalaliCalendar.jalaliToGregorianString(j[0], 12, JalaliCalendar.monthLength(j[0], 12))};
            case 3:
                return new String[]{"0000-01-01", "9999-12-31"};
            default:
                return JalaliCalendar.monthRangeIso(j[0], j[1]);
        }
    }

    private String periodTitle() {
        String[] r = range();
        if (spPeriod.getSelectedItemPosition() == 3) return "کل زمان";
        return PERIODS[spPeriod.getSelectedItemPosition()] + " (" + JalaliCalendar.gregorianStringToJalali(r[0])
                + " تا " + JalaliCalendar.gregorianStringToJalali(r[1]) + ")";
    }

    // ---------------- اجزای ساده ----------------

    private TextView heading(String t) {
        TextView v = new TextView(act);
        v.setText(t);
        v.setTextSize(15);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        v.setTextColor(Ui.themeColor(act, R.attr.barberAccent, 0xFF0F766E));
        v.setPadding(Ui.dp(act, 4), Ui.dp(act, 16), 0, Ui.dp(act, 6));
        return v;
    }

    private LinearLayout card() {
        MaterialCardView cv = new MaterialCardView(act);
        LinearLayout inner = new LinearLayout(act);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(Ui.dp(act, 14), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10));
        cv.addView(inner);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 4);
        content.addView(cv, lp);
        return inner;
    }

    private void row(LinearLayout card, String label, String value, boolean strong) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        TextView l = new TextView(act);
        l.setText(label);
        l.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(act);
        v.setText(value);
        v.setGravity(Gravity.END);
        v.setTypeface(v.getTypeface(), strong ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        card.addView(r);
    }

    private void note(String t) {
        TextView v = new TextView(act);
        v.setText(t);
        v.setTextSize(12);
        v.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        v.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), 0);
        content.addView(v);
    }

    // ---------------- ساخت گزارش ----------------

    @Override
    public void refresh() {
        content.removeAllViews();
        Db db = Db.get(act);
        List<Models.License> lics = db.listLicenses(0);
        List<Models.Payment> pays = db.listAllPayments();
        List<Models.Payout> outs = db.listAllPayouts();
        String[] r = range();
        Stats.Totals t = Stats.compute(lics, pays, outs, r[0], r[1]);
        Stats.Position pos = Stats.position(lics, pays, outs);

        // خلاصه‌ی دوره
        content.addView(heading("خلاصه – " + periodTitle()));
        LinearLayout c1 = card();
        row(c1, "لایسنس فروخته‌شده", Fa.digits(String.valueOf(t.salesCount)) + " عدد", false);
        row(c1, "مجموع فروش", Fa.money(t.salesAmount), true);
        if (t.discountAmount > 0) row(c1, "تخفیف داده‌شده", Fa.money(t.discountAmount), false);
        row(c1, "پورسانت ویزیتورها (تعهدی)", Fa.money(t.commissionAccrued), false);
        row(c1, "درآمد خالص فروش", Fa.money(t.netSales()), true);
        LinearLayout c2 = card();
        row(c2, "دریافتی نقدی از مشتریان", Fa.money(t.received), false);
        row(c2, "پورسانت پرداخت‌شده", Fa.money(t.commissionPaid), false);
        row(c2, "خالص نقدی", Fa.money(t.netCash()), true);

        // به تفکیک نوع لایسنس
        content.addView(heading("فروش به تفکیک نوع"));
        LinearLayout c3 = card();
        boolean any = false;
        for (int type : Labels.SELLABLE_TYPES) {
            int[] n = t.byTypeCount.get(type);
            if (n == null) continue;
            any = true;
            row(c3, Labels.licenseType(type) + " (" + Fa.digits(String.valueOf(n[0])) + ")",
                    Fa.money(t.byTypeAmount.get(type)[0]), false);
        }
        if (!any) row(c3, "در این دوره فروشی ثبت نشده", "—", false);

        // پورسانت ویزیتورها در دوره
        if (!t.byVisitor.isEmpty()) {
            content.addView(heading("پورسانت ویزیتورها در این دوره"));
            LinearLayout c4 = card();
            for (Map.Entry<Long, long[]> e : t.byVisitor.entrySet()) {
                Models.Visitor v = db.getVisitor(e.getKey());
                row(c4, v != null ? v.name : "؟", Fa.money(e.getValue()[0]), false);
            }
        }

        // مانده‌ها (مستقل از دوره)
        content.addView(heading("مانده‌ی حساب‌ها (کل زمان)"));
        LinearLayout c5 = card();
        row(c5, "بدهی مشتریان (" + Fa.digits(String.valueOf(pos.debtors)) + " نفر)", Fa.money(pos.receivables), true);
        row(c5, "پورسانت پرداخت‌نشده به ویزیتورها", Fa.money(pos.commissionDue), true);

        // یادآوری تمدید
        addRenewals(db);
    }

    // ---------------- تمدید ----------------

    private void addRenewals(final Db db) {
        final long today = IssueLogic.todayEpochDay();
        final List<Renewal.Info> list = new ArrayList<Renewal.Info>();
        final java.util.Map<Long, Models.Customer> byId = new java.util.HashMap<Long, Models.Customer>();
        for (Models.Customer c : db.listCustomers("")) {
            byId.put(c.id, c);
            List<Models.License> ls = db.listLicenses(c.id);   // جدید به قدیم
            Collections.reverse(ls);
            Renewal.Info info = Renewal.info(c.id, ls, today);
            if (info != null && info.daysLeft <= Renewal.WARN_DAYS && info.daysLeft >= -365) list.add(info);
        }
        Collections.sort(list, new Comparator<Renewal.Info>() {
            @Override
            public int compare(Renewal.Info a, Renewal.Info b) {
                return Long.compare(a.daysLeft, b.daysLeft);
            }
        });

        content.addView(heading("یادآوری تمدید (" + Fa.digits(String.valueOf(list.size())) + ")"));
        LinearLayout card = card();
        if (list.isEmpty()) {
            row(card, "لایسنسی در ۳۰ روز آینده تمام نمی‌شود", "✅", false);
        }
        for (final Renewal.Info info : list) {
            final Models.Customer c = byId.get(info.customerId);
            if (c == null) continue;
            String status = info.daysLeft < 0
                    ? "⛔ " + Fa.digits(String.valueOf(-info.daysLeft)) + " روز پیش تمام شد"
                    : "⏳ " + Fa.digits(String.valueOf(info.daysLeft)) + " روز مانده";
            LinearLayout line = new LinearLayout(act);
            line.setOrientation(LinearLayout.VERTICAL);
            line.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));
            TextView title = new TextView(act);
            title.setText(c.shopName + " – " + status);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            line.addView(title);
            TextView sub = new TextView(act);
            sub.setText("پایان تخمینی: " + JalaliCalendar.gregorianStringToJalali(
                    java.time.LocalDate.ofEpochDay(info.endEpochDay).toString()));
            sub.setTextSize(12);
            sub.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
            line.addView(sub);
            line.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRenewalActions(c, info);
                }
            });
            card.addView(line);
        }
        note("تاریخ پایان تخمینی است (فرض می‌شود هر کد همان روز صدور فعال شده). تاریخ دقیق را از منوی لایسنس گوشی مشتری ببینید.");
    }

    private String reminderText(Models.Customer c, Renewal.Info info) {
        String date = JalaliCalendar.gregorianStringToJalali(java.time.LocalDate.ofEpochDay(info.endEpochDay).toString());
        String who = c.ownerName.isEmpty() ? "" : c.ownerName + " عزیز، ";
        String when = info.daysLeft < 0
                ? "لایسنس نرم‌افزار مدیریت آرایشگاه شما در تاریخ " + date + " تمام شده است"
                : "لایسنس نرم‌افزار مدیریت آرایشگاه شما حدود " + Fa.digits(String.valueOf(info.daysLeft))
                + " روز دیگر (تا " + date + ") تمام می‌شود";
        Seller s = Seller.load(Db.get(act));
        return "سلام " + who + when + ". برای تمدید با ما در تماس باشید."
                + (s.phone.isEmpty() ? "" : " " + Fa.digits(s.phone));
    }

    private void showRenewalActions(final Models.Customer c, final Renewal.Info info) {
        final String msg = reminderText(c, info);
        final boolean hasMobile = !c.mobile.isEmpty();
        List<String> items = new ArrayList<String>();
        if (hasMobile) {
            items.add("📩 پیامک یادآوری");
            items.add("💬 واتساپ یادآوری");
        }
        items.add("🔑 صدور کد تمدید");
        items.add("👤 پروفایل مشتری");
        final int offset = hasMobile ? 2 : 0;
        new MaterialAlertDialogBuilder(act)
                .setTitle(c.shopName)
                .setItems(items.toArray(new CharSequence[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (hasMobile && which == 0) Contact.sms(act, c.mobile, msg);
                        else if (hasMobile && which == 1) Contact.whatsapp(act, c.mobile, msg);
                        else if (which == offset) IssueDialog.show(act, c.id);
                        else CustomerProfile.show(act, c.id);
                    }
                })
                .show();
    }
}
