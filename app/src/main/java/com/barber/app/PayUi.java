package com.barber.app;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** بخش انتخاب روش پرداخت و مبلغ دریافتی (برای فرم نوبت، «انجام شد» و ثبت پرداخت بدهی) */
public final class PayUi {

    /** بخش پرداخت: روش + مبلغ دریافتی (خالی = کل مبلغ) */
    public static final class Section {
        public final LinearLayout view;
        public final RadioGroup group;
        public final EditText etPaid;
        public final TextView hint;

        Section(LinearLayout v, RadioGroup g, EditText e, TextView h) {
            view = v;
            group = g;
            etPaid = e;
            hint = h;
        }

        public String method() {
            int idx = group.indexOfChild(group.findViewById(group.getCheckedRadioButtonId()));
            return Pay.methodAt(idx);
        }

        /** مبلغ دریافتی؛ null اگر نامعتبر (خطا روی فیلد گذاشته می‌شود) */
        public Double paid(double total) {
            Double p = Pay.resolvePaid(etPaid.getText().toString(), total);
            if (p == null) etPaid.setError("بین ۰ و کل مبلغ (" + Fmt.price(total) + ")");
            return p;
        }

        /** متن راهنما: چقدر بدهی می‌ماند */
        public void updateHint(double total) {
            Double p = Pay.resolvePaid(etPaid.getText().toString(), total);
            if (p == null) {
                hint.setText("");
            } else if (total - p > 0.5) {
                hint.setText("⚠️ " + Fmt.money(total - p) + " به عنوان بدهی مشتری ثبت می‌شود");
            } else {
                hint.setText("✓ تسویه کامل");
            }
        }
    }

    private PayUi() {
    }

    public static Section build(final MainActivity act, final java.util.concurrent.Callable<Double> totalSupplier) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView t = new TextView(act);
        t.setText("روش پرداخت");
        t.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        t.setPadding(0, Ui.dp(act, 8), 0, 0);
        box.addView(t);

        RadioGroup g = new RadioGroup(act);
        g.setOrientation(RadioGroup.HORIZONTAL);
        g.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < Pay.LABELS.length; i++) {
            RadioButton rb = new RadioButton(act);
            rb.setId(View.generateViewId());
            rb.setText(Pay.LABELS[i]);
            g.addView(rb, new RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        ((RadioButton) g.getChildAt(0)).setChecked(true);
        box.addView(g);

        final EditText et = new EditText(act);
        et.setHint("مبلغ دریافتی (خالی = کل مبلغ)");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setMaxLines(1);
        box.addView(et);

        final TextView hint = new TextView(act);
        hint.setTextSize(12.5f);
        hint.setPadding(Ui.dp(act, 4), 0, Ui.dp(act, 4), 0);
        box.addView(hint);

        final Section s = new Section(box, g, et, hint);
        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence x, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence x, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                try {
                    s.updateHint(totalSupplier.call());
                } catch (Exception ignored) {
                }
            }
        });
        return s;
    }

    public interface OnPay {
        void onPay(double amount, String method);
    }

    /** پنجره‌ی ثبت پرداخت بدهی: مبلغ (پیش‌فرض کل مانده) و روش */
    public static void showDebtPayment(final MainActivity act, String title, final double remaining,
                                       final OnPay cb) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 4));

        TextView info = new TextView(act);
        info.setText("مانده‌ی بدهی: " + Fmt.money(remaining));
        info.setTextSize(15);
        box.addView(info);

        final Section sec = build(act, new java.util.concurrent.Callable<Double>() {
            @Override
            public Double call() {
                return remaining;
            }
        });
        sec.etPaid.setHint("مبلغ پرداخت‌شده (خالی = کل مانده)");
        box.addView(sec.view);

        final AlertDialog d = new MaterialAlertDialogBuilder(act)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("ثبت پرداخت", null)
                .setNegativeButton("انصراف", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double p = sec.paid(remaining);
                if (p == null) return;
                if (p <= 0) {
                    sec.etPaid.setError("مبلغ باید بیشتر از صفر باشد");
                    return;
                }
                d.dismiss();
                cb.onPay(p, sec.method());
            }
        });
    }
}
