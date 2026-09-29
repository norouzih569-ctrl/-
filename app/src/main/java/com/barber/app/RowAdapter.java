package com.barber.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** آداپتور عمومی همه لیست‌ها: (شروع) عنوان کوچک، (وسط) عنوان و توضیح، (انتها) مقدار. */
public class RowAdapter extends BaseAdapter {

    public static class Row {
        public final long id;
        public final String lead;
        public final String title;
        public final String subtitle;
        public final String trail;

        public Row(long id, String lead, String title, String subtitle, String trail) {
            this.id = id;
            this.lead = lead;
            this.title = title;
            this.subtitle = subtitle;
            this.trail = trail;
        }
    }

    private final Context ctx;
    private final List<Row> rows = new ArrayList<>();

    public RowAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return rows.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_row, parent, false);
        }
        bind(v, rows.get(position));
        return v;
    }

    /** پر کردن یک ردیف (برای استفاده در لیست‌های ساخته‌شده با کد هم کاربرد دارد). */
    public static void bind(View v, Row r) {
        setText((TextView) v.findViewById(R.id.row_lead), r.lead);
        setText((TextView) v.findViewById(R.id.row_title), r.title);
        setText((TextView) v.findViewById(R.id.row_subtitle), r.subtitle);
        setText((TextView) v.findViewById(R.id.row_trail), r.trail);
    }

    private static void setText(TextView tv, String text) {
        if (text == null || text.isEmpty()) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
        }
    }
}
