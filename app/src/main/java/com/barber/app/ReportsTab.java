package com.barber.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** تب گزارش ماهانه: درآمد، سهم سالن، هزینه‌ها، مانده، تسویه آرایشگران و خروجی PDF/اکسل/متن. */
public class ReportsTab implements Refreshable {

    private final MainActivity act;
    private final Spinner spMonth;
    private final Spinner spYear;
    private final TextView statIncome;
    private final TextView statSalonShare;
    private final TextView statExpenses;
    private final TextView statBalance;
    private final LinearLayout llSettlement;
    private final LinearLayout llExpenses;
    private final LinearLayout llServices;
    private final List<Integer> years = new ArrayList<>();

    /** گزارش ماهِ انتخاب‌شده؛ نمایش و خروجی‌ها همگی از همین یک منبع می‌آیند. */
    private ReportData current;

    public ReportsTab(MainActivity activity, View root) {
        this.act = activity;
        spMonth = root.findViewById(R.id.sp_report_month);
        spYear = root.findViewById(R.id.sp_report_year);
        statIncome = root.findViewById(R.id.stat_income);
        statSalonShare = root.findViewById(R.id.stat_salon_share);
        statExpenses = root.findViewById(R.id.stat_expenses);
        statBalance = root.findViewById(R.id.stat_balance);
        llSettlement = root.findViewById(R.id.ll_settlement);
        llExpenses = root.findViewById(R.id.ll_month_expenses);
        llServices = root.findViewById(R.id.ll_month_barber_services);

        int[] today = JalaliCalendar.today();

        List<String> months = new ArrayList<>();
        for (String m : JalaliCalendar.MONTH_NAMES) months.add(m);
        spMonth.setAdapter(Ui.spinnerAdapter(act, months));
        spMonth.setSelection(today[1] - 1);

        List<String> yearLabels = new ArrayList<>();
        for (int y = today[0] - 3; y <= today[0] + 2; y++) {
            years.add(y);
            yearLabels.add(String.valueOf(y));
        }
        spYear.setAdapter(Ui.spinnerAdapter(act, yearLabels));
        spYear.setSelection(3);

        AdapterView.OnItemSelectedListener reload = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                load();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spMonth.setOnItemSelectedListener(reload);
        spYear.setOnItemSelectedListener(reload);

        root.findViewById(R.id.btn_export_pdf).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                export(0);
            }
        });
        root.findViewById(R.id.btn_export_excel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                export(1);
            }
        });
        root.findViewById(R.id.btn_export_text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                export(2);
            }
        });
    }

    @Override
    public void refresh() {
        load();
    }

    /** گزارش ماه انتخاب‌شده را از دیتابیس می‌سازد */
    private ReportData build(int year, int month) {
        String[] range = JalaliCalendar.monthRangeIso(year, month);
        Db db = Db.get(act);

        ReportData r = new ReportData();
        r.year = year;
        r.month = month;
        r.monthName = JalaliCalendar.MONTH_NAMES[month - 1];
        r.salon = AppConfig.getSalonName(act);
        r.currency = AppConfig.CURRENCY;
        int[] t = JalaliCalendar.today();
        r.generatedOn = JalaliCalendar.format(t[0], t[1], t[2]);

        for (Db.Appointment a : db.getAppointmentsBetween(range[0], range[1])) {
            ReportData.Appt x = new ReportData.Appt();
            x.date = JalaliCalendar.gregorianStringToJalali(a.date);
            x.time = a.time;
            x.customer = a.customerName;
            x.service = a.service;
            x.price = a.price;
            r.appts.add(x);
        }
        for (Db.Expense e : db.getExpensesBetween(range[0], range[1])) {
            ReportData.Exp x = new ReportData.Exp();
            x.date = JalaliCalendar.gregorianStringToJalali(e.date);
            x.desc = e.description;
            x.amount = e.amount;
            r.exps.add(x);
        }
        for (Db.BarberService s : db.getBarberServicesBetween(range[0], range[1])) {
            ReportData.Svc x = new ReportData.Svc();
            x.date = JalaliCalendar.gregorianStringToJalali(s.date);
            x.barber = s.barberName;
            x.service = s.service;
            x.price = s.price;
            x.barberShare = s.barberShare;
            x.salonShare = s.salonShare;
            r.svcs.add(x);
        }
        r.computeTotals();
        return r;
    }

    private void load() {
        int month = spMonth.getSelectedItemPosition() + 1;
        int yearPos = spYear.getSelectedItemPosition();
        if (month < 1 || yearPos < 0 || yearPos >= years.size()) return;
        final ReportData r = build(years.get(yearPos), month);
        current = r;

        statIncome.setText(r.money(r.income));
        statSalonShare.setText(r.money(r.salonShare));
        statExpenses.setText(r.money(r.expensesTotal));
        statBalance.setText(r.money(r.balance));
        statBalance.setTextColor(r.balance >= 0 ? 0xFF66BB6A : 0xFFEF5350);

        // تسویه‌ی آرایشگران (با لمس: رسید)
        llSettlement.removeAllViews();
        List<ReportData.Settle> settle = r.settlements();
        if (settle.isEmpty()) {
            llSettlement.addView(emptyText("سرویسی برای آرایشگران در این ماه ثبت نشده است"));
        } else {
            LayoutInflater inflater = LayoutInflater.from(act);
            for (final ReportData.Settle s : settle) {
                View v = inflater.inflate(R.layout.item_row, llSettlement, false);
                RowAdapter.bind(v, new RowAdapter.Row(0, "", s.barber,
                        s.count + " سرویس | سهم سالن: " + ReportData.fmt(s.salonShare),
                        ReportData.fmt(s.barberShare), true));
                View card = v.findViewById(R.id.row_card);
                card.setClickable(true);
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showSlip(r, s);
                    }
                });
                llSettlement.addView(v, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        List<RowAdapter.Row> expenseRows = new ArrayList<>();
        for (ReportData.Exp e : r.exps) {
            expenseRows.add(new RowAdapter.Row(0, e.date, e.desc, "", ReportData.fmt(e.amount)));
        }
        fill(llExpenses, expenseRows, "هزینه‌ای در این ماه ثبت نشده است");

        List<RowAdapter.Row> serviceRows = new ArrayList<>();
        for (ReportData.Svc s : r.svcs) {
            String sub = s.service + " | سهم آرایشگر: " + ReportData.fmt(s.barberShare)
                    + " | سهم سالن: " + ReportData.fmt(s.salonShare);
            serviceRows.add(new RowAdapter.Row(0, s.date, s.barber, sub, ReportData.fmt(s.price)));
        }
        fill(llServices, serviceRows, "سرویسی در این ماه ثبت نشده است");
    }

    private void showSlip(final ReportData r, final ReportData.Settle s) {
        final String slip = r.slipText(s);
        new MaterialAlertDialogBuilder(act)
                .setTitle("رسید تسویه — " + s.barber)
                .setMessage(slip)
                .setPositiveButton("ارسال", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        ReportExporter.shareText(act, "رسید تسویه " + s.barber, slip);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    /** 0 = PDF ، 1 = اکسل ، 2 = متن */
    private void export(int kind) {
        if (current == null) {
            load();
            if (current == null) return;
        }
        try {
            if (kind == 0) {
                ReportExporter.sharePdf(act, current);
            } else if (kind == 1) {
                ReportExporter.shareExcel(act, current);
            } else {
                ReportExporter.shareText(act, current.title(), current.toText());
            }
        } catch (Exception e) {
            Ui.toastLong(act, "ساخت یا ارسال فایل گزارش ناموفق بود");
        }
    }

    private TextView emptyText(String text) {
        TextView tv = new TextView(act);
        tv.setText(text);
        tv.setPadding(12, 12, 12, 12);
        return tv;
    }

    private void fill(LinearLayout container, List<RowAdapter.Row> rows, String emptyText) {
        container.removeAllViews();
        if (rows.isEmpty()) {
            container.addView(emptyText(emptyText));
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(act);
        for (RowAdapter.Row r : rows) {
            View v = inflater.inflate(R.layout.item_row, container, false);
            RowAdapter.bind(v, r);
            container.addView(v, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
