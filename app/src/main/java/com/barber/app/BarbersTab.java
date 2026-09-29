package com.barber.app;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/** تب آرایشگران درصدی و ثبت سرویس‌های آن‌ها. */
public class BarbersTab implements Refreshable {

    private static final int SERVICE_LIMIT = 50;

    private final MainActivity act;
    private final RowAdapter barberAdapter;
    private final RowAdapter serviceAdapter;
    private List<Db.Barber> barbers = new ArrayList<>();
    private List<Db.BarberService> services = new ArrayList<>();

    public BarbersTab(MainActivity activity, View root) {
        this.act = activity;

        ListView listBarbers = root.findViewById(R.id.list_barbers);
        TextView emptyBarbers = root.findViewById(R.id.empty_barbers);
        barberAdapter = new RowAdapter(act);
        listBarbers.setAdapter(barberAdapter);
        listBarbers.setEmptyView(emptyBarbers);

        ListView listServices = root.findViewById(R.id.list_barber_services);
        TextView emptyServices = root.findViewById(R.id.empty_barber_services);
        serviceAdapter = new RowAdapter(act);
        listServices.setAdapter(serviceAdapter);
        listServices.setEmptyView(emptyServices);

        root.findViewById(R.id.btn_add_barber).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBarberDialog();
            }
        });
        root.findViewById(R.id.btn_add_barber_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showServiceDialog();
            }
        });
        listBarbers.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < barbers.size()) {
                    confirmDeleteBarber(barbers.get(position));
                }
            }
        });
        listServices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < services.size()) {
                    confirmDeleteService(services.get(position));
                }
            }
        });
    }

    @Override
    public void refresh() {
        Db db = Db.get(act);
        barbers = db.getBarbers();
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Db.Barber b : barbers) {
            rows.add(new RowAdapter.Row(b.id, "", b.name, "سهم سالن", Fmt.percent(b.percent)));
        }
        barberAdapter.setRows(rows);

        services = db.getRecentBarberServices(SERVICE_LIMIT);
        List<RowAdapter.Row> srows = new ArrayList<>();
        for (Db.BarberService s : services) {
            srows.add(new RowAdapter.Row(s.id, JalaliCalendar.gregorianStringToJalali(s.date),
                    s.barberName, s.service, Fmt.price(s.price)));
        }
        serviceAdapter.setRows(srows);
    }

    private void confirmDeleteBarber(final Db.Barber b) {
        new AlertDialog.Builder(act)
                .setTitle("حذف آرایشگر")
                .setMessage("آیا از حذف آرایشگر «" + b.name + "» مطمئن هستید؟\nسرویس‌های ثبت‌شده‌ی او هم حذف می‌شود.")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Db.get(act).deleteBarber(b.id);
                        act.refreshAll();
                        Ui.toast(act, "آرایشگر حذف شد");
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void confirmDeleteService(final Db.BarberService s) {
        new AlertDialog.Builder(act)
                .setTitle("حذف سرویس")
                .setMessage("سرویس «" + s.service + "» آرایشگر " + s.barberName + " حذف شود؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Db.get(act).deleteBarberService(s.id);
                        act.refreshAll();
                        Ui.toast(act, "سرویس حذف شد");
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showBarberDialog() {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_barber, null);
        final EditText etName = v.findViewById(R.id.et_barber_name);
        final EditText etPercent = v.findViewById(R.id.et_barber_percent);
        final AlertDialog dialog = new AlertDialog.Builder(act)
                .setTitle("آرایشگر جدید")
                .setView(v)
                .setPositiveButton("ثبت", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) {
                    etName.setError("نام آرایشگر وارد نشده است");
                    return;
                }
                Double percent = Fmt.parsePrice(etPercent.getText().toString());
                if (percent == null) {
                    etPercent.setError("درصد صحیح نیست");
                    return;
                }
                if (percent > 100) {
                    etPercent.setError("درصد باید بین ۰ تا ۱۰۰ باشد");
                    return;
                }
                Db.get(act).addBarber(name, percent);
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, "آرایشگر «" + name + "» اضافه شد");
            }
        });
    }

    private void showServiceDialog() {
        final Db db = Db.get(act);
        final List<Db.Barber> list = db.getBarbers();
        if (list.isEmpty()) {
            Ui.toastLong(act, "ابتدا یک آرایشگر اضافه کنید");
            return;
        }

        View v = LayoutInflater.from(act).inflate(R.layout.dialog_barber_service, null);
        final Spinner spBarber = v.findViewById(R.id.sp_bs_barber);
        final Spinner spService = v.findViewById(R.id.sp_bs_service);
        final EditText etPrice = v.findViewById(R.id.et_bs_price);
        final Button btnDate = v.findViewById(R.id.btn_bs_date);

        List<String> names = new ArrayList<>();
        for (Db.Barber b : list) {
            names.add(b.name + " (" + Fmt.percent(b.percent) + ")");
        }
        spBarber.setAdapter(Ui.spinnerAdapter(act, names));

        final List<String> services = new ArrayList<>();
        for (String s : AppConfig.SERVICES) services.add(s);
        spService.setAdapter(Ui.spinnerAdapter(act, services));
        etPrice.setText(Fmt.plain(AppConfig.getPrice(act, services.get(0))));

        final int[] lastPos = {0};
        spService.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == lastPos[0]) return;
                lastPos[0] = position;
                etPrice.setText(Fmt.plain(AppConfig.getPrice(act, services.get(position))));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final int[] date = act.selectedDate.clone();
        btnDate.setText("تاریخ: " + JalaliCalendar.format(date[0], date[1], date[2]));
        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JalaliPicker.show(act, date, new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int year, int month, int day) {
                        date[0] = year;
                        date[1] = month;
                        date[2] = day;
                        btnDate.setText("تاریخ: " + JalaliCalendar.format(year, month, day));
                    }
                });
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(act)
                .setTitle("ثبت سرویس آرایشگر")
                .setView(v)
                .setPositiveButton("ثبت", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Double price = Fmt.parsePrice(etPrice.getText().toString());
                if (price == null) {
                    etPrice.setError("قیمت صحیح نیست");
                    return;
                }
                Db.Barber barber = list.get(spBarber.getSelectedItemPosition());
                String service = services.get(spService.getSelectedItemPosition());
                String iso = JalaliCalendar.jalaliToGregorianString(date[0], date[1], date[2]);
                double[] shares = db.addBarberService(barber.id, iso, service, price);
                dialog.dismiss();
                act.refreshAll();
                new AlertDialog.Builder(act)
                        .setTitle("ثبت شد")
                        .setMessage("آرایشگر: " + barber.name + "\n"
                                + "خدمت: " + service + "\n"
                                + "قیمت: " + Fmt.money(price) + "\n"
                                + "سهم آرایشگر: " + Fmt.money(shares[0]) + "\n"
                                + "سهم سالن: " + Fmt.money(shares[1]))
                        .setPositiveButton("باشه", null)
                        .show();
            }
        });
    }
}
