package com.carrypick.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One purchase between a buyer and a traveler. Created either by booking an Offer (Flow A)
 * or by a traveler accepting a PurchaseRequest (Flow B). All amounts are KRW.
 */
@Entity(name = "CpOrder")
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User traveler;

    @ManyToOne(fetch = FetchType.EAGER)
    private Offer offer;

    @ManyToOne(fetch = FetchType.EAGER)
    private PurchaseRequest request;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, length = 2)
    private String countryCode;

    private String imageUrl;

    private int quantity;

    /** B: item budget for the whole order (offer: est. price x qty, request: max budget). */
    private long budget;
    /** R: traveler reward for the whole order. */
    private long reward;
    /** F: platform fee on (B + R). */
    private long fee;
    /** T: estimated tax/duty. */
    private long tax;

    /** P: actual item price from the receipt. */
    private Long actualPrice;
    private String receiptUrl;

    /** Extra item amount when P > B (P - B). */
    private long extraItemAmount;
    /** Fee charged on the extra amount. */
    private long extraFee;

    private LocalDate expectedDelivery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.OrderStatus status = Enums.OrderStatus.PENDING_PAYMENT;

    private String cancelReason;

    private Instant createdAt = Instant.now();
    private Instant paidAt;
    private Instant purchasedAt;
    private Instant deliveredAt;
    private Instant completedAt;
    private Instant settledAt;
    private Instant cancelledAt;

    protected Order() {}

    public Order(User buyer, User traveler, Offer offer, PurchaseRequest request, String productName,
                 String countryCode, String imageUrl, int quantity, long budget, long reward, long fee, long tax,
                 LocalDate expectedDelivery) {
        this.buyer = buyer;
        this.traveler = traveler;
        this.offer = offer;
        this.request = request;
        this.productName = productName;
        this.countryCode = countryCode;
        this.imageUrl = imageUrl;
        this.quantity = quantity;
        this.budget = budget;
        this.reward = reward;
        this.fee = fee;
        this.tax = tax;
        this.expectedDelivery = expectedDelivery;
    }

    /** Amount charged at the first payment: B + R + F + T. */
    public long initialCharge() {
        return budget + reward + fee + tax;
    }

    /** Amount charged when the buyer approves an over-budget receipt. */
    public long extraCharge() {
        return extraItemAmount + extraFee;
    }

    public boolean isBuyer(Long userId) { return buyer.getId().equals(userId); }
    public boolean isTraveler(Long userId) { return traveler.getId().equals(userId); }

    // --- getters / setters ---
    public Long getId() { return id; }
    public User getBuyer() { return buyer; }
    public User getTraveler() { return traveler; }
    public Offer getOffer() { return offer; }
    public PurchaseRequest getRequest() { return request; }
    public String getProductName() { return productName; }
    public String getCountryCode() { return countryCode; }
    public String getImageUrl() { return imageUrl; }
    public int getQuantity() { return quantity; }
    public long getBudget() { return budget; }
    public long getReward() { return reward; }
    public long getFee() { return fee; }
    public long getTax() { return tax; }
    public Long getActualPrice() { return actualPrice; }
    public void setActualPrice(Long actualPrice) { this.actualPrice = actualPrice; }
    public String getReceiptUrl() { return receiptUrl; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }
    public long getExtraItemAmount() { return extraItemAmount; }
    public void setExtraItemAmount(long extraItemAmount) { this.extraItemAmount = extraItemAmount; }
    public long getExtraFee() { return extraFee; }
    public void setExtraFee(long extraFee) { this.extraFee = extraFee; }
    public LocalDate getExpectedDelivery() { return expectedDelivery; }
    public Enums.OrderStatus getStatus() { return status; }
    public void setStatus(Enums.OrderStatus status) { this.status = status; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Instant getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(Instant purchasedAt) { this.purchasedAt = purchasedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
