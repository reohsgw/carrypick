package com.carrypick.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Simulated settlement after the buyer confirms receipt. No real money moves. */
@Entity
@Table(name = "settlements")
public class Settlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    private Order order;

    /** P + R paid out to the traveler. */
    private long travelerPayout;
    /** B - P refunded to the buyer when the receipt is under budget. */
    private long buyerRefund;
    /** F (+ extra fee) kept by the platform. */
    private long platformFee;

    private boolean mock = true;

    private Instant createdAt = Instant.now();

    protected Settlement() {}

    public Settlement(Order order, long travelerPayout, long buyerRefund, long platformFee) {
        this.order = order;
        this.travelerPayout = travelerPayout;
        this.buyerRefund = buyerRefund;
        this.platformFee = platformFee;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public long getTravelerPayout() { return travelerPayout; }
    public long getBuyerRefund() { return buyerRefund; }
    public long getPlatformFee() { return platformFee; }
    public boolean isMock() { return mock; }
    public Instant getCreatedAt() { return createdAt; }
}
