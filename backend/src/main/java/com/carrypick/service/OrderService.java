package com.carrypick.service;

import com.carrypick.common.ApiException;
import com.carrypick.domain.*;
import com.carrypick.domain.Enums.OrderStatus;
import com.carrypick.repo.*;
import com.carrypick.web.dto.Dtos.OrderDto;
import com.carrypick.web.dto.Dtos.Quote;
import com.carrypick.web.dto.Forms.BookForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Order creation and every status change. Each action checks:
 *   1. who is calling (buyer or traveler of this order), and
 *   2. that the order is in a status where this action is allowed.
 */
@Service
public class OrderService {
    private static final Set<OrderStatus> CANCELLABLE =
            EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED, OrderStatus.PAID);

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final SettlementRepository settlements;
    private final TripRepository trips;
    private final MarketService market;
    private final AuthService auth;
    private final PricingService pricing;
    private final NotificationService notifications;
    private final FileStorageService files;
    private final Mapper mapper;

    public OrderService(OrderRepository orders, PaymentRepository payments, SettlementRepository settlements,
                        TripRepository trips, MarketService market, AuthService auth, PricingService pricing,
                        NotificationService notifications, FileStorageService files, Mapper mapper) {
        this.orders = orders;
        this.payments = payments;
        this.settlements = settlements;
        this.trips = trips;
        this.market = market;
        this.auth = auth;
        this.pricing = pricing;
        this.notifications = notifications;
        this.files = files;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- create

    /** Flow A: buyer books an existing offer (B4). */
    @Transactional
    public OrderDto bookOffer(Long userId, BookForm f) {
        User buyer = auth.require(userId);
        Offer offer = market.findOffer(f.offerId());
        if (offer.getTraveler().getId().equals(userId)) {
            throw ApiException.badRequest("OWN_OFFER", "You cannot book your own offer");
        }
        if (!offer.isBookable(Today.get())) {
            throw ApiException.conflict("OFFER_UNAVAILABLE", "This offer can no longer be booked");
        }
        if (f.quantity() > offer.getQuantity()) {
            throw ApiException.conflict("NOT_ENOUGH_STOCK", "Only " + offer.getQuantity() + " left");
        }
        offer.reserve(f.quantity());
        Quote q = pricing.quote(offer.getEstPrice() * f.quantity(), offer.getReward() * f.quantity());
        Order o = orders.save(new Order(buyer, offer.getTraveler(), offer, null, offer.getProductName(),
                offer.getCountryCode(), offer.getImageUrl(), f.quantity(), q.budget(), q.reward(), q.fee(), q.tax(),
                offer.getTrip().getReturnDate()));
        notifications.notify(offer.getTraveler(), o.getId(), NotificationService.Kind.BOOKED, buyer.getName(),
                offer.getProductName(), (long) f.quantity(),
                buyer.getName() + " booked " + f.quantity() + " x " + offer.getProductName() + ". Waiting for payment.");
        return mapper.order(o, userId);
    }

    /** Flow B: traveler accepts an open request (T6). */
    @Transactional
    public OrderDto acceptRequest(Long userId, Long requestId, Long tripId) {
        User traveler = auth.require(userId);
        PurchaseRequest r = market.findRequest(requestId);
        if (r.getBuyer().getId().equals(userId)) {
            throw ApiException.badRequest("OWN_REQUEST", "You cannot accept your own request");
        }
        if (r.getStatus() != Enums.RequestStatus.OPEN || r.getDeadline().isBefore(Today.get())) {
            throw ApiException.conflict("REQUEST_NOT_OPEN", "This request is no longer open");
        }
        List<Trip> myTrips = trips.findByTravelerIdAndReturnDateGreaterThanEqualOrderByDepartDateAsc(userId, Today.get());
        if (tripId != null) {
            myTrips = myTrips.stream().filter(t -> t.getId().equals(tripId)).toList();
        }
        Trip trip = market.matchingTrip(myTrips, r);
        if (trip == null) {
            throw ApiException.conflict("NO_MATCHING_TRIP",
                    "You need a trip to this country that returns on or before " + r.getDeadline());
        }
        r.setTraveler(traveler);
        r.setStatus(Enums.RequestStatus.MATCHED);
        Quote q = pricing.quote(r.getMaxBudget(), r.getReward());
        Order o = orders.save(new Order(r.getBuyer(), traveler, null, r, r.getProductName(), r.getCountryCode(),
                r.getImageUrl(), 1, q.budget(), q.reward(), q.fee(), q.tax(), trip.getReturnDate()));
        notifications.notify(r.getBuyer(), o.getId(), NotificationService.Kind.REQUEST_ACCEPTED, traveler.getName(),
                r.getProductName(), null,
                traveler.getName() + " accepted your request for " + r.getProductName() + ". Please complete payment.");
        return mapper.order(o, userId);
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public List<OrderDto> myOrders(Long userId, String role) {
        List<Order> list = "traveler".equalsIgnoreCase(role)
                ? orders.findByTravelerIdOrderByCreatedAtDesc(userId)
                : orders.findByBuyerIdOrderByCreatedAtDesc(userId);
        return list.stream().map(o -> mapper.orderSummary(o, userId)).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long userId, Long orderId) {
        return mapper.order(findForParticipant(userId, orderId), userId);
    }

    // ---------------------------------------------------------------- traveler actions (T7)

    /** Traveler bought the item: enter the actual price and upload the receipt. */
    @Transactional
    public OrderDto markPurchased(Long userId, Long orderId, long actualPrice, MultipartFile receipt) {
        Order o = findForParticipant(userId, orderId);
        requireTraveler(o, userId);
        requireStatus(o, OrderStatus.PAID);
        if (actualPrice <= 0) throw ApiException.badRequest("PRICE", "Actual price must be greater than 0");
        o.setReceiptUrl(files.store(receipt));
        o.setActualPrice(actualPrice);
        if (actualPrice <= o.getBudget()) {
            o.setStatus(OrderStatus.PURCHASED);
            o.setPurchasedAt(Instant.now());
            notifications.notify(o.getBuyer(), o.getId(), NotificationService.Kind.PURCHASED, o.getTraveler().getName(),
                    o.getProductName(), actualPrice, o.getTraveler().getName() + " bought " + o.getProductName()
                    + ". Receipt uploaded.");
        } else {
            long extra = actualPrice - o.getBudget();
            o.setExtraItemAmount(extra);
            o.setExtraFee(pricing.feeOn(extra));
            o.setStatus(OrderStatus.AWAITING_APPROVAL);
            notifications.notify(o.getBuyer(), o.getId(), NotificationService.Kind.OVER_BUDGET, o.getTraveler().getName(),
                    o.getProductName(), extra, "The actual price of " + o.getProductName()
                    + " is over your budget by " + extra + " KRW. Please approve or reject.");
        }
        return mapper.order(o, userId);
    }

    @Transactional
    public OrderDto markDelivered(Long userId, Long orderId) {
        Order o = findForParticipant(userId, orderId);
        requireTraveler(o, userId);
        requireStatus(o, OrderStatus.PURCHASED);
        o.setStatus(OrderStatus.DELIVERED);
        o.setDeliveredAt(Instant.now());
        notifications.notify(o.getBuyer(), o.getId(), NotificationService.Kind.DELIVERED, o.getTraveler().getName(),
                o.getProductName(), null, o.getTraveler().getName() + " handed over "
                + o.getProductName() + ". Please confirm you received it.");
        return mapper.order(o, userId);
    }

    // ---------------------------------------------------------------- buyer actions (B8)

    /** Buyer rejects an over-budget receipt: order cancelled and a refund is simulated. */
    @Transactional
    public OrderDto rejectOverBudget(Long userId, Long orderId) {
        Order o = findForParticipant(userId, orderId);
        requireBuyer(o, userId);
        requireStatus(o, OrderStatus.AWAITING_APPROVAL);
        refundAll(o);
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelledAt(Instant.now());
        o.setCancelReason("Buyer rejected the over-budget price");
        notifications.notify(o.getTraveler(), o.getId(), NotificationService.Kind.REJECTED, o.getBuyer().getName(),
                o.getProductName(), null, o.getBuyer().getName() + " rejected the over-budget price for "
                + o.getProductName() + ". The order was cancelled.");
        return mapper.order(o, userId);
    }

    /** Buyer confirms receipt: order completed and settlement is simulated immediately. */
    @Transactional
    public OrderDto confirmReceived(Long userId, Long orderId) {
        Order o = findForParticipant(userId, orderId);
        requireBuyer(o, userId);
        requireStatus(o, OrderStatus.DELIVERED);
        Instant now = Instant.now();
        o.setStatus(OrderStatus.COMPLETED);
        o.setCompletedAt(now);
        settle(o);
        notifications.notify(o.getTraveler(), o.getId(), NotificationService.Kind.SETTLED, o.getBuyer().getName(),
                o.getProductName(), o.getActualPrice() == null ? null : o.getActualPrice() + o.getReward(),
                o.getBuyer().getName() + " confirmed receipt of "
                + o.getProductName() + ". Your payout was settled (simulated).");
        return mapper.order(o, userId);
    }

    /** Either side cancels before the item is purchased. */
    @Transactional
    public OrderDto cancel(Long userId, Long orderId, String reason) {
        Order o = findForParticipant(userId, orderId);
        if (!CANCELLABLE.contains(o.getStatus())) {
            throw ApiException.conflict("CANNOT_CANCEL", "Orders can only be cancelled before the item is purchased");
        }
        boolean byBuyer = o.isBuyer(userId);
        if (o.getStatus() == OrderStatus.PAID) refundAll(o);
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelledAt(Instant.now());
        o.setCancelReason(reason == null || reason.isBlank()
                ? (byBuyer ? "Cancelled by buyer" : "Cancelled by traveler") : reason.trim());

        if (o.getOffer() != null) {
            o.getOffer().release(o.getQuantity());
        }
        PurchaseRequest r = o.getRequest();
        if (r != null) {
            if (byBuyer) {
                r.setStatus(Enums.RequestStatus.CANCELLED);
            } else {
                // Traveler backed out: put the request back on the board.
                r.setTraveler(null);
                r.setStatus(r.getDeadline().isBefore(Today.get()) ? Enums.RequestStatus.EXPIRED : Enums.RequestStatus.OPEN);
            }
        }
        User other = byBuyer ? o.getTraveler() : o.getBuyer();
        notifications.notify(other, o.getId(), NotificationService.Kind.CANCELLED,
                (byBuyer ? o.getBuyer() : o.getTraveler()).getName(), o.getProductName(), null, "Order for " + o.getProductName() + " was cancelled. " + o.getCancelReason());
        return mapper.order(o, userId);
    }

    // ---------------------------------------------------------------- called by PaymentService

    public void onInitialPaymentSuccess(Order o) {
        o.setStatus(OrderStatus.PAID);
        o.setPaidAt(Instant.now());
        notifications.notify(o.getTraveler(), o.getId(), NotificationService.Kind.PAID, o.getBuyer().getName(),
                o.getProductName(), o.initialCharge(), o.getBuyer().getName() + " paid for " + o.getProductName()
                + ". You can buy it on your trip.");
    }

    public void onInitialPaymentFailed(Order o, String reason) {
        o.setStatus(OrderStatus.PAYMENT_FAILED);
    }

    public void onExtraPaymentSuccess(Order o) {
        o.setStatus(OrderStatus.PURCHASED);
        o.setPurchasedAt(Instant.now());
        notifications.notify(o.getTraveler(), o.getId(), NotificationService.Kind.EXTRA_PAID, o.getBuyer().getName(),
                o.getProductName(), o.getExtraItemAmount(), o.getBuyer().getName() + " approved and paid the extra "
                + o.getExtraItemAmount() + " KRW for " + o.getProductName() + ".");
    }

    // ---------------------------------------------------------------- helpers

    public Order findForParticipant(Long userId, Long orderId) {
        Order o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (!o.isBuyer(userId) && !o.isTraveler(userId)) {
            // Same answer as "not found" so order ids cannot be probed.
            throw ApiException.notFound("Order");
        }
        return o;
    }

    public static void requireBuyer(Order o, Long userId) {
        if (!o.isBuyer(userId)) throw ApiException.forbidden("Only the buyer can do this");
    }

    public static void requireTraveler(Order o, Long userId) {
        if (!o.isTraveler(userId)) throw ApiException.forbidden("Only the traveler can do this");
    }

    public static void requireStatus(Order o, OrderStatus expected) {
        if (o.getStatus() != expected) {
            throw ApiException.conflict("WRONG_STATUS",
                    "This action needs status " + expected + " but the order is " + o.getStatus());
        }
    }

    /** Simulated refund of everything the buyer paid. */
    private void refundAll(Order o) {
        long paid = payments.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream()
                .filter(p -> p.getStatus() == Enums.PaymentStatus.SUCCESS && p.getType() != Enums.PaymentType.REFUND)
                .mapToLong(Payment::getAmount).sum();
        if (paid > 0) {
            Payment refund = new Payment(o, Enums.PaymentType.REFUND, paid, "refund-" + o.getId() + "-" + System.nanoTime(), true);
            refund.setStatus(Enums.PaymentStatus.SUCCESS);
            refund.setCompletedAt(Instant.now());
            payments.save(refund);
        }
    }

    /**
     * Simulated settlement:
     *   traveler payout = P + R
     *   buyer refund    = B - P when the receipt is under budget (0 otherwise)
     *   platform keeps  = F + extra fee
     */
    private void settle(Order o) {
        long actual = o.getActualPrice() == null ? o.getBudget() : o.getActualPrice();
        long payout = actual + o.getReward();
        long refund = Math.max(0, o.getBudget() - actual);
        long platform = o.getFee() + o.getExtraFee();
        settlements.save(new Settlement(o, payout, refund, platform));
        if (refund > 0) {
            Payment p = new Payment(o, Enums.PaymentType.REFUND, refund, "refund-" + o.getId() + "-" + System.nanoTime(), true);
            p.setStatus(Enums.PaymentStatus.SUCCESS);
            p.setCompletedAt(Instant.now());
            payments.save(p);
        }
        o.setStatus(OrderStatus.SETTLED);
        o.setSettledAt(Instant.now());
    }
}
