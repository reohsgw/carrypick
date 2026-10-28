package com.carrypick.service;

import com.carrypick.domain.Offer;
import com.carrypick.domain.Order;
import com.carrypick.domain.PurchaseRequest;
import com.carrypick.repo.OrderRepository;
import com.carrypick.repo.PaymentRepository;
import com.carrypick.repo.SettlementRepository;
import com.carrypick.web.dto.Dtos.*;
import org.springframework.stereotype.Component;

/** Converts entities to response DTOs. */
@Component
public class Mapper {
    private final PricingService pricing;
    private final PaymentRepository payments;
    private final SettlementRepository settlements;
    private final OrderRepository orders;

    public Mapper(PricingService pricing, PaymentRepository payments, SettlementRepository settlements,
                  OrderRepository orders) {
        this.pricing = pricing;
        this.payments = payments;
        this.settlements = settlements;
        this.orders = orders;
    }

    public OfferDto offer(Offer o) {
        return new OfferDto(o.getId(), PersonDto.of(o.getTraveler()), TripDto.of(o.getTrip()), o.getCountryCode(),
                o.getProductName(), o.getStore(), o.getCategory(), o.getDescription(), o.getImageUrl(),
                o.getEstPrice(), o.getReward(), o.getQuantity(), o.getStatus(), o.isBookable(Today.get()),
                pricing.quote(o.getEstPrice(), o.getReward()));
    }

    public RequestDto request(PurchaseRequest r) {
        Long orderId = r.getTraveler() == null ? null
                : orders.findFirstByRequestIdOrderByCreatedAtDesc(r.getId()).map(Order::getId).orElse(null);
        return new RequestDto(r.getId(), PersonDto.of(r.getBuyer()), PersonDto.of(r.getTraveler()),
                r.getCountryCode(), r.getProductName(), r.getProductLink(), r.getImageUrl(), r.getOptions(),
                r.getNote(), r.getMaxBudget(), r.getReward(), r.getDeadline(), r.getStatus(),
                pricing.quote(r.getMaxBudget(), r.getReward()), r.getCreatedAt(), orderId);
    }

    /** Full order view for the order detail screens. */
    public OrderDto order(Order o, Long viewerId) {
        String role = o.isBuyer(viewerId) ? "BUYER" : "TRAVELER";
        return build(o, role, true);
    }

    /** Lighter order view for lists (no payments / settlement lookups). */
    public OrderDto orderSummary(Order o, Long viewerId) {
        String role = o.isBuyer(viewerId) ? "BUYER" : "TRAVELER";
        return build(o, role, false);
    }

    private OrderDto build(Order o, String role, boolean full) {
        PurchaseRequest r = o.getRequest();
        return new OrderDto(o.getId(), role, o.getStatus(), r != null ? "REQUEST" : "OFFER",
                o.getOffer() != null ? o.getOffer().getId() : null, r != null ? r.getId() : null,
                PersonDto.of(o.getBuyer()), PersonDto.of(o.getTraveler()), o.getProductName(), o.getCountryCode(),
                o.getImageUrl(), o.getOffer() != null ? o.getOffer().getCategory() : null,
                r != null ? r.getOptions() : null, r != null ? r.getProductLink() : null,
                o.getQuantity(), o.getBudget(), o.getReward(), o.getFee(), o.getTax(), o.initialCharge(),
                o.getActualPrice(), o.getReceiptUrl(), o.getExtraItemAmount(), o.getExtraFee(), o.extraCharge(),
                o.getExpectedDelivery(), o.getCancelReason(), o.getCreatedAt(), o.getPaidAt(), o.getPurchasedAt(),
                o.getDeliveredAt(), o.getCompletedAt(), o.getSettledAt(), o.getCancelledAt(),
                full ? payments.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream().map(PaymentDto::of).toList() : java.util.List.of(),
                full ? SettlementDto.of(settlements.findByOrderId(o.getId()).orElse(null)) : null);
    }
}
