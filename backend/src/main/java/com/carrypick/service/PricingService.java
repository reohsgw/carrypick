package com.carrypick.service;

import com.carrypick.config.AppProperties;
import com.carrypick.web.dto.Dtos.Quote;
import org.springframework.stereotype.Service;

/**
 * Charge = B + R + F + T
 *   B = item budget, R = traveler reward,
 *   F = feeRate x (B + R), T = taxRate x B only when B is above the threshold (estimate).
 * All amounts are whole KRW; rounding is half-up (Math.round).
 * The React app repeats this formula for live previews; the server value is the one charged.
 */
@Service
public class PricingService {
    private final AppProperties.Pricing cfg;

    public PricingService(AppProperties props) {
        this.cfg = props.pricing();
    }

    public Quote quote(long budget, long reward) {
        long fee = Math.round(cfg.feeRate() * (budget + reward));
        long tax = budget > cfg.taxThresholdKrw() ? Math.round(cfg.taxRate() * budget) : 0;
        return new Quote(budget, reward, fee, tax, budget + reward + fee + tax);
    }

    public long feeOn(long amount) {
        return Math.round(cfg.feeRate() * amount);
    }
}
