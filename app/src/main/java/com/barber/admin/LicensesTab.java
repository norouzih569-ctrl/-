package com.barber.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** تب لایسنس‌ها: تاریخچه‌ی همه‌ی کدهای صادرشده و صدور کد جدید. */
public class LicensesTab implements Tab {

    private final MainActivity act;
    private final View root;
    private final RowAdapter adapter;
    private List<Models.License> data = new ArrayList<Models.License>();

    public LicensesTab(MainActivity activity) {
        this.act = activity;
        root = LayoutInflater.from(act).inflate(R.layout.tab_licenses, null, false);
        ListView list = root.findViewById(R.id.list_licenses);
        TextView empty = root.findViewById(R.id.empty_licenses);
        adapter = new RowAdapter(act);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        root.findViewById(R.id.fab_issue).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IssueDialog.chooser(act, 0);
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < data.size()) showDetail(data.get(position));
            }
        });
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void refresh() {
        Db db = Db.get(act);
        data = db.listLicenses(0);
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        for (Models.License l : data) {
            Models.Customer c = db.getCustomer(l.customerId);
            String sub = Labels.licenseType(l.type) + (l.type == LicenseCodec.TYPE_TRANSFER ? ""
                    : " • " + Fmt.price(l.finalPrice));
            String trail = l.voided ? "⛔ باطل" : (l.code.isEmpty() ? "" : l.code);
            rows.add(new RowAdapter.Row(l.id, JalaliCalendar.gregorianStringToJalali(l.issueDate),
                    c != null ? c.shopName : "؟", sub, trail));
        }
        adapter.setRows(rows);
    }

    private void showDetail(final Models.License l) {
        final Db db = Db.get(act);
        final Models.Customer c = db.getCustomer(l.customerId);
        StringBuilder sb = new StringBuilder();
        sb.append("مشتری: ").append(c != null ? c.shopName : "؟").append('\n');
        sb.append("نوع: ").append(Labels.licenseType(l.type)).append('\n');
        sb.append("تاریخ صدور: ").append(JalaliCalendar.gregorianStringToJalali(l.issueDate)).append('\n');
        sb.append("شناسه‌ی دستگاه: ").append(l.deviceId).append('\n');
        if (l.type != LicenseCodec.TYPE_TRANSFER) {
            sb.append("قیمت پایه: ").append(Fmt.money(l.basePrice)).append('\n');
            if (l.discountPercent > 0) sb.append("تخفیف: ").append(Fmt.percent(l.discountPercent)).append('\n');
            sb.append("مبلغ نهایی: ").append(Fmt.money(l.finalPrice)).append('\n');
            if (l.visitorId > 0) {
                Models.Visitor v = db.getVisitor(l.visitorId);
                sb.append("ویزیتور: ").append(v != null ? v.name : "؟").append(" (")
                        .append(Fmt.percent(l.commissionPercent)).append(" = ")
                        .append(Fmt.money(l.commissionAmount)).append(")\n");
            }
        }
        if (!l.notes.isEmpty()) sb.append("یادداشت: ").append(l.notes).append('\n');
        if (l.voided) sb.append("\n⛔ این لایسنس باطل شده و در درآمد و پورسانت حساب نمی‌شود.\n");
        sb.append("\nکد: ").append(l.code);

        new MaterialAlertDialogBuilder(act)
                .setTitle("لایسنس " + JalaliCalendar.gregorianStringToJalali(l.issueDate))
                .setMessage(sb.toString())
                .setPositiveButton("ارسال کد", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        IssueDialog.sendChooser(act, c, IssueLogic.message(l.code, l.type, l.deviceId));
                    }
                })
                .setNeutralButton(l.voided ? "رفع ابطال" : "ابطال", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        confirmVoid(l);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void confirmVoid(final Models.License l) {
        new MaterialAlertDialogBuilder(act)
                .setTitle(l.voided ? "رفع ابطال" : "ابطال لایسنس")
                .setMessage(l.voided
                        ? "لایسنس دوباره در درآمد و پورسانت حساب شود؟"
                        : "لایسنس باطل شود؟ از درآمد، بدهی مشتری و پورسانت ویزیتور کم می‌شود (کد روی گوشی مشتری همچنان کار می‌کند؛ این فقط حساب‌وکتاب شماست).")
                .setPositiveButton("بله", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        Db.get(act).setLicenseVoid(l.id, !l.voided);
                        act.refreshAll();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }
}
