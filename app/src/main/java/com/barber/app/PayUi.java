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

        // نسخه‌ی مستقیم: برای «کارت» و «واریز» دکمه‌ی نمایش QR حساب
        final TextView qrBtn = new TextView(act);
        qrBtn.setText("🔳 نمایش QR کارت برای مشتری");
        qrBtn.setTextSize(14);
        qrBtn.setTextColor(Ui.themeColor(act, R.attr.barberAccent, 0xFF1E6BE6));
        qrBtn.setPadding(Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 8));
        qrBtn.setVisibility(View.GONE);
        qrBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BankUi.pickAndShowQr(act);
            }
        });
        box.addView(qrBtn);
        if (AppConfig.extrasEnabled()) {
            g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    int idx = group.indexOfChild(group.findViewById(checkedId));
                    qrBtn.setVisibility(idx > 0 ? View.VISIBLE : View.GONE);
                }
            });
        }

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
                .setView(Ui.scroll(box))
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

/** حساب‌های بانکی آرایشگاه: ثبت تا ۱۰ حساب، تعیین حساب پیش‌فرض، اشتراک‌گذاری متن حساب برای مشتری */
final class BankUi {

    private BankUi() {
    }

    /** ۱۶ رقم کارت را چهارتا‌چهارتا نشان می‌دهد */
    static String fmtCard(String card) {
        if (card == null || card.length() != 16) return card == null ? "" : card;
        return card.substring(0, 4) + " " + card.substring(4, 8) + " " + card.substring(8, 12) + " " + card.substring(12);
    }

    /** الگوریتم لون برای شماره کارت */
    static boolean luhn(String d) {
        int sum = 0;
        boolean dbl = false;
        for (int i = d.length() - 1; i >= 0; i--) {
            int n = d.charAt(i) - '0';
            if (dbl) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    /** شبا: IR + ۲۴ رقم و کنترل mod 97 */
    static boolean validSheba(String s) {
        if (!s.matches("IR[0-9]{24}")) return false;
        String r = s.substring(4) + "1827" + s.substring(2, 4); // I=18 R=27
        int rem = 0;
        for (int i = 0; i < r.length(); i++) rem = (rem * 10 + (r.charAt(i) - '0')) % 97;
        return rem == 1;
    }

    /** QR شماره کارت (فقط متن ارقام) */
    static android.graphics.Bitmap qrBitmap(String text, int size) throws Exception {
        java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.HashMap<>();
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 2);
        com.google.zxing.common.BitMatrix m = new com.google.zxing.qrcode.QRCodeWriter()
                .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints);
        int[] px = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
        }
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, size, 0, 0, size, size);
        return bmp;
    }

    /** پنجره‌ی QR یک حساب: مشتری با اپ بانکش اسکن می‌کند یا عکسش برای او فرستاده می‌شود */
    static void showQr(final MainActivity act, final Db.BankAccount b) {
        if (b.card == null || b.card.isEmpty()) {
            Ui.toastLong(act, "برای این حساب شماره کارت ثبت نشده است");
            return;
        }
        final android.graphics.Bitmap bmp;
        try {
            bmp = qrBitmap(b.card, 720);
        } catch (Exception e) {
            Ui.toastLong(act, "ساخت QR ناموفق بود");
            return;
        }
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(act, 16), Ui.dp(act, 8), Ui.dp(act, 16), Ui.dp(act, 8));
        android.widget.ImageView iv = new android.widget.ImageView(act);
        iv.setImageBitmap(bmp);
        box.addView(iv, new LinearLayout.LayoutParams(Ui.dp(act, 260), Ui.dp(act, 260)));
        TextView cap = new TextView(act);
        cap.setGravity(android.view.Gravity.CENTER);
        cap.setTextSize(16);
        cap.setPadding(0, Ui.dp(act, 8), 0, 0);
        StringBuilder sb = new StringBuilder();
        if (!b.owner.isEmpty()) sb.append(b.owner).append("\n");
        if (!b.bank.isEmpty()) sb.append(b.bank).append("\n");
        sb.append(fmtCard(b.card));
        cap.setText(sb.toString());
        box.addView(cap);
        TextView note = new TextView(act);
        note.setGravity(android.view.Gravity.CENTER);
        note.setTextSize(12);
        note.setText("مشتری می‌تواند با اپ بانک یا اسکنر، QR را بخواند. اگر اپ بانکش شماره را نشان نداد، شماره‌ی بالا را کپی کند.");
        box.addView(note);
        new MaterialAlertDialogBuilder(act)
                .setTitle("🔳 QR کارت")
                .setView(Ui.scroll(box))
                .setPositiveButton("📤 ارسال عکس QR", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            java.io.File dir = new java.io.File(act.getCacheDir(), "exports");
                            if (!dir.exists()) dir.mkdirs();
                            java.io.File f = new java.io.File(dir, "card-qr.png");
                            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                            try {
                                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                            } finally {
                                out.close();
                            }
                            ReportExporter.shareFile(act, f, "image/png", "QR کارت");
                        } catch (Exception e) {
                            Ui.toastLong(act, "ارسال عکس ناموفق بود");
                        }
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    /** از صفحه‌ی پرداخت: اگر یک حساب دارید مستقیم QR، وگرنه اول انتخاب حساب */
    static void pickAndShowQr(final MainActivity act) {
        final java.util.List<Db.BankAccount> all = new java.util.ArrayList<>();
        for (Db.BankAccount b : Db.get(act).getBankAccounts()) if (!b.card.isEmpty()) all.add(b);
        if (all.isEmpty()) {
            Ui.toastLong(act, "اول از منو ← تنظیمات ← حساب‌های بانکی، حسابی با شماره کارت ثبت کنید");
            return;
        }
        if (all.size() == 1) {
            showQr(act, all.get(0));
            return;
        }
        String[] items = new String[all.size()];
        for (int i = 0; i < items.length; i++) {
            Db.BankAccount b = all.get(i);
            items[i] = (b.isDefault ? "⭐ " : "") + (b.bank.isEmpty() ? "حساب" : b.bank)
                    + (b.owner.isEmpty() ? "" : " — " + b.owner) + "\n" + fmtCard(b.card);
        }
        new MaterialAlertDialogBuilder(act)
                .setTitle("کدام حساب؟")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        showQr(act, all.get(which));
                    }
                })
                .show();
    }

    static String shareText(Db.BankAccount b) {
        StringBuilder sb = new StringBuilder("💳 اطلاعات حساب\n");
        if (!b.owner.isEmpty()) sb.append("به نام: ").append(b.owner).append("\n");
        if (!b.bank.isEmpty()) sb.append("بانک: ").append(b.bank).append("\n");
        if (!b.card.isEmpty()) sb.append("شماره کارت: ").append(fmtCard(b.card)).append("\n");
        if (!b.sheba.isEmpty()) sb.append("شبا: ").append(b.sheba).append("\n");
        return sb.toString().trim();
    }

    static void show(final MainActivity act) {
        final Db db = Db.get(act);
        final java.util.List<Db.BankAccount> list = db.getBankAccounts();

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 16), Ui.dp(act, 8), Ui.dp(act, 16), Ui.dp(act, 8));

        final AlertDialog[] holder = new AlertDialog[1];
        if (list.isEmpty()) {
            TextView empty = new TextView(act);
            empty.setText("هنوز حسابی ثبت نکرده‌اید. تا ۱۰ حساب می‌توانید اضافه کنید و یکی را پیش‌فرض بگذارید.");
            empty.setPadding(0, Ui.dp(act, 12), 0, Ui.dp(act, 12));
            box.addView(empty);
        }
        for (final Db.BankAccount b : list) {
            TextView t = new TextView(act);
            StringBuilder sb = new StringBuilder();
            sb.append(b.isDefault ? "⭐ " : "").append(b.bank.isEmpty() ? "حساب" : b.bank);
            if (!b.owner.isEmpty()) sb.append(" — ").append(b.owner);
            if (!b.card.isEmpty()) sb.append("\n").append(fmtCard(b.card));
            if (!b.sheba.isEmpty()) sb.append("\n").append(b.sheba);
            t.setText(sb.toString());
            t.setTextSize(15);
            t.setPadding(Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 12));
            t.setBackground(Ui.tinted(act, Ui.themeColor(act, R.attr.barberAccent, 0xFF1E6BE6), 0x1F, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(act, 8);
            t.setLayoutParams(lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    holder[0].dismiss();
                    options(act, b);
                }
            });
            box.addView(t);
        }

        android.widget.ScrollView sv = new android.widget.ScrollView(act);
        sv.addView(box);
        holder[0] = new MaterialAlertDialogBuilder(act)
                .setTitle("🏦 حساب‌های بانکی")
                .setView(sv)
                .setPositiveButton("➕ حساب جدید", null)
                .setNegativeButton("بستن", null)
                .create();
        holder[0].show();
        holder[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (list.size() >= Db.MAX_BANK_ACCOUNTS) {
                    Ui.toastLong(act, "حداکثر " + Db.MAX_BANK_ACCOUNTS + " حساب می‌توانید ثبت کنید");
                    return;
                }
                holder[0].dismiss();
                form(act, null);
            }
        });
    }

    private static void options(final MainActivity act, final Db.BankAccount b) {
        String[] items = {"✏️ ویرایش", "⭐ پیش‌فرض کن", "📤 ارسال برای مشتری (متن)", "🔳 نمایش QR کارت", "🗑 حذف"};
        new MaterialAlertDialogBuilder(act)
                .setTitle(b.bank.isEmpty() ? "حساب" : b.bank)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            form(act, b);
                        } else if (which == 1) {
                            if (!act.canWrite()) return;
                            Db.get(act).setDefaultBank(b.id);
                            show(act);
                        } else if (which == 2) {
                            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
                            i.setType("text/plain");
                            i.putExtra(android.content.Intent.EXTRA_TEXT, shareText(b));
                            act.startActivity(android.content.Intent.createChooser(i, "ارسال اطلاعات حساب"));
                        } else if (which == 3) {
                            showQr(act, b);
                        } else {
                            confirmDelete(act, b);
                        }
                    }
                })
                .show();
    }

    private static void confirmDelete(final MainActivity act, final Db.BankAccount b) {
        if (!act.canWrite()) return;
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف حساب")
                .setMessage("این حساب حذف شود؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Db.get(act).deleteBankAccount(b.id);
                        show(act);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private static EditText field(MainActivity act, LinearLayout box, String hint, String value, int type) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        box.addView(e);
        return e;
    }

    private static void form(final MainActivity act, final Db.BankAccount existing) {
        if (!act.canWrite()) return;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), Ui.dp(act, 8));

        final EditText etOwner = field(act, box, "نام صاحب حساب",
                existing == null ? "" : existing.owner, android.text.InputType.TYPE_CLASS_TEXT);
        final EditText etBank = field(act, box, "نام بانک",
                existing == null ? "" : existing.bank, android.text.InputType.TYPE_CLASS_TEXT);
        final EditText etCard = field(act, box, "شماره کارت (۱۶ رقم)",
                existing == null ? "" : existing.card, android.text.InputType.TYPE_CLASS_PHONE);
        final EditText etSheba = field(act, box, "شماره شبا (اختیاری، مثل IR12...)",
                existing == null ? "" : existing.sheba, android.text.InputType.TYPE_CLASS_TEXT);
        final android.widget.CheckBox cb = new android.widget.CheckBox(act);
        cb.setText("حساب پیش‌فرض (روی رسید و QR اول نشان داده می‌شود)");
        cb.setChecked(existing != null && existing.isDefault);
        box.addView(cb);

        android.widget.ScrollView sv = new android.widget.ScrollView(act);
        sv.addView(box);
        final AlertDialog dlg = new MaterialAlertDialogBuilder(act)
                .setTitle(existing == null ? "حساب جدید" : "ویرایش حساب")
                .setView(sv)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String card = Fmt.normalizeDigits(etCard.getText().toString()).replaceAll("[^0-9]", "");
                String sheba = Fmt.normalizeDigits(etSheba.getText().toString())
                        .replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.US);
                if (!sheba.isEmpty() && !sheba.startsWith("IR") && sheba.matches("[0-9]{24}")) sheba = "IR" + sheba;
                if (card.isEmpty() && sheba.isEmpty()) {
                    etCard.setError("شماره کارت یا شبا را وارد کنید");
                    return;
                }
                if (!card.isEmpty() && (card.length() != 16 || !luhn(card))) {
                    etCard.setError("شماره کارت باید ۱۶ رقم معتبر باشد");
                    return;
                }
                if (!sheba.isEmpty() && !validSheba(sheba)) {
                    etSheba.setError("شبا باید IR و ۲۴ رقم معتبر باشد");
                    return;
                }
                Db.BankAccount b = new Db.BankAccount();
                b.id = existing == null ? 0 : existing.id;
                b.owner = etOwner.getText().toString().trim();
                b.bank = etBank.getText().toString().trim();
                b.card = card;
                b.sheba = sheba;
                b.isDefault = cb.isChecked();
                long id = Db.get(act).saveBankAccount(b);
                if (id < 0) {
                    Ui.toastLong(act, "ثبت نشد (حداکثر " + Db.MAX_BANK_ACCOUNTS + " حساب یا لایسنس تمام شده)");
                    return;
                }
                dlg.dismiss();
                show(act);
            }
        });
    }
}
