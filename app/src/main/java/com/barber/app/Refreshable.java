package com.barber.app;

/** هر تب برنامه باید بتواند اطلاعاتش را دوباره از دیتابیس بخواند. */
public interface Refreshable {
    void refresh();
}
