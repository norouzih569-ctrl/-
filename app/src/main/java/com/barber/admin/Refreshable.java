package com.barber.admin;

/** هر تب برنامه باید بتواند اطلاعاتش را دوباره از دیتابیس بخواند. */
public interface Refreshable {
    void refresh();
}
