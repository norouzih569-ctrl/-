package com.barber.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** تب گزارش ماهانه: درآمد، سهم سالن، هزینه‌ها و مانده. */
public class ReportsTab implements Refreshable {

    private final MainActivity act;
    private final Spinner spMonth;
    private final Spinner spYear;
    private final TextView statIncome;
    private final TextView statSalonShare;
    private final TextView statExpenses;
    private final TextView statBalance;
    private final LinearLayout llExpenses;
    private final LinearLayout llServices;
    private final List<Integer> years = new ArrayList<>();

    public ReportsTab(MainActivity activity, View root) {
        this.act = activity;
        spMonth = root.findViewById(R.id.sp_report_month);
        spYear = root.findViewById(R.id.sp_report_year);
        statIncome = root.findViewById(R.id.stat_income);
        statSalonShare = root.findViewById(R.id.stat_salon_share);
        statExpenses = root.findViewById(R.id.stat_expenses);
        statBalance = root.findViewById(R.id.stat_balance);
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
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        int month = spMonth.getSelectedItemPosition() + 1;
        int yearPos = spYear.getSelectedItemPosition();
        if (month < 1 || yearPos < 0 || yearPos >= years.size()) return;
        int year = years.get(yearPos);

        String[] range = JalaliCalendar.monthRangeIso(year, month);
        Db db = Db.get(act);

        double income = db.incomeBetween(range[0], range[1]);
        List<Db.Expense> expenses = db.getExpensesBetween(range[0], range[1]);
        List<Db.BarberService> services = db.getBarberServicesBetween(range[0], range[1]);

        double totalExpenses = 0;
        for (Db.Expense e : expenses) totalExpenses += e.amount;
        double salonShare = 0;
        for (Db.BarberService s : services) salonShare += s.salonShare;

        double balance = (income + salonShare) - totalExpenses;

        statIncome.setText(Fmt.money(income));
        statSalonShare.setText(Fmt.money(salonShare));
        statExpenses.setText(Fmt.money(totalExpenses));
        statBalance.setText(Fmt.money(balance));
        statBalance.setTextColor(balance >= 0 ? 0xFF43A047 : 0xFFE53935);

        List<RowAdapter.Row> expenseRows = new ArrayList<>();
        for (Db.Expense e : expenses) {
            expenseRows.add(new RowAdapter.Row(e.id, JalaliCalendar.gregorianStringToJalali(e.date),
                    e.description, "", Fmt.price(e.amount)));
        }
        fill(llExpenses, expenseRows, "هزینه‌ای در این ماه ثبت نشده است");

        List<RowAdapter.Row> serviceRows = new ArrayList<>();
        for (Db.BarberService s : services) {
            String sub = s.service + " | سهم آرایشگر: " + Fmt.price(s.barberShare)
                    + " | سهم سالن: " + Fmt.price(s.salonShare);
            serviceRows.add(new RowAdapter.Row(s.id, JalaliCalendar.gregorianStringToJalali(s.date),
                    s.barberName, sub, Fmt.price(s.price)));
        }
        fill(llServices, serviceRows, "سرویسی در این ماه ثبت نشده است");
    }

    private void fill(LinearLayout container, List<RowAdapter.Row> rows, String emptyText) {
        container.removeAllViews();
        if (rows.isEmpty()) {
            TextView tv = new TextView(act);
            tv.setText(emptyText);
            tv.setPadding(12, 12, 12, 12);
            container.addView(tv);
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
