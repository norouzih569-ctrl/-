package com.barber.admin;

import android.view.View;

/** هر تب برنامه: یک نما دارد و می‌تواند داده‌هایش را دوباره بخواند. */
public interface Tab extends Refreshable {
    View view();
}
