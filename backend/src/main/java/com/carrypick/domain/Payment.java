package com.carrypick.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** One payment attempt (or simulated refund) for an order. */
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.PaymentType type;

    private long amount;

    /** The orderId we send to Toss (must be unique per payment attempt). */
    @Column(nullable = false, unique = true)
    private String tossOrderId;

    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.PaymentStatus status = Enums.PaymentStatus.READY;

    private String failReason;

    /** True for refunds and mock-mode payments: no real money moved. */
    private boolean simulated;

    private Instant createdAt = Instant.now();
    private Instant completedAt;

    protected Payment() {}

    public Payment(Order order, Enums.PaymentType type, long amount, String tossOrderId, boolean simulated) {
        this.order = order;
        this.type = type;
        this.amount = amount;
        this.tossOrderId = tossOrderId;
        this.simulated = simulated;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public Enums.PaymentType getType() { return type; }
    public long getAmount() { return amount; }
    public String getTossOrderId() { return tossOrderId; }
    public String getPaymentKey() { return paymentKey; }
    public void setPaymentKey(String paymentKey) { this.paymentKey = paymentKey; }
    public Enums.PaymentStatus getStatus() { return status; }
    public void setStatus(Enums.PaymentStatus status) { this.status = status; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public boolean isSimulated() { return simulated; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
