package com.carrypick.service;

import java.time.LocalDate;
import java.time.ZoneId;

/** "Today" in Korea time, used for all date rules (trip start, request deadline). */
public final class Today {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private Today() {}

    public static LocalDate get() {
        return LocalDate.now(ZONE);
    }
}
