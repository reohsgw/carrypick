package com.carrypick.domain;

/** All status / type enums in one place so they are easy to find. */
public final class Enums {
    private Enums() {}

    public enum Mode { BUYER, TRAVELER }

    public enum OfferStatus { ACTIVE, SOLD_OUT, CLOSED }

    public enum RequestStatus { OPEN, MATCHED, EXPIRED, CANCELLED }

    /**
     * Order status flow (see the planning doc):
     * PENDING_PAYMENT -> PAID -> (budget check) -> PURCHASED -> DELIVERED -> COMPLETED -> SETTLED
     *   PENDING_PAYMENT -> PAYMENT_FAILED -> (retry) PAID
     *   PAID -> AWAITING_APPROVAL (actual price > budget) -> approve+pay -> PURCHASED | reject -> CANCELLED
     *   PENDING_PAYMENT / PAYMENT_FAILED / PAID -> CANCELLED
     */
    public enum OrderStatus {
        PENDING_PAYMENT, PAYMENT_FAILED, PAID, AWAITING_APPROVAL, PURCHASED,
        DELIVERED, COMPLETED, SETTLED, CANCELLED
    }

    public enum PaymentType { INITIAL, EXTRA, REFUND }

    public enum PaymentStatus { READY, SUCCESS, FAILED }
}
