package com.carrypick.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** A buyer's request for a product no traveler has offered yet. Prices are KRW. */
@Entity
@Table(name = "purchase_requests")
public class PurchaseRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User buyer;

    /** Set when a traveler accepts. */
    @ManyToOne(fetch = FetchType.EAGER)
    private User traveler;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private String productName;

    @Column(length = 1000)
    private String productLink;

    private String imageUrl;

    private String options;

    @Column(length = 1000)
    private String note;

    /** Max budget for the item itself (B). */
    private long maxBudget;

    /** Reward offered to the traveler (R). */
    private long reward;

    @Column(nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.RequestStatus status = Enums.RequestStatus.OPEN;

    private Instant createdAt = Instant.now();

    protected PurchaseRequest() {}

    public PurchaseRequest(User buyer, String countryCode, String productName, String productLink, String imageUrl,
                           String options, String note, long maxBudget, long reward, LocalDate deadline) {
        this.buyer = buyer;
        this.countryCode = countryCode;
        this.productName = productName;
        this.productLink = productLink;
        this.imageUrl = imageUrl;
        this.options = options;
        this.note = note;
        this.maxBudget = maxBudget;
        this.reward = reward;
        this.deadline = deadline;
    }

    public Long getId() { return id; }
    public User getBuyer() { return buyer; }
    public User getTraveler() { return traveler; }
    public void setTraveler(User traveler) { this.traveler = traveler; }
    public String getCountryCode() { return countryCode; }
    public String getProductName() { return productName; }
    public String getProductLink() { return productLink; }
    public String getImageUrl() { return imageUrl; }
    public String getOptions() { return options; }
    public String getNote() { return note; }
    public long getMaxBudget() { return maxBudget; }
    public long getReward() { return reward; }
    public LocalDate getDeadline() { return deadline; }
    public Enums.RequestStatus getStatus() { return status; }
    public void setStatus(Enums.RequestStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
