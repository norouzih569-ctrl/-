package com.barber.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** ویرایش لیست قیمت و تخفیف پیش‌فرض هر نوع لایسنس. */
public final class PriceList {

    private PriceList() {
    }

    public static void show(final MainActivity act) {
        final Db db = Db.get(act);
        final List<Models.PriceRow> rows = db.getPrices();
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_prices, null);
        LinearLayout box = v.findViewById(R.id.prices_box);

        final List<EditText> prices = new ArrayList<EditText>();
        final List<EditText> discounts = new ArrayList<EditText>();
        for (Models.PriceRow p : rows) {
            TextView t = new TextView(act);
            t.setText(Labels.licenseType(p.type) + " (" + LicenseCodec.daysForType(p.type) + " روز)");
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            t.setPadding(0, Ui.dp(act, 12), 0, Ui.dp(act, 4));
            box.addView(t);

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            EditText etP = new EditText(act);
            etP.setHint("قیمت (تومان)");
            etP.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etP.setText(Fmt.plain(p.price));
            row.addView(etP, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
            EditText etD = new EditText(act);
            etD.setHint("تخفیف ٪");
            etD.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            etD.setText(Fmt.percent(p.discountPercent).replace("%", ""));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(Ui.dp(act, 8));
            row.addView(etD, lp);
            box.addView(row);
            prices.add(etP);
            discounts.add(etD);
        }
        TextView hint = new TextView(act);
        hint.setText("«تخفیف ٪» پیش‌فرض است (مثلاً تخفیف جشنواره) و هنگام صدور برای هر مشتری قابل تغییر است.");
        hint.setTextSize(12);
        hint.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        hint.setPadding(0, Ui.dp(act, 12), 0, 0);
        box.addView(hint);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("لیست قیمت و تخفیف")
                .setView(v)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long[] newPrice = new long[rows.size()];
                double[] newDisc = new double[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    Double p = Fmt.parsePrice(prices.get(i).getText().toString());
                    if (p == null || p < 0) {
                        prices.get(i).setError("قیمت نامعتبر");
                        return;
                    }
                    Double d = Fmt.parsePrice(discounts.get(i).getText().toString());
                    if (d == null) d = 0.0;
                    if (d > 100) {
                        discounts.get(i).setError("حداکثر 100");
                        return;
                    }
                    newPrice[i] = Math.round(p);
                    newDisc[i] = d;
                }
                for (int i = 0; i < rows.size(); i++) db.setPrice(rows.get(i).type, newPrice[i], newDisc[i]);
                dialog.dismiss();
                Ui.toast(act, "لیست قیمت ذخیره شد");
            }
        });
    }
}
