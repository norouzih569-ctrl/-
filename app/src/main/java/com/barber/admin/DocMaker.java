package com.barber.admin;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** ساخت پیش‌فاکتور، قرارداد و لیست قیمت (PDF) و تنظیم مشخصات فروشنده. */
public final class DocMaker {

    private DocMaker() {
    }

    private static String today() {
        int[] j = JalaliCalendar.today();
        return Fa.digits(JalaliCalendar.format(j[0], j[1], j[2]));
    }

    /** شماره‌ی سند: تاریخ شمسی + شماره‌ی ترتیبی سه‌رقمی */
    private static String nextDocNo(Db db) {
        int seq = 1;
        try {
            seq = Integer.parseInt(db.getSetting("doc_seq", "0")) + 1;
        } catch (NumberFormatException ignored) {
            // از ۱ شروع می‌شود
        }
        db.setSetting("doc_seq", String.valueOf(seq));
        int[] j = JalaliCalendar.today();
        return Fa.digits(String.format(java.util.Locale.US, "%04d%02d%02d-%03d", j[0], j[1], j[2], seq));
    }

    private static File exportFile(MainActivity act, String name) {
        File dir = new File(act.getCacheDir(), "exports");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    /** نوشتن PDF و نمایش پنجره‌ی باز کردن/ارسال */
    private static void finish(final MainActivity act, Doc doc, String fileName, String title) {
        final File f = exportFile(act, fileName);
        try {
            PdfRenderer.write(act, doc, f);
        } catch (Exception e) {
            Ui.toastLong(act, "ساخت PDF انجام نشد: " + e.getMessage());
            return;
        }
        new MaterialAlertDialogBuilder(act)
                .setTitle("✅ " + title + " ساخته شد")
                .setMessage("فایل PDF آماده است.")
                .setPositiveButton("باز کردن", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        Share.open(act, f, "application/pdf");
                    }
                })
                .setNeutralButton("ارسال", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        Share.file(act, f, "application/pdf");
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    // ================= لیست قیمت =================

    public static void priceListPdf(MainActivity act) {
        Db db = Db.get(act);
        Doc doc = Docs.priceList(Seller.load(db), db.getPrices(), today());
        finish(act, doc, "price_list.pdf", "لیست قیمت");
    }

    // ================= پیش‌فاکتور و قرارداد برای یک مشتری =================

    public static void forCustomer(final MainActivity act, long customerId) {
        final Db db = Db.get(act);
        final Models.Customer c = db.getCustomer(customerId);
        if (c == null) return;
        final List<Models.PriceRow> plans = db.getPrices();
        if (plans.isEmpty()) return;

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 8));

        box.addView(label(act, "نوع سند"));
        final Spinner spDoc = new Spinner(act);
        List<String> docs = new ArrayList<String>();
        docs.add("پیش‌فاکتور");
        docs.add("قرارداد (همراه با لیست قیمت)");
        spDoc.setAdapter(Ui.spinnerAdapter(act, docs));
        box.addView(spDoc);

        box.addView(label(act, "نوع لایسنس"));
        final Spinner spPlan = new Spinner(act);
        List<String> names = new ArrayList<String>();
        int def = 0;
        for (int i = 0; i < plans.size(); i++) {
            Models.PriceRow p = plans.get(i);
            names.add(Labels.licenseType(p.type) + " – " + Fmt.price(p.price));
            if (p.type == LicenseCodec.TYPE_1Y) def = i;
        }
        spPlan.setAdapter(Ui.spinnerAdapter(act, names));
        spPlan.setSelection(def);
        box.addView(spPlan);

        final EditText etDiscount = new EditText(act);
        etDiscount.setHint("درصد تخفیف");
        etDiscount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(act, 8);
        box.addView(etDiscount, lp);

        final EditText etValid = new EditText(act);
        etValid.setHint("اعتبار پیش‌فاکتور (روز)");
        etValid.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etValid.setText("7");
        box.addView(etValid, lp);

        spPlan.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                etDiscount.setText(Fmt.percent(plans.get(pos).discountPercent).replace("%", ""));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        spDoc.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                etValid.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);
        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("سند برای " + c.shopName)
                .setView(scroll)
                .setPositiveButton("ساخت PDF", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Models.PriceRow plan = plans.get(Math.max(0, spPlan.getSelectedItemPosition()));
                Double d = Fmt.parsePrice(etDiscount.getText().toString());
                double discount = d == null ? 0 : Pricing.clampPercent(d);
                Seller seller = Seller.load(db);
                String docNo = nextDocNo(db);
                boolean proforma = spDoc.getSelectedItemPosition() == 0;
                dialog.dismiss();
                if (proforma) {
                    Double vd = Fmt.parsePrice(etValid.getText().toString());
                    int valid = vd == null || vd < 1 ? 7 : (int) Math.min(365, Math.round(vd));
                    Doc doc = Docs.proforma(seller, c, plan.type, plan.price, discount, valid, docNo, today());
                    finish(act, doc, "proforma_" + Validate.digits(docNo) + ".pdf", "پیش‌فاکتور");
                } else {
                    Doc doc = Docs.contract(seller, c, plan.type, plan.price, discount, db.getPrices(), docNo, today());
                    finish(act, doc, "contract_" + Validate.digits(docNo) + ".pdf", "قرارداد");
                }
            }
        });
    }

    private static TextView label(MainActivity act, String t) {
        TextView v = new TextView(act);
        v.setText(t);
        v.setTextSize(12.5f);
        v.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        v.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 2));
        return v;
    }

    // ================= مشخصات فروشنده =================

    public static void sellerInfo(final MainActivity act) {
        final Db db = Db.get(act);
        Seller s = Seller.load(db);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), Ui.dp(act, 8));

        final EditText name = field(act, box, "نام شخص / شرکت فروشنده", s.name, false);
        final EditText brand = field(act, box, "نام برنامه (در سربرگ)", s.brand, false);
        final EditText nid = field(act, box, "کد ملی / شناسه ملی", s.nationalId, true);
        final EditText eco = field(act, box, "کد اقتصادی", s.economicCode, true);
        final EditText phone = field(act, box, "تلفن پشتیبانی", s.phone, true);
        final EditText address = field(act, box, "نشانی", s.address, false);
        final EditText bank = field(act, box, "اطلاعات پرداخت (شماره کارت / شبا)", s.bank, false);
        final EditText signer = field(act, box, "نام امضاکننده", s.signer, false);
        final EditText vat = field(act, box, "مالیات بر ارزش افزوده ٪ (۰ = نمایش داده نشود)",
                s.vatPercent == 0 ? "0" : Fmt.percent(s.vatPercent).replace("%", ""), true);

        TextView hint = new TextView(act);
        hint.setText("لوگو و مهر بعداً اضافه می‌شود؛ جای آن‌ها در PDF آماده است.");
        hint.setTextSize(12);
        hint.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        hint.setPadding(0, Ui.dp(act, 10), 0, 0);
        box.addView(hint);

        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);
        new MaterialAlertDialogBuilder(act)
                .setTitle("اطلاعات فروشنده")
                .setView(scroll)
                .setPositiveButton("ذخیره", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        Seller n = new Seller();
                        n.name = name.getText().toString();
                        n.brand = brand.getText().toString().trim().isEmpty()
                                ? new Seller().brand : brand.getText().toString();
                        n.nationalId = nid.getText().toString();
                        n.economicCode = eco.getText().toString();
                        n.phone = phone.getText().toString();
                        n.address = address.getText().toString();
                        n.bank = bank.getText().toString();
                        n.signer = signer.getText().toString();
                        Double v = Fmt.parsePrice(vat.getText().toString());
                        n.vatPercent = v == null ? 0 : v;
                        n.save(db);
                        Ui.toast(act, "ذخیره شد");
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private static EditText field(MainActivity act, LinearLayout box, String hint, String value, boolean numeric) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(numeric ? (android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
                : android.text.InputType.TYPE_CLASS_TEXT);
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(act, 8);
        box.addView(e, lp);
        return e;
    }
}
