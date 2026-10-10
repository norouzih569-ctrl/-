package com.barber.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** صدور کد لایسنس برای یک مشتری و دستگاه او. */
public final class IssueDialog {

    private IssueDialog() {
    }

    private static double pct(EditText et) {
        Double v = Fmt.parsePrice(et.getText().toString());
        return v == null ? 0 : Pricing.clampPercent(v);
    }

    /** انتخاب بین لایسنس جدید و کد جابجایی دستگاه */
    public static void chooser(final MainActivity act, final long customerId) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("صدور کد")
                .setItems(new CharSequence[]{"🔑 لایسنس جدید یا تمدید", "📱 کد جابجایی دستگاه (گوشی جدید)"},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                if (which == 0) show(act, customerId);
                                else TransferDialog.show(act, customerId);
                            }
                        })
                .show();
    }

    /** customerId = 0 یعنی از فهرست انتخاب شود */
    public static void show(final MainActivity act, long customerId) {
        final Db db = Db.get(act);
        final List<Models.Customer> customers = db.listCustomers("");
        if (customers.isEmpty()) {
            Ui.toastLong(act, "اول باید یک مشتری ثبت کنید");
            return;
        }
        final List<Models.PriceRow> plans = new ArrayList<Models.PriceRow>();
        for (Models.PriceRow p : db.getPrices()) {
            if (LicenseCodec.daysForType(p.type) > 0) plans.add(p);
        }
        final List<Models.Visitor> visitors = new ArrayList<Models.Visitor>();
        visitors.addAll(db.listVisitors(true));

        View v = LayoutInflater.from(act).inflate(R.layout.dialog_issue, null);
        final Spinner spCustomer = v.findViewById(R.id.sp_issue_customer);
        final Spinner spPlan = v.findViewById(R.id.sp_issue_plan);
        final Spinner spVisitor = v.findViewById(R.id.sp_issue_visitor);
        final EditText etDevice = v.findViewById(R.id.et_issue_device);
        final EditText etDiscount = v.findViewById(R.id.et_issue_discount);
        final EditText etCommission = v.findViewById(R.id.et_issue_commission);
        final EditText etPaid = v.findViewById(R.id.et_issue_paid);
        final EditText etNote = v.findViewById(R.id.et_issue_note);
        final TextView tvSummary = v.findViewById(R.id.tv_issue_summary);

        // مشتری
        List<String> cNames = new ArrayList<String>();
        int cSel = 0;
        for (int i = 0; i < customers.size(); i++) {
            Models.Customer c = customers.get(i);
            cNames.add(c.shopName + (c.ownerName.isEmpty() ? "" : " – " + c.ownerName));
            if (c.id == customerId) cSel = i;
        }
        spCustomer.setAdapter(Ui.spinnerAdapter(act, cNames));
        spCustomer.setSelection(cSel);

        // پلن‌ها
        List<String> pNames = new ArrayList<String>();
        for (Models.PriceRow p : plans) {
            pNames.add(Labels.licenseType(p.type) + " – " + Fmt.price(p.price)
                    + (p.discountPercent > 0 ? " (تخفیف " + Fmt.percent(p.discountPercent) + ")" : ""));
        }
        spPlan.setAdapter(Ui.spinnerAdapter(act, pNames));
        int defPlan = 0;
        for (int i = 0; i < plans.size(); i++) if (plans.get(i).type == LicenseCodec.TYPE_1Y) defPlan = i;
        spPlan.setSelection(defPlan);

        // ویزیتورها
        List<String> vNames = new ArrayList<String>();
        vNames.add("بدون ویزیتور");
        for (Models.Visitor vis : visitors) vNames.add(vis.name + " (" + Fmt.percent(vis.percent) + ")");
        spVisitor.setAdapter(Ui.spinnerAdapter(act, vNames));

        final Runnable update = new Runnable() {
            @Override
            public void run() {
                int pi = spPlan.getSelectedItemPosition();
                if (pi < 0 || pi >= plans.size()) return;
                IssueLogic.Summary s = IssueLogic.summarize(plans.get(pi).price, pct(etDiscount), pct(etCommission));
                StringBuilder sb = new StringBuilder();
                sb.append("قیمت پایه: ").append(Fmt.money(s.base));
                if (s.discountAmount > 0) sb.append("\nتخفیف: ").append(Fmt.money(s.discountAmount));
                sb.append("\nمبلغ نهایی: ").append(Fmt.money(s.finalPrice));
                if (spVisitor.getSelectedItemPosition() > 0) {
                    sb.append("\nپورسانت ویزیتور: ").append(Fmt.money(s.commission));
                }
                tvSummary.setText(sb.toString());
            }
        };
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                update.run();
            }
        };
        etDiscount.addTextChangedListener(watcher);
        etCommission.addTextChangedListener(watcher);

        spPlan.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < plans.size()) {
                    etDiscount.setText(Fmt.percent(plans.get(pos).discountPercent).replace("%", ""));
                }
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spVisitor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos <= 0) {
                    etCommission.setText("0");
                    etCommission.setEnabled(false);
                } else {
                    etCommission.setEnabled(true);
                    etCommission.setText(Fmt.percent(visitors.get(pos - 1).percent).replace("%", ""));
                }
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        // با عوض شدن مشتری، ویزیتور معرفش انتخاب می‌شود
        spCustomer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                long vid = customers.get(pos).visitorId;
                int sel = 0;
                for (int i = 0; i < visitors.size(); i++) if (visitors.get(i).id == vid) sel = i + 1;
                spVisitor.setSelection(sel);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("صدور لایسنس")
                .setView(v)
                .setPositiveButton("ساخت کد", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Models.Customer cust = customers.get(Math.max(0, spCustomer.getSelectedItemPosition()));
                int pi = spPlan.getSelectedItemPosition();
                if (pi < 0 || pi >= plans.size()) return;
                final Models.PriceRow plan = plans.get(pi);

                final String device = IssueLogic.parseDevice(etDevice.getText().toString());
                if (device == null) {
                    etDevice.setError("شناسه باید ۱۰ حرف/رقم باشد (مثل K7M2A-9QX4T)");
                    return;
                }
                final long today = IssueLogic.todayEpochDay();
                final String code = IssueLogic.makeCode(LicenseKeys.SECRET, device, plan.type, today);
                if (code == null) {
                    Ui.toastLong(act, "ساعت یا تاریخ گوشی درست نیست؛ کد ساخته نشد");
                    return;
                }
                final double discount = pct(etDiscount);
                final int vpos = spVisitor.getSelectedItemPosition();
                final long visitorId = vpos > 0 ? visitors.get(vpos - 1).id : 0;
                final double commission = visitorId > 0 ? pct(etCommission) : 0;
                final IssueLogic.Summary s = IssueLogic.summarize(plan.price, discount, commission);

                Double paidD = Fmt.parsePrice(etPaid.getText().toString());
                final long paid = paidD == null ? 0 : Math.round(paidD);
                if (paid > s.finalPrice) {
                    etPaid.setError("بیشتر از مبلغ نهایی (" + Fmt.price(s.finalPrice) + ") است");
                    return;
                }

                // هشدار: همین شناسه قبلاً برای مشتری دیگری صادر شده؟
                boolean otherOwner = false;
                for (Models.License old : db.listLicenses(0)) {
                    if (old.deviceId.equals(device) && old.customerId != cust.id && !old.voided) otherOwner = true;
                }
                final Runnable doIssue = new Runnable() {
                    @Override
                    public void run() {
                        Models.License l = new Models.License();
                        l.customerId = cust.id;
                        l.deviceId = device;
                        l.type = plan.type;
                        l.code = code;
                        l.issueDate = JalaliCalendar.todayGregorianString();
                        l.basePrice = plan.price;
                        l.discountPercent = discount;
                        l.visitorId = visitorId;
                        l.commissionPercent = commission;
                        l.notes = etNote.getText().toString();
                        long id = db.insertLicense(l);
                        if (paid > 0 && id > 0) {
                            Models.Payment p = new Models.Payment();
                            p.customerId = cust.id;
                            p.licenseId = id;
                            p.amount = paid;
                            p.payDate = l.issueDate;
                            p.method = Labels.PAY_METHODS[0];
                            p.note = "پرداخت هنگام صدور";
                            db.addPayment(p);
                        }
                        dialog.dismiss();
                        act.refreshAll();
                        showResult(act, cust, code, plan.type, device);
                    }
                };
                if (otherOwner) {
                    new MaterialAlertDialogBuilder(act)
                            .setTitle("شناسه‌ی تکراری")
                            .setMessage("این شناسه‌ی دستگاه قبلاً برای مشتری دیگری کد گرفته است. باز هم صادر شود؟")
                            .setPositiveButton("صدور", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int which) {
                                    doIssue.run();
                                }
                            })
                            .setNegativeButton("انصراف", null)
                            .show();
                } else {
                    doIssue.run();
                }
            }
        });
    }

    /** نمایش کد ساخته‌شده با کپی و ارسال */
    public static void showResult(final MainActivity act, final Models.Customer cust, final String code,
                                  final int type, final String device) {
        final String msg = IssueLogic.message(code, type, device);
        new MaterialAlertDialogBuilder(act)
                .setTitle("✅ کد ساخته شد")
                .setMessage(cust.shopName + "\n" + Labels.licenseType(type) + "\n\n" + code
                        + "\n\nشناسه‌ی دستگاه: " + device)
                .setPositiveButton("ارسال", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        sendChooser(act, cust, msg);
                    }
                })
                .setNeutralButton("کپی کد", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        Share.copy(act, "license", code);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    /** انتخاب روش ارسال: پیامک، واتساپ یا سایر برنامه‌ها */
    public static void sendChooser(final MainActivity act, final Models.Customer cust, final String msg) {
        final boolean hasMobile = cust != null && !cust.mobile.isEmpty();
        CharSequence[] items = hasMobile
                ? new CharSequence[]{"📩 پیامک به " + cust.mobile, "💬 واتساپ", "📤 سایر برنامه‌ها", "📋 کپی متن"}
                : new CharSequence[]{"📤 ارسال با برنامه‌ها", "📋 کپی متن"};
        new MaterialAlertDialogBuilder(act)
                .setTitle("ارسال کد")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (hasMobile) {
                            if (which == 0) Contact.sms(act, cust.mobile, msg);
                            else if (which == 1) Contact.whatsapp(act, cust.mobile, msg);
                            else if (which == 2) Share.text(act, msg);
                            else Share.copy(act, "license", msg);
                        } else {
                            if (which == 0) Share.text(act, msg);
                            else Share.copy(act, "license", msg);
                        }
                    }
                })
                .show();
    }
}
