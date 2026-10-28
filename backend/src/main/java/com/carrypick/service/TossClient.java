package com.carrypick.service;

import com.carrypick.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Calls the Toss Payments confirm API with the SECRET key. Only the server may do this,
 * which is why the browser never sees the secret key.
 * In mock mode no network call is made.
 */
@Component
public class TossClient {
    private static final Logger log = LoggerFactory.getLogger(TossClient.class);

    public record Result(boolean success, String message) {}

    private final AppProperties.Payment cfg;
    private final RestClient http = RestClient.create();

    public TossClient(AppProperties props) {
        this.cfg = props.payment();
    }

    public Result confirm(String paymentKey, String tossOrderId, long amount) {
        if (cfg.isMock()) {
            // Mock page sends keys starting with "mock_fail" to simulate a declined card.
            if (paymentKey.startsWith("mock_fail")) return new Result(false, "Card declined (mock)");
            return new Result(true, "OK (mock)");
        }
        if (cfg.tossSecretKey() == null || cfg.tossSecretKey().isBlank()) {
            return new Result(false, "TOSS_SECRET_KEY is not configured on the server");
        }
        String basic = Base64.getEncoder()
                .encodeToString((cfg.tossSecretKey() + ":").getBytes(StandardCharsets.UTF_8));
        try {
            http.post()
                    .uri(cfg.tossConfirmUrl())
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", tossOrderId, "amount", amount))
                    .retrieve()
                    .toBodilessEntity();
            return new Result(true, "OK");
        } catch (RestClientResponseException e) {
            log.warn("Toss confirm failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new Result(false, "Toss rejected the payment: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Toss confirm error", e);
            return new Result(false, "Could not reach Toss Payments");
        }
    }
}
