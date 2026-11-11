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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    private final GridMode.Host barberGrid;
    private final GridMode.Host serviceGrid;

    public BarbersTab(MainActivity activity, View root) {
        this.act = activity;

        ListView listBarbers = root.findViewById(R.id.list_barbers);
        TextView emptyBarbers = root.findViewById(R.id.empty_barbers);
        barberAdapter = new RowAdapter(act);
        listBarbers.setAdapter(barberAdapter);
        listBarbers.setEmptyView(emptyBarbers);
        barberGrid = new GridMode.Host(act, listBarbers);

        ListView listServices = root.findViewById(R.id.list_barber_services);
        TextView emptyServices = root.findViewById(R.id.empty_barber_services);
        serviceAdapter = new RowAdapter(act);
        listServices.setAdapter(serviceAdapter);
        listServices.setEmptyView(emptyServices);
        serviceGrid = new GridMode.Host(act, listServices);

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
                    showBarberActions(barbers.get(position));
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
            rows.add(new RowAdapter.Row(b.id, "", b.name, "سهم سالن", Fmt.percent(b.percent), true));
        }
        barberAdapter.setRows(rows);
        List<Object[]> brows = new ArrayList<>();
        for (Db.Barber b : barbers) brows.add(new Object[]{b.name, Fmt.percent(b.percent)});
        barberGrid.render(new String[]{"آرایشگر", "سهم سالن"}, brows, null, new TableUi.OnRow() {
            @Override
            public void onRow(int i) {
                if (i >= 0 && i < barbers.size()) showBarberActions(barbers.get(i));
            }
        }, true);

        services = db.getRecentBarberServices(SERVICE_LIMIT);
        List<RowAdapter.Row> srows = new ArrayList<>();
        for (Db.BarberService s : services) {
            srows.add(new RowAdapter.Row(s.id, JalaliCalendar.gregorianStringToJalali(s.date),
                    s.barberName, s.service, Fmt.price(s.price)));
        }
        serviceAdapter.setRows(srows);
        List<Object[]> strows = new ArrayList<>();
        double ssum = 0;
        for (Db.BarberService s : services) {
            strows.add(new Object[]{JalaliCalendar.gregorianStringToJalali(s.date), s.barberName, s.service, s.price});
            ssum += s.price;
        }
        serviceGrid.render(new String[]{"تاریخ", "آرایشگر", "خدمت", "مبلغ"}, strows,
                new Object[]{"جمع", "", "", ssum}, new TableUi.OnRow() {
                    @Override
                    public void onRow(int i) {
                        if (i >= 0 && i < services.size()) confirmDeleteService(services.get(i));
                    }
                }, true);
    }

    private void confirmDeleteBarber(final Db.Barber b) {
        if (!act.canWrite()) return;
        new MaterialAlertDialogBuilder(act)
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
        if (!act.canWrite()) return;
        new MaterialAlertDialogBuilder(act)
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

    /** منوی هر آرایشگر: پرداخت، رسید و خروجی، ویرایش (شماره کارت)، حذف */
    private void showBarberActions(final Db.Barber b) {
        CharSequence[] items = {
                "💵 ثبت پرداخت به " + b.name,
                "🧾 رسید و خروجی این ماه (فقط کارکرد خودش)",
                "📋 پرداخت‌های انجام‌شده (لیست و حذف)",
                "✏️ ویرایش (نام، درصد، شماره کارت)",
                "🗑 حذف آرایشگر"
        };
        new MaterialAlertDialogBuilder(act)
                .setTitle(b.name + " — مانده‌ی کل: " + Fmt.money(Db.get(act).barberBalanceTotal(b.id)))
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) BarberSettleUi.addPayment(act, b.id, b.name, null);
                        else if (which == 1) BarberSettleUi.showSlipForMonth(act, b);
                        else if (which == 2) BarberSettleUi.listPayments(act, b);
                        else if (which == 3) showBarberDialog(b);
                        else confirmDeleteBarber(b);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showBarberDialog() {
        showBarberDialog(null);
    }

    /** existing == null: آرایشگر جدید؛ در غیر این صورت ویرایش */
    private void showBarberDialog(final Db.Barber existing) {
        if (!act.canWrite()) return;
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_barber, null);
        final EditText etName = v.findViewById(R.id.et_barber_name);
        final EditText etPercent = v.findViewById(R.id.et_barber_percent);
        final EditText etCard = v.findViewById(R.id.et_barber_card);
        if (existing != null) {
            etName.setText(existing.name);
            etPercent.setText(existing.percent == Math.floor(existing.percent)
                    ? Fmt.plain(existing.percent) : String.valueOf(existing.percent));
            etCard.setText(existing.card == null ? "" : existing.card);
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle(existing == null ? "آرایشگر جدید" : "ویرایش آرایشگر")
                .setView(Ui.scroll(v))
                .setPositiveButton(existing == null ? "ثبت" : "ذخیره", null)
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
                String card = etCard.getText().toString().trim();
                if (existing == null) {
                    Db.get(act).addBarber(name, percent, card);
                } else {
                    Db.get(act).updateBarber(existing.id, name, percent, card);
                }
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, existing == null ? "آرایشگر «" + name + "» اضافه شد" : "تغییرات ذخیره شد");
            }
        });
    }

    private void showServiceDialog() {
        if (!act.canWrite()) return;
        final Db db = Db.get(act);
        final List<Db.Barber> list = db.getBarbers();
        if (list.isEmpty()) {
            Ui.toastLong(act, "ابتدا یک آرایشگر اضافه کنید");
            return;
        }

        View v = LayoutInflater.from(act).inflate(R.layout.dialog_barber_service, null);
        final Spinner spBarber = v.findViewById(R.id.sp_bs_barber);
        final Button btnService = v.findViewById(R.id.btn_bs_service);
        final EditText etPrice = v.findViewById(R.id.et_bs_price);
        final Button btnDate = v.findViewById(R.id.btn_bs_date);

        List<String> names = new ArrayList<>();
        for (Db.Barber b : list) {
            names.add(b.name + " (" + Fmt.percent(b.percent) + ")");
        }
        spBarber.setAdapter(Ui.spinnerAdapter(act, names));

        final List<String> services = new ArrayList<>();
        services.addAll(AppConfig.getServices(act));
        final boolean[] sel = new boolean[services.size()];
        if (sel.length > 0) sel[0] = true;
        final Runnable refreshService = new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                double sum = 0;
                for (int i = 0; i < services.size(); i++) {
                    if (!sel[i]) continue;
                    if (sb.length() > 0) sb.append(" + ");
                    sb.append(services.get(i));
                    sum += AppConfig.getPrice(act, services.get(i));
                }
                btnService.setText(sb.length() == 0 ? "انتخاب خدمت‌ها" : "خدمت: " + sb);
                etPrice.setText(Fmt.plain(sum));
            }
        };
        btnService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final boolean[] tmp = sel.clone();
                String[] labels = new String[services.size()];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = services.get(i) + "   —   " + Fmt.price(AppConfig.getPrice(act, services.get(i)));
                }
                new MaterialAlertDialogBuilder(act)
                        .setTitle("خدمت‌ها (چند انتخابی)")
                        .setMultiChoiceItems(labels, tmp, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which, boolean checked) {
                                tmp[which] = checked;
                            }
                        })
                        .setPositiveButton("تأیید", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                System.arraycopy(tmp, 0, sel, 0, sel.length);
                                refreshService.run();
                            }
                        })
                        .setNegativeButton("انصراف", null)
                        .show();
            }
        });
        refreshService.run();

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

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("ثبت سرویس آرایشگر")
                .setView(Ui.scroll(v))
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
                StringBuilder sbs = new StringBuilder();
                for (int i = 0; i < services.size(); i++) {
                    if (!sel[i]) continue;
                    if (sbs.length() > 0) sbs.append(" + ");
                    sbs.append(services.get(i));
                }
                if (sbs.length() == 0) {
                    Ui.toast(act, "حداقل یک خدمت انتخاب کنید");
                    return;
                }
                String service = sbs.toString();
                String iso = JalaliCalendar.jalaliToGregorianString(date[0], date[1], date[2]);
                double[] shares = db.addBarberService(barber.id, iso, service, price);
                dialog.dismiss();
                act.refreshAll();
                new MaterialAlertDialogBuilder(act)
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
