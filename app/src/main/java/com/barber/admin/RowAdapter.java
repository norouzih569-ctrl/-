package com.barber.admin;

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
        /** اگر true باشد به‌جای متن lead، حرف اول عنوان داخل یک دایره (آواتار) نمایش داده می‌شود */
        public final boolean avatar;
        /** سرتیتر روز در نمای هفتگی (کارت رنگی بدون لبه) */
        public final boolean header;

        public Row(long id, String lead, String title, String subtitle, String trail) {
            this(id, lead, title, subtitle, trail, false);
        }

        public Row(long id, String lead, String title, String subtitle, String trail, boolean avatar) {
            this(id, lead, title, subtitle, trail, avatar, false);
        }

        public Row(long id, String lead, String title, String subtitle, String trail,
                   boolean avatar, boolean header) {
            this.header = header;
            this.id = id;
            this.lead = lead;
            this.title = title;
            this.subtitle = subtitle;
            this.trail = trail;
            this.avatar = avatar;
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
        bindLead(v, r);
        bindCard(v, r);
        setText((TextView) v.findViewById(R.id.row_title), r.title);
        setText((TextView) v.findViewById(R.id.row_subtitle), r.subtitle);
        setText((TextView) v.findViewById(R.id.row_trail), r.trail);
    }

    /** سرتیتر روز رنگی و بی‌لبه است؛ ردیف‌های عادی سطح و لبه‌ی تم را دارند (ردیف‌ها بازیافت می‌شوند). */
    private static void bindCard(View v, Row r) {
        Context ctx = v.getContext();
        com.google.android.material.card.MaterialCardView card =
                (com.google.android.material.card.MaterialCardView) v.findViewById(R.id.row_card);
        TextView title = (TextView) v.findViewById(R.id.row_title);
        int accent = Ui.themeColor(ctx, R.attr.barberAccent, 0xFF1E6BE6);
        if (r.header) {
            card.setCardBackgroundColor((accent & 0x00FFFFFF) | (0x26 << 24));
            card.setStrokeWidth(0);
            title.setTextColor(accent);
        } else {
            card.setCardBackgroundColor(Ui.themeColor(ctx, R.attr.barberSurface, 0xFFFFFFFF));
            card.setStrokeWidth(Ui.dp(ctx, 1));
            title.setTextColor(Ui.themeColor(ctx, android.R.attr.textColorPrimary, 0xFF000000));
        }
    }

    /** سمت شروع کارت: آواتار گرد (حرف اول) یا چیپ رنگی (ساعت/تاریخ) */
    private static void bindLead(View v, Row r) {
        Context ctx = v.getContext();
        TextView lead = (TextView) v.findViewById(R.id.row_lead);
        int accent = Ui.themeColor(ctx, R.attr.barberAccent, 0xFF1E6BE6);
        android.widget.LinearLayout.LayoutParams lp =
                (android.widget.LinearLayout.LayoutParams) lead.getLayoutParams();
        if (r.avatar) {
            String t = r.title == null ? "" : r.title.trim();
            String initial = t.isEmpty() ? "؟" : new String(Character.toChars(t.codePointAt(0)));
            lead.setVisibility(View.VISIBLE);
            lead.setText(initial);
            lead.setTextSize(18);
            lp.width = Ui.dp(ctx, 44);
            lp.height = Ui.dp(ctx, 44);
            lead.setPadding(0, 0, 0, 0);
            android.graphics.drawable.GradientDrawable g = Ui.tinted(ctx, accent, 0x2E, 22);
            lead.setBackground(g);
        } else if (r.lead == null || r.lead.isEmpty()) {
            lead.setVisibility(View.GONE);
        } else {
            lead.setVisibility(View.VISIBLE);
            lead.setText(r.lead);
            lead.setTextSize(13);
            lp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            lead.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 6), Ui.dp(ctx, 10), Ui.dp(ctx, 6));
            lead.setBackground(Ui.tinted(ctx, accent, 0x26, 10));
        }
        lead.setLayoutParams(lp);
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
