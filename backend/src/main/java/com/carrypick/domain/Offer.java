package com.carrypick.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** A product a traveler offers to buy and bring back on a specific trip. Prices are KRW. */
@Entity
@Table(name = "offers")
public class Offer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User traveler;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Trip trip;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private String productName;

    private String store;
    private String category;

    @Column(length = 1000)
    private String description;

    private String imageUrl;

    /** Estimated item price per unit (KRW). */
    private long estPrice;

    /** Traveler reward per unit (KRW). */
    private long reward;

    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.OfferStatus status = Enums.OfferStatus.ACTIVE;

    private Instant createdAt = Instant.now();

    protected Offer() {}

    public Offer(User traveler, Trip trip, String productName, String store, String category, String description,
                 String imageUrl, long estPrice, long reward, int quantity) {
        this.traveler = traveler;
        this.trip = trip;
        this.countryCode = trip.getCountryCode();
        this.productName = productName;
        this.store = store;
        this.category = category;
        this.description = description;
        this.imageUrl = imageUrl;
        this.estPrice = estPrice;
        this.reward = reward;
        this.quantity = quantity;
    }

    /** Can a buyer book this right now? Active, stock left, and the trip has not started. */
    public boolean isBookable(LocalDate today) {
        return status == Enums.OfferStatus.ACTIVE && quantity > 0 && trip.getDepartDate().isAfter(today);
    }

    public void reserve(int qty) {
        this.quantity -= qty;
        if (this.quantity <= 0) {
            this.quantity = 0;
            this.status = Enums.OfferStatus.SOLD_OUT;
        }
    }

    public void release(int qty) {
        this.quantity += qty;
        if (this.status == Enums.OfferStatus.SOLD_OUT && this.quantity > 0) {
            this.status = Enums.OfferStatus.ACTIVE;
        }
    }

    public void update(String productName, String store, String category, String description, String imageUrl,
                       long estPrice, long reward, int quantity) {
        this.productName = productName;
        this.store = store;
        this.category = category;
        this.description = description;
        this.imageUrl = imageUrl;
        this.estPrice = estPrice;
        this.reward = reward;
        this.quantity = quantity;
        if (status == Enums.OfferStatus.SOLD_OUT && quantity > 0) status = Enums.OfferStatus.ACTIVE;
        if (status == Enums.OfferStatus.ACTIVE && quantity == 0) status = Enums.OfferStatus.SOLD_OUT;
    }

    public Long getId() { return id; }
    public User getTraveler() { return traveler; }
    public Trip getTrip() { return trip; }
    public String getCountryCode() { return countryCode; }
    public String getProductName() { return productName; }
    public String getStore() { return store; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public long getEstPrice() { return estPrice; }
    public long getReward() { return reward; }
    public int getQuantity() { return quantity; }
    public Enums.OfferStatus getStatus() { return status; }
    public void setStatus(Enums.OfferStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
