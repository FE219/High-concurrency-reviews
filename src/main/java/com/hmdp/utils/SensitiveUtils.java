package com.hmdp.utils;

public final class SensitiveUtils {
    private SensitiveUtils() {}

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
