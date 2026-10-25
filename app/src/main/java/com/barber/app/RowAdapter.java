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

    // ---- نمای جدولی ----
    private static final float[] COL_WEIGHT = {1.1f, 2.0f, 2.4f, 1.4f};
    private boolean table;
    private String[] heads = {null, null, null, null};
    private View headerView;

    /**
     * سرستون جدول را به بالای لیست اضافه می‌کند (باید قبل از setAdapter صدا زده شود).
     * heads = {شروع، عنوان، توضیح، انتها}؛ null یعنی آن ستون نمایش داده نشود.
     * توجه: سرستون یک «هدر» لیست است، پس موقعیت لمس باید با getHeaderViewsCount() کم شود.
     */
    public void attach(android.widget.ListView list, String[] heads) {
        this.heads = heads;
        headerView = buildRow(null, true);
        list.addHeaderView(headerView, null, false);
        fillHeader();
        headerView.setVisibility(table ? View.VISIBLE : View.GONE);
    }

    public void setTable(boolean on) {
        if (on == table) return;
        table = on;
        if (headerView != null) headerView.setVisibility(on ? View.VISIBLE : View.GONE);
        notifyDataSetChanged();
    }

    private void fillHeader() {
        android.widget.LinearLayout box = (android.widget.LinearLayout) headerView;
        android.widget.LinearLayout row = (android.widget.LinearLayout) box.getChildAt(0);
        int accent = Ui.themeColor(ctx, R.attr.barberAccent, 0xFF1E6BE6);
        box.setBackgroundColor((accent & 0x00FFFFFF) | (0x33 << 24));
        for (int i = 0; i < 4; i++) {
            TextView t = (TextView) row.getChildAt(i);
            t.setVisibility(heads[i] == null ? View.GONE : View.VISIBLE);
            t.setText(heads[i] == null ? "" : heads[i]);
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
    }

    /** یک ردیف جدول (۴ ستون با وزن ثابت + خط جداکننده‌ی پایین) */
    private View buildRow(ViewGroup parent, boolean headerRow) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(ctx);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        int pad = Ui.dp(ctx, 8);
        row.setPadding(Ui.dp(ctx, 12), pad + 2, Ui.dp(ctx, 12), pad + 2);
        for (int i = 0; i < 4; i++) {
            TextView t = new TextView(ctx);
            t.setTextSize(12.5f);
            t.setTextColor(Ui.themeColor(ctx, android.R.attr.textColorPrimary, 0xFF000000));
            t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            t.setPadding(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3), 0);
            row.addView(t, new android.widget.LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, COL_WEIGHT[i]));
        }
        box.addView(row);
        View div = new View(ctx);
        div.setBackgroundColor(0x22808080);
        box.addView(div, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return box;
    }

    private void bindTable(View v, Row r, int position) {
        android.widget.LinearLayout box = (android.widget.LinearLayout) v;
        android.widget.LinearLayout row = (android.widget.LinearLayout) box.getChildAt(0);
        int accent = Ui.themeColor(ctx, R.attr.barberAccent, 0xFF1E6BE6);
        String[] texts = {r.lead, r.title, r.subtitle, r.trail};
        for (int i = 0; i < 4; i++) {
            TextView t = (TextView) row.getChildAt(i);
            t.setVisibility(heads[i] == null ? View.GONE : View.VISIBLE);
            t.setText(texts[i] == null ? "" : texts[i]);
            boolean bold = r.header || i == 3;
            t.setTypeface(null, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            t.setTextColor(r.header ? accent
                    : Ui.themeColor(ctx, android.R.attr.textColorPrimary, 0xFF000000));
        }
        if (r.header) box.setBackgroundColor((accent & 0x00FFFFFF) | (0x26 << 24));
        else if (position % 2 == 1) box.setBackgroundColor((accent & 0x00FFFFFF) | (0x0E << 24));
        else box.setBackgroundColor(0);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return table ? 1 : 0;
    }

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
        if (table) {
            if (v == null) v = buildRow(parent, false);
            bindTable(v, rows.get(position), position);
            return v;
        }
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
