package com.barber.app;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** صفحه‌های محصولات: فهرست، افزودن/ویرایش/حذف، فروش و فروش‌های ماه */
public final class ProductsUi {

    private ProductsUi() {
    }

    private static LinearLayout column(MainActivity act) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 8));
        return box;
    }

    private static EditText field(MainActivity act, String hint, boolean numeric) {
        EditText e = new EditText(act);
        e.setHint(hint);
        if (numeric) e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    /** فهرست محصولات */
    public static void show(final MainActivity act) {
        final LinearLayout box = column(act);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);
        final AlertDialog d = new MaterialAlertDialogBuilder(act)
                .setTitle("🛍 محصولات و فروش")
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .setNeutralButton("+ محصول جدید", null)
                .setNegativeButton("فروش‌های ماه", null)
                .create();
        final Runnable[] rebuild = {null};
        rebuild[0] = new Runnable() {
            @Override
            public void run() {
                box.removeAllViews();
                List<Db.Product> list = Db.get(act).getProducts();
                TextView note = new TextView(act);
                note.setText(list.isEmpty()
                        ? "هنوز محصولی ثبت نشده. با «+ محصول جدید» شروع کنید (مثلاً ادکلن، ژل مو)."
                        : "روی هر محصول بزنید تا بفروشید، ویرایش کنید یا موجودی اضافه کنید.");
                note.setTextSize(12);
                note.setPadding(0, 0, 0, Ui.dp(act, 8));
                box.addView(note);
                for (final Db.Product p : list) {
                    TextView row = new TextView(act);
                    double up = ProductMath.unitProfit(p.sellPrice, p.buyPrice);
                    row.setText(p.name + (p.stock <= 0 ? "   ❌ ناموجود" : "   (موجودی " + p.stock + ")")
                            + "\nخرید " + Fmt.price(p.buyPrice) + "  •  فروش " + Fmt.price(p.sellPrice)
                            + "  •  سود هر عدد " + Fmt.price(up)
                            + " (" + Fmt.percent(Math.round(ProductMath.markupPercent(p.sellPrice, p.buyPrice))) + ")");
                    row.setTextSize(14);
                    row.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            actions(act, p, rebuild[0]);
                        }
                    });
                    box.addView(row);
                }
            }
        };
        rebuild[0].run();
        d.show();
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(act, null, rebuild[0]);
            }
        });
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                monthSales(act, rebuild[0]);
            }
        });
    }

    private static void actions(final MainActivity act, final Db.Product p, final Runnable done) {
        new MaterialAlertDialogBuilder(act)
                .setTitle(p.name)
                .setItems(new CharSequence[]{"🛒 فروش", "✏️ ویرایش", "➕ افزودن موجودی", "حذف"},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface x, int which) {
                                if (which == 0) sell(act, p, done);
                                else if (which == 1) edit(act, p, done);
                                else if (which == 2) addStock(act, p, done);
                                else confirmDelete(act, p, done);
                            }
                        })
                .show();
    }

    private static void edit(final MainActivity act, final Db.Product p, final Runnable done) {
        if (!act.canWrite()) return;
        LinearLayout box = column(act);
        final EditText etName = field(act, "نام محصول", false);
        final EditText etBuy = field(act, "قیمت خرید (" + AppConfig.CURRENCY + ")", true);
        final EditText etSell = field(act, "قیمت فروش (" + AppConfig.CURRENCY + ")", true);
        final EditText etStock = field(act, "موجودی (تعداد)", true);
        final TextView profit = new TextView(act);
        profit.setTextSize(13);
        if (p != null) {
            etName.setText(p.name);
            etBuy.setText(Fmt.plain(p.buyPrice));
            etSell.setText(Fmt.plain(p.sellPrice));
            etStock.setText(String.valueOf(p.stock));
        } else {
            etStock.setText("1");
        }
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                Double b = Fmt.parsePrice(etBuy.getText().toString());
                Double s = Fmt.parsePrice(etSell.getText().toString());
                if (b == null || s == null) {
                    profit.setText("");
                    return;
                }
                profit.setText("سود هر عدد: " + Fmt.money(ProductMath.unitProfit(s, b))
                        + " (" + Fmt.percent(Math.round(ProductMath.markupPercent(s, b))) + ")");
            }
        };
        etBuy.addTextChangedListener(tw);
        etSell.addTextChangedListener(tw);
        tw.afterTextChanged(null);
        box.addView(etName);
        box.addView(etBuy);
        box.addView(etSell);
        box.addView(profit);
        box.addView(etStock);
        final AlertDialog d = new MaterialAlertDialogBuilder(act)
                .setTitle(p == null ? "محصول جدید" : "ویرایش محصول")
                .setView(Ui.scroll(box))
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = ServiceList.clean(etName.getText().toString());
                if (name.isEmpty()) {
                    etName.setError("نام محصول را وارد کنید");
                    return;
                }
                Double buy = Fmt.parsePrice(etBuy.getText().toString());
                if (buy == null) {
                    etBuy.setError("قیمت خرید صحیح نیست");
                    return;
                }
                Double sell = Fmt.parsePrice(etSell.getText().toString());
                if (sell == null) {
                    etSell.setError("قیمت فروش صحیح نیست");
                    return;
                }
                Double st = Fmt.parsePrice(etStock.getText().toString());
                if (st == null || st > 100000) {
                    etStock.setError("تعداد صحیح نیست");
                    return;
                }
                if (p == null) Db.get(act).addProduct(name, buy, sell, (int) Math.round(st));
                else Db.get(act).updateProduct(p.id, name, buy, sell, (int) Math.round(st));
                d.dismiss();
                done.run();
            }
        });
    }

    private static void addStock(final MainActivity act, final Db.Product p, final Runnable done) {
        if (!act.canWrite()) return;
        LinearLayout box = column(act);
        final EditText etQty = field(act, "تعداد خرید جدید", true);
        final EditText etBuy = field(act, "قیمت خرید این دسته (خالی = همان قبلی)", true);
        box.addView(etQty);
        box.addView(etBuy);
        final AlertDialog d = new MaterialAlertDialogBuilder(act)
                .setTitle("افزودن موجودی – " + p.name)
                .setView(Ui.scroll(box))
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double q = Fmt.parsePrice(etQty.getText().toString());
                if (q == null || q < 1 || q > 100000) {
                    etQty.setError("تعداد صحیح نیست");
                    return;
                }
                double buy = p.buyPrice;
                if (!etBuy.getText().toString().trim().isEmpty()) {
                    Double b = Fmt.parsePrice(etBuy.getText().toString());
                    if (b == null) {
                        etBuy.setError("قیمت صحیح نیست");
                        return;
                    }
                    buy = b;
                }
                Db.get(act).updateProduct(p.id, p.name, buy, p.sellPrice, p.stock + (int) Math.round(q));
                d.dismiss();
                done.run();
            }
        });
    }

    private static void confirmDelete(final MainActivity act, final Db.Product p, final Runnable done) {
        if (!act.canWrite()) return;
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف «" + p.name + "»؟")
                .setMessage("محصول از لیست حذف می‌شود. فروش‌های قبلی در گزارش می‌مانند.")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface x, int w) {
                        Db.get(act).deleteProduct(p.id);
                        done.run();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private static void sell(final MainActivity act, final Db.Product p, final Runnable done) {
        if (!act.canWrite()) return;
        if (p.stock <= 0) {
            Ui.toast(act, "موجودی این محصول صفر است");
            return;
        }
        LinearLayout box = column(act);
        final EditText etQty = field(act, "تعداد", true);
        etQty.setText("1");
        final EditText etPrice = field(act, "قیمت فروش هر عدد (" + AppConfig.CURRENCY + ")", true);
        etPrice.setText(Fmt.plain(p.sellPrice));
        final TextView info = new TextView(act);
        info.setTextSize(13);
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
        final RadioGroup group = g;
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                Double q = Fmt.parsePrice(etQty.getText().toString());
                Double pr = Fmt.parsePrice(etPrice.getText().toString());
                if (q == null || pr == null) {
                    info.setText("");
                    return;
                }
                info.setText("جمع: " + Fmt.money(q * pr) + "  •  سود: "
                        + Fmt.money(ProductMath.saleProfit((int) Math.round(q), pr, p.buyPrice)));
            }
        };
        etQty.addTextChangedListener(tw);
        etPrice.addTextChangedListener(tw);
        tw.afterTextChanged(null);
        box.addView(etQty);
        box.addView(etPrice);
        box.addView(info);
        box.addView(g);
        final AlertDialog d = new MaterialAlertDialogBuilder(act)
                .setTitle("فروش – " + p.name + " (موجودی " + p.stock + ")")
                .setView(Ui.scroll(box))
                .setPositiveButton("ثبت فروش", null)
                .setNegativeButton("انصراف", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double q = Fmt.parsePrice(etQty.getText().toString());
                if (q == null) {
                    etQty.setError("تعداد صحیح نیست");
                    return;
                }
                String err = ProductMath.validateSale((int) Math.round(q), p.stock);
                if (err != null) {
                    etQty.setError(err);
                    return;
                }
                Double pr = Fmt.parsePrice(etPrice.getText().toString());
                if (pr == null) {
                    etPrice.setError("قیمت صحیح نیست");
                    return;
                }
                int idx = group.indexOfChild(group.findViewById(group.getCheckedRadioButtonId()));
                boolean ok = Db.get(act).sellProduct(p.id, (int) Math.round(q), pr, Pay.methodAt(idx),
                        JalaliCalendar.todayGregorianString());
                d.dismiss();
                done.run();
                act.refreshAll();
                Ui.toast(act, ok ? "فروش ثبت شد" : "ثبت نشد");
            }
        });
    }

    /** فروش‌های ماه جاری؛ با لمس هر فروش می‌توان لغوش کرد (موجودی برمی‌گردد) */
    private static void monthSales(final MainActivity act, final Runnable refreshList) {
        int[] t = JalaliCalendar.today();
        String[] r = JalaliCalendar.monthRangeIso(t[0], t[1]);
        List<Db.ProductSale> sales = Db.get(act).getProductSalesBetween(r[0], r[1]);
        LinearLayout box = column(act);
        double rev = 0, profit = 0;
        for (final Db.ProductSale s : sales) {
            rev += s.total();
            profit += s.profit();
            TextView row = new TextView(act);
            row.setText(JalaliCalendar.gregorianStringToJalali(s.date) + "  •  " + s.name + " × " + s.qty
                    + "\n" + Fmt.money(s.total()) + "  •  سود " + Fmt.price(s.profit()) + "  •  " + Pay.label(s.method));
            row.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!act.canWrite()) return;
                    new MaterialAlertDialogBuilder(act)
                            .setTitle("لغو این فروش؟")
                            .setMessage(s.name + " × " + s.qty + " حذف می‌شود و موجودی برمی‌گردد.")
                            .setPositiveButton("لغو فروش", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface x, int w) {
                                    Db.get(act).deleteProductSale(s.id);
                                    refreshList.run();
                                    act.refreshAll();
                                    Ui.toast(act, "فروش لغو شد");
                                }
                            })
                            .setNegativeButton("انصراف", null)
                            .show();
                }
            });
            box.addView(row);
        }
        if (sales.isEmpty()) {
            TextView e = new TextView(act);
            e.setText("این ماه فروشی ثبت نشده است");
            box.addView(e);
        }
        ScrollView sv = new ScrollView(act);
        sv.addView(box);
        new MaterialAlertDialogBuilder(act)
                .setTitle("فروش‌های این ماه")
                .setMessage(sales.isEmpty() ? null : "جمع فروش: " + Fmt.money(rev) + "\nسود: " + Fmt.money(profit))
                .setView(sv)
                .setPositiveButton("بستن", null)
                .show();
    }
}
