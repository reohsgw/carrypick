package com.carrypick.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User user;

    /** Order this notification is about (nullable). */
    private Long orderId;

    @Column(nullable = false, length = 500)
    private String message;

    /** Message type so the app can show it in the user's language (see NotificationService.Kind). */
    @Column(length = 40)
    private String kind;

    private String actorName;
    private String productName;
    private Long amount;

    @Column(name = "is_read")
    private boolean read;

    private Instant createdAt = Instant.now();

    protected Notification() {}

    public Notification(User user, Long orderId, String kind, String actorName, String productName, Long amount,
                        String message) {
        this.user = user;
        this.orderId = orderId;
        this.kind = kind;
        this.actorName = actorName;
        this.productName = productName;
        this.amount = amount;
        this.message = message;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Long getOrderId() { return orderId; }
    public String getMessage() { return message; }
    public String getKind() { return kind; }
    public String getActorName() { return actorName; }
    public String getProductName() { return productName; }
    public Long getAmount() { return amount; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Instant getCreatedAt() { return createdAt; }
}
