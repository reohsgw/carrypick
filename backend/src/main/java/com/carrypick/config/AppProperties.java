package com.carrypick.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String corsOrigins,
        Jwt jwt,
        String uploadDir,
        boolean seedDemoData,
        Pricing pricing,
        Payment payment,
        Ai ai,
        java.util.Map<String, Double> fx
) {
    /** AI receipt reader. provider = "mock" (sample result, no key) or "anthropic" (Claude vision). */
    public record Ai(String provider, String apiKey, String model, String apiUrl) {
        public boolean isMock() {
            return provider == null || !provider.equalsIgnoreCase("anthropic") || apiKey == null || apiKey.isBlank();
        }
    }

    public record Jwt(String secret, int ttlHours) {}

    public record Pricing(double feeRate, long taxThresholdKrw, double taxRate) {}

    public record Payment(String mode, String tossClientKey, String tossSecretKey, String tossConfirmUrl) {
        public boolean isMock() {
            return mode == null || !mode.equalsIgnoreCase("toss");
        }
    }
}
