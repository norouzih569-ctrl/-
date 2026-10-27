package com.barber.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** جدول ساده‌ی قابل مرتب‌سازی (لمس سرستون) با ردیف جمع؛ سلول: String یا Number */
public final class TableUi {

    public interface OnRow {
        void onRow(int originalIndex);
    }

    private TableUi() {
    }

    private static final class Row {
        final Object[] cells;
        final int index;

        Row(Object[] c, int i) {
            cells = c;
            index = i;
        }
    }

    public static View build(Context ctx, String[] headers, List<Object[]> data,
                             Object[] totals, OnRow onRow) {
        return build(ctx, headers, data, totals, onRow, true);
    }

    public static View build(final Context ctx, final String[] headers, List<Object[]> data,
                             final Object[] totals, final OnRow onRow, final boolean sortable) {
        final int accent = Ui.themeColor(ctx, R.attr.barberAccent, 0xFF1F5FBF);
        final int textColor = Ui.themeColor(ctx, android.R.attr.textColorPrimary, 0xFF222222);
        final List<Row> rows = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) rows.add(new Row(data.get(i), i));
        final int[] sortCol = {-1};
        final boolean[] asc = {true};

        final TableLayout table = new TableLayout(ctx);
        table.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        table.setStretchAllColumns(true);
        final int pad = Ui.dp(ctx, 8);

        final Runnable[] render = {null};
        render[0] = new Runnable() {
            @Override
            public void run() {
                table.removeAllViews();
                TableRow hr = new TableRow(ctx);
                hr.setBackgroundColor((accent & 0x00FFFFFF) | 0x33000000);
                for (int c = 0; c < headers.length; c++) {
                    final int col = c;
                    String arrow = sortCol[0] == c ? (asc[0] ? " ▲" : " ▼") : "";
                    TextView t = cell(ctx, headers[c] + (sortable ? arrow : ""), true, textColor, pad);
                    if (sortable) t.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (sortCol[0] == col) asc[0] = !asc[0];
                            else {
                                sortCol[0] = col;
                                asc[0] = true;
                            }
                            sort(rows, col, asc[0]);
                            render[0].run();
                        }
                    });
                    hr.addView(t);
                }
                table.addView(hr);
                for (int i = 0; i < rows.size(); i++) {
                    final Row r = rows.get(i);
                    TableRow tr = new TableRow(ctx);
                    if (i % 2 == 1) tr.setBackgroundColor((accent & 0x00FFFFFF) | 0x12000000);
                    for (Object o : r.cells) tr.addView(cell(ctx, show(o), false, textColor, pad));
                    if (onRow != null) {
                        tr.setClickable(true);
                        tr.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                onRow.onRow(r.index);
                            }
                        });
                    }
                    table.addView(tr);
                }
                if (totals != null) {
                    TableRow tr = new TableRow(ctx);
                    tr.setBackgroundColor((accent & 0x00FFFFFF) | 0x26000000);
                    for (Object o : totals) tr.addView(cell(ctx, show(o), true, textColor, pad));
                    table.addView(tr);
                }
            }
        };
        render[0].run();

        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setFillViewport(true);
        hs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        hs.addView(table, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4), 0);
        wrap.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private static String show(Object o) {
        if (o == null) return "";
        if (o instanceof Number) return ReportData.fmt(((Number) o).doubleValue());
        return String.valueOf(o);
    }

    private static TextView cell(Context ctx, String s, boolean bold, int color, int pad) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(12.5f);
        t.setTextColor(color);
        t.setPadding(pad, pad, pad, pad);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(false);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private static void sort(List<Row> rows, final int col, final boolean asc) {
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                Object x = a.cells[col], y = b.cells[col];
                int c;
                if (x instanceof Number && y instanceof Number) {
                    c = Double.compare(((Number) x).doubleValue(), ((Number) y).doubleValue());
                } else {
                    c = String.valueOf(x).compareTo(String.valueOf(y));
                }
                return asc ? c : -c;
            }
        });
    }
}
