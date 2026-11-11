package com.barber.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ScrollView;

import java.util.List;

/**
 * حالت نمایش همه‌ی لیست‌ها: کارتی (پیش‌فرض) یا جدولی. انتخاب یک‌بار برای همه‌ی تب‌ها ذخیره می‌شود.
 */
public final class GridMode {

    private static final String PREFS = "view_mode";

    private GridMode() {
    }

    public static boolean isTable(Context ctx) {
        return "table".equals(ctx.getSharedPreferences(PREFS, 0).getString("mode", "cards"));
    }

    public static void setTable(Context ctx, boolean table) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("mode", table ? "table" : "cards").apply();
    }

    /** میزبان جدول کنار یک ListView (در همان FrameLayout) */
    public static final class Host {
        private final Context ctx;
        private final ListView list;
        private final ScrollView scroll;

        public Host(Context ctx, ListView list) {
            this.ctx = ctx;
            this.list = list;
            scroll = new ScrollView(ctx);
            scroll.setClipToPadding(false);
            scroll.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 88));
            scroll.setVisibility(View.GONE);
            scroll.setVerticalScrollBarEnabled(false);
            ViewGroup parent = (ViewGroup) list.getParent();
            parent.addView(scroll, parent.indexOfChild(list) + 1, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        /**
         * باید «بعد از» adapter.setRows صدا زده شود (چون ListView خودش نمایش/پنهان شدنش را با تغییر داده‌ها تنظیم می‌کند).
         * در حالت کارتی چیزی را عوض نمی‌کند، جز اینکه جدول را پنهان می‌کند.
         */
        public void render(String[] headers, List<Object[]> rows, Object[] totals,
                           TableUi.OnRow click, boolean sortable) {
            if (!isTable(ctx)) {
                scroll.setVisibility(View.GONE);
                return;
            }
            list.setVisibility(View.GONE);
            if (rows.isEmpty()) {
                scroll.setVisibility(View.GONE); // متن «خالی» خودش نشان داده می‌شود
                return;
            }
            scroll.removeAllViews();
            scroll.addView(TableUi.build(ctx, headers, rows, totals, click, sortable));
            scroll.setVisibility(View.VISIBLE);
        }
    }
}
