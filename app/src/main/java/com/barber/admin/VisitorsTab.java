package com.barber.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** تب ویزیتورها: درصد، تعداد مشتری و وضعیت پرداخت پورسانت هر ویزیتور. */
public class VisitorsTab implements Tab {

    private final MainActivity act;
    private final View root;
    private final RowAdapter adapter;
    private List<Models.Visitor> data = new ArrayList<Models.Visitor>();

    public VisitorsTab(MainActivity activity) {
        this.act = activity;
        root = LayoutInflater.from(act).inflate(R.layout.tab_visitors, null, false);
        ListView list = root.findViewById(R.id.list_visitors);
        TextView empty = root.findViewById(R.id.empty_visitors);
        adapter = new RowAdapter(act);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        root.findViewById(R.id.fab_add_visitor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VisitorForm.show(act, null);
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < data.size()) {
                    VisitorProfile.show(act, data.get(position).id);
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < data.size()) {
                    showActions(data.get(position));
                    return true;
                }
                return false;
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
        data = db.listVisitors(false);
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        for (Models.Visitor v : data) {
            Commission.Result res = db.visitorCommissionStatus(v.id);
            int unpaid = 0;
            long due = 0;
            for (Commission.Item it : res.items) {
                if (it.status() != Commission.PAID) unpaid++;
                due += it.due();
            }
            int customers = db.listCustomersByVisitor(v.id).size();
            String sub = "درصد " + Fmt.percent(v.percent) + " • " + customers + " مشتری"
                    + (v.active ? "" : " • غیرفعال");
            String trail;
            if (res.items.isEmpty()) trail = "";
            else if (due <= 0) trail = "✅ تسویه";
            else trail = "❌ " + Fmt.price(due) + "\n" + unpaid + " لایسنس";
            rows.add(new RowAdapter.Row(v.id, "", v.name, sub, trail, true));
        }
        adapter.setRows(rows);
    }

    private void showActions(final Models.Visitor v) {
        new MaterialAlertDialogBuilder(act)
                .setTitle(v.name)
                .setItems(new CharSequence[]{"ویرایش", "حذف"}, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) VisitorForm.show(act, v);
                        else confirmDelete(v);
                    }
                })
                .show();
    }

    private void confirmDelete(final Models.Visitor v) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف ویزیتور")
                .setMessage("ویزیتور «" + v.name + "» حذف شود؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (Db.get(act).deleteVisitor(v.id)) {
                            act.refreshAll();
                            Ui.toast(act, "ویزیتور حذف شد");
                        } else {
                            Ui.toastLong(act, "این ویزیتور مشتری، لایسنس یا پرداخت دارد و حذف نمی‌شود؛ می‌توانید غیرفعالش کنید");
                        }
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }
}
