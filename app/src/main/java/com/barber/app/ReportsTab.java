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
    private final TextView tvPaySummary;
    private final TextView tvProductSummary;
    private final TextView tvCompare;
    private final TextView tvBestDay;
    private final BarChartView chart;
    private final LinearLayout llTableExtra;
    private final LinearLayout llTableAppts;
    private final LinearLayout llTableProducts;
    private boolean tableMode;
    private boolean syncing;
    private final com.google.android.material.button.MaterialButtonToggleGroup toggle;
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
        tvPaySummary = root.findViewById(R.id.tv_pay_summary);
        tvProductSummary = root.findViewById(R.id.tv_product_summary);
        tvCompare = root.findViewById(R.id.tv_compare);
        tvBestDay = root.findViewById(R.id.tv_best_day);
        chart = root.findViewById(R.id.chart_income);
        llTableExtra = root.findViewById(R.id.ll_table_extra);
        llTableAppts = root.findViewById(R.id.ll_table_appts);
        llTableProducts = root.findViewById(R.id.ll_table_products);
        tableMode = GridMode.isTable(act);
        toggle = root.findViewById(R.id.toggle_view);
        toggle.check(tableMode ? R.id.btn_view_table : R.id.btn_view_cards);
        toggle.addOnButtonCheckedListener(new com.google.android.material.button.MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(com.google.android.material.button.MaterialButtonToggleGroup g,
                                        int checkedId, boolean isChecked) {
                if (!isChecked || syncing) return;
                boolean t = checkedId == R.id.btn_view_table;
                if (t == GridMode.isTable(act)) return;
                GridMode.setTable(act, t);
                act.updateViewModeButton();
                act.refreshAll(); // همه‌ی تب‌ها با هم عوض می‌شوند
            }
        });
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
        return buildReport(act, year, month);
    }

    /** گزارش یک ماه (برای صفحه‌ی گزارش‌ها و رسید آرایشگران) */
    static ReportData buildReport(MainActivity act, int year, int month) {
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
            if (!a.done || a.earned() <= 0) continue; // «در انتظار» یا بدهی (پولی دریافت نشده) درآمد نیست
            ReportData.Appt x = new ReportData.Appt();
            x.date = JalaliCalendar.gregorianStringToJalali(a.date);
            x.day = JalaliCalendar.jalaliOfIso(a.date)[2];
            x.time = a.time;
            x.customer = a.customerName;
            x.service = a.service;
            x.price = a.earned();
            r.appts.add(x);
        }
        for (Db.ManualIncome mi : db.getManualIncomeBetween(range[0], range[1])) {
            ReportData.Appt x = new ReportData.Appt();
            x.date = JalaliCalendar.gregorianStringToJalali(mi.date);
            x.day = JalaliCalendar.jalaliOfIso(mi.date)[2];
            x.time = "—";
            x.customer = "درآمد دستی (بدون نوبت)";
            x.service = Pay.label(mi.method) + (mi.note == null || mi.note.isEmpty() ? "" : " – " + mi.note);
            x.price = mi.amount;
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
            x.barberId = s.barberId;
            r.svcs.add(x);
        }
        // تسویه‌ی آرایشگران: شماره کارت، مانده‌ی ماه‌های قبل و پرداخت‌های همین ماه
        for (Db.Barber b : db.getBarbers()) {
            ReportData.BarberInfo bi = new ReportData.BarberInfo();
            bi.name = b.name;
            bi.card = b.card;
            bi.prev = db.barberShareSum(b.id, "0000-01-01", prevDay(range[0]))
                    - db.barberPaidSum(b.id, "0000-01-01", prevDay(range[0]));
            bi.paid = db.barberPaidSum(b.id, range[0], range[1]);
            r.info.put(b.id, bi);
        }
        for (Db.BarberPayment p : db.getBarberPayments(-1, range[0], range[1])) {
            ReportData.PayRec pr = new ReportData.PayRec();
            pr.barberId = p.barberId;
            pr.date = JalaliCalendar.gregorianStringToJalali(p.date);
            pr.amount = p.amount;
            pr.note = p.note;
            r.pays.add(pr);
        }
        r.computeTotals();
        return r;
    }

    /** روز قبل از یک تاریخ ISO (برای حساب مانده‌ی قبل از ابتدای ماه) */
    private static String prevDay(String iso) {
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            String[] p = iso.split("-");
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 12, 0, 0);
            c.add(java.util.Calendar.DAY_OF_MONTH, -1);
            return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                    c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
                    c.get(java.util.Calendar.DAY_OF_MONTH));
        } catch (Exception e) {
            return "0000-01-01";
        }
    }

    private void load() {
        tableMode = GridMode.isTable(act);
        syncing = true;
        toggle.check(tableMode ? R.id.btn_view_table : R.id.btn_view_cards);
        syncing = false;
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

        // نمودار درآمد روزانه + مقایسه با ماه قبل
        int monthLen = JalaliCalendar.monthLength(r.year, r.month);
        int[] dd = new int[r.appts.size()];
        double[] da = new double[r.appts.size()];
        for (int i = 0; i < dd.length; i++) {
            dd[i] = r.appts.get(i).day;
            da[i] = r.appts.get(i).price;
        }
        double[] daily = ReportMath.dailyTotals(dd, da, monthLen);
        chart.setData(daily, Ui.themeColor(act, R.attr.barberAccent, 0xFF1F5FBF) & 0x00FFFFFF | 0x99000000,
                Ui.themeColor(act, R.attr.barberAccent, 0xFF1F5FBF),
                Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        int py = r.month == 1 ? r.year - 1 : r.year;
        int pm = r.month == 1 ? 12 : r.month - 1;
        ReportData prev = build(py, pm);
        String cmp = ReportMath.describeChange(r.income, prev.income);
        tvCompare.setText(cmp.isEmpty() ? "" : cmp + " (ماه قبل: " + r.money(prev.income) + ")");
        int bi = ReportMath.maxIndex(daily);
        tvBestDay.setText(bi < 0 ? "در این ماه درآمدی ثبت نشده است"
                : "پردرآمدترین روز: " + (bi + 1) + " " + r.monthName + " — " + r.money(daily[bi]));

        // دریافتی‌های ماه به تفکیک روش + کل بدهی مشتریان
        String[] range = JalaliCalendar.monthRangeIso(r.year, r.month);
        Db pdb = Db.get(act);
        double[] by = pdb.receivedByMethod(range[0], range[1]);
        StringBuilder ps = new StringBuilder();
        for (int i = 0; i < Pay.LABELS.length; i++) {
            ps.append(Pay.LABELS[i]).append(": ").append(r.money(by[i])).append("\n");
        }
        ps.append("جمع دریافتی: ").append(r.money(by[0] + by[1] + by[2])).append("\n");
        double debtAll = pdb.totalDebt();
        ps.append(debtAll > 0 ? "⚠️ کل بدهی مشتریان تا امروز: " + r.money(debtAll) : "✓ بدهی معوقی وجود ندارد");
        ps.append("\n(فقط پرداخت‌های ثبت‌شده با نسخه‌ی جدید؛ نوبت‌های قدیمی روش ندارند)");
        ps.append("\n").append(act.capStatusText());
        tvPaySummary.setText(ps.toString());

        // فروش محصولات ماه (جدا از درآمد نوبت‌ها)
        List<Db.ProductSale> psales = pdb.getProductSalesBetween(range[0], range[1]);
        double prRev = 0, prCost = 0;
        java.util.LinkedHashMap<String, int[]> byName = new java.util.LinkedHashMap<>();
        for (Db.ProductSale s : psales) {
            prRev += s.total();
            prCost += s.qty * s.unitCost;
            int[] q = byName.get(s.name);
            if (q == null) byName.put(s.name, q = new int[1]);
            q[0] += s.qty;
        }
        if (psales.isEmpty()) {
            tvProductSummary.setText("در این ماه فروش محصولی ثبت نشده است");
        } else {
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, int[]> e : byName.entrySet()) {
                sb.append(e.getKey()).append(" × ").append(e.getValue()[0]).append("\n");
            }
            sb.append("جمع فروش محصولات: ").append(r.money(prRev)).append("\n")
                    .append("قیمت تمام‌شده: ").append(r.money(prCost)).append("\n")
                    .append("سود محصولات: ").append(r.money(prRev - prCost));
            tvProductSummary.setText(sb.toString());
        }

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

        llTableExtra.setVisibility(tableMode ? View.VISIBLE : View.GONE);
        if (tableMode) renderTables(r, settle, psales);
    }

    private void putTable(LinearLayout box, String[] heads, List<Object[]> rows, Object[] totals,
                          String empty, TableUi.OnRow click) {
        box.removeAllViews();
        if (rows.isEmpty()) {
            box.addView(emptyText(empty));
            return;
        }
        box.addView(TableUi.build(act, heads, rows, totals, click));
    }

    /** نمای جدولی: همه‌ی بخش‌ها به‌صورت جدول (جایگزین کارت‌ها) */
    private void renderTables(final ReportData r, final List<ReportData.Settle> settle, List<Db.ProductSale> psales) {
        List<Object[]> rows = new ArrayList<>();
        double t = 0, b = 0, sl = 0;
        int cnt = 0;
        for (ReportData.Settle s : settle) {
            rows.add(new Object[]{s.barber, s.count, s.total, s.barberShare, s.salonShare});
            cnt += s.count;
            t += s.total;
            b += s.barberShare;
            sl += s.salonShare;
        }
        putTable(llSettlement, new String[]{"آرایشگر", "تعداد", "جمع", "سهم آرایشگر", "سهم سالن"}, rows,
                new Object[]{"جمع", cnt, t, b, sl}, "سرویسی برای آرایشگران در این ماه ثبت نشده است",
                new TableUi.OnRow() {
                    @Override
                    public void onRow(int i) {
                        showSlip(r, settle.get(i));
                    }
                });

        rows = new ArrayList<>();
        for (ReportData.Exp e : r.exps) rows.add(new Object[]{e.date, e.desc, e.amount});
        putTable(llExpenses, new String[]{"تاریخ", "شرح", "مبلغ"}, rows,
                new Object[]{"جمع", "", r.expensesTotal}, "هزینه‌ای در این ماه ثبت نشده است", null);

        rows = new ArrayList<>();
        double tp = 0, tb = 0, ts = 0;
        for (ReportData.Svc s : r.svcs) {
            rows.add(new Object[]{s.date, s.barber, s.service, s.price, s.barberShare, s.salonShare});
            tp += s.price;
            tb += s.barberShare;
            ts += s.salonShare;
        }
        putTable(llServices, new String[]{"تاریخ", "آرایشگر", "خدمت", "مبلغ", "سهم آرایشگر", "سهم سالن"}, rows,
                new Object[]{"جمع", "", "", tp, tb, ts}, "سرویسی در این ماه ثبت نشده است", null);

        rows = new ArrayList<>();
        for (ReportData.Appt a : r.appts) rows.add(new Object[]{a.date, a.time, a.customer, a.service, a.price});
        putTable(llTableAppts, new String[]{"تاریخ", "ساعت", "مشتری", "خدمت", "مبلغ"}, rows,
                new Object[]{"جمع", "", "", "", r.income}, "نوبت انجام‌شده‌ای در این ماه نیست", null);

        rows = new ArrayList<>();
        double pr = 0, pp = 0;
        for (Db.ProductSale s : psales) {
            rows.add(new Object[]{JalaliCalendar.gregorianStringToJalali(s.date), s.name, s.qty, s.unitPrice,
                    s.total(), s.profit()});
            pr += s.total();
            pp += s.profit();
        }
        putTable(llTableProducts, new String[]{"تاریخ", "محصول", "تعداد", "قیمت واحد", "جمع", "سود"}, rows,
                new Object[]{"جمع", "", "", "", pr, pp}, "در این ماه فروش محصولی ثبت نشده است", null);
    }

    private void showSlip(final ReportData r, final ReportData.Settle s) {
        BarberSettleUi.showSlip(act, r, s, new Runnable() {
            @Override
            public void run() {
                load();
            }
        });
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
