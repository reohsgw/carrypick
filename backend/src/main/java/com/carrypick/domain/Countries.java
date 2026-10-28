package com.carrypick.domain;

import java.util.List;
import java.util.Optional;

/** Fixed list of supported countries. Kept in code to stay simple for the prototype. */
public final class Countries {
    private Countries() {}

    public record Country(String code, String name, String nameKo, String currency, String flag) {}

    public static final List<Country> ALL = List.of(
            new Country("JP", "Japan", "일본", "JPY", "🇯🇵"),
            new Country("US", "United States", "미국", "USD", "🇺🇸"),
            new Country("FR", "France", "프랑스", "EUR", "🇫🇷"),
            new Country("GB", "United Kingdom", "영국", "GBP", "🇬🇧"),
            new Country("TH", "Thailand", "태국", "THB", "🇹🇭"),
            new Country("VN", "Vietnam", "베트남", "VND", "🇻🇳"),
            new Country("ID", "Indonesia", "인도네시아", "IDR", "🇮🇩"),
            new Country("TW", "Taiwan", "대만", "TWD", "🇹🇼")
    );

    public static Optional<Country> find(String code) {
        if (code == null) return Optional.empty();
        return ALL.stream().filter(c -> c.code().equalsIgnoreCase(code)).findFirst();
    }

    public static boolean exists(String code) {
        return find(code).isPresent();
    }
}
