package com.carrypick.service;

import com.carrypick.common.ApiException;
import com.carrypick.config.AppProperties;
import com.carrypick.domain.Enums;
import com.carrypick.domain.Enums.OrderStatus;
import com.carrypick.domain.Enums.PaymentType;
import com.carrypick.domain.Order;
import com.carrypick.domain.Payment;
import com.carrypick.repo.PaymentRepository;
import com.carrypick.web.dto.Dtos.OrderDto;
import com.carrypick.web.dto.Dtos.PaymentPrepareDto;
import com.carrypick.web.dto.Forms.ConfirmForm;
import com.carrypick.web.dto.Forms.FailForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment flow (same for mock and Toss test mode):
 *   1. prepare  - server decides the amount and creates a READY payment with a unique tossOrderId
 *   2. browser  - opens the payment window (Toss SDK or the mock page) with that amount
 *   3. confirm  - server checks the amount matches what it stored, then confirms with Toss
 *      fail     - browser reports a cancelled/declined payment
 */
@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final OrderService orderService;
    private final TossClient toss;
    private final AppProperties.Payment cfg;
    private final Mapper mapper;

    public PaymentService(PaymentRepository payments, OrderService orderService, TossClient toss,
                          AppProperties props, Mapper mapper) {
        this.payments = payments;
        this.orderService = orderService;
        this.toss = toss;
        this.cfg = props.payment();
        this.mapper = mapper;
    }

    @Transactional
    public PaymentPrepareDto prepare(Long userId, Long orderId) {
        Order o = orderService.findForParticipant(userId, orderId);
        OrderService.requireBuyer(o, userId);

        PaymentType type;
        long amount;
        if (o.getStatus() == OrderStatus.PENDING_PAYMENT || o.getStatus() == OrderStatus.PAYMENT_FAILED) {
            type = PaymentType.INITIAL;
            amount = o.initialCharge();
        } else if (o.getStatus() == OrderStatus.AWAITING_APPROVAL) {
            type = PaymentType.EXTRA;
            amount = o.extraCharge();
        } else {
            throw ApiException.conflict("NOTHING_TO_PAY", "There is nothing to pay for this order right now");
        }
        // Toss orderId: 6-64 chars, letters, numbers, - and _ only, unique per attempt.
        String tossOrderId = "CP-" + o.getId() + "-" + type.name().charAt(0) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        payments.save(new Payment(o, type, amount, tossOrderId, cfg.isMock()));

        String orderName = (type == PaymentType.EXTRA ? "[Extra] " : "") + o.getProductName();
        if (orderName.length() > 90) orderName = orderName.substring(0, 90);
        return new PaymentPrepareDto(cfg.isMock() ? "mock" : "toss", cfg.isMock() ? null : cfg.tossClientKey(),
                tossOrderId, orderName, amount, type.name(), customerKey(userId));
    }

    @Transactional
    public OrderDto confirm(Long userId, ConfirmForm f) {
        Payment p = payments.findByTossOrderId(f.tossOrderId())
                .orElseThrow(() -> ApiException.notFound("Payment"));
        Order o = orderService.findForParticipant(userId, p.getOrder().getId());
        OrderService.requireBuyer(o, userId);

        if (p.getStatus() == Enums.PaymentStatus.SUCCESS) {
            return mapper.order(o, userId); // already confirmed (e.g. page refreshed)
        }
        if (p.getStatus() != Enums.PaymentStatus.READY) {
            throw ApiException.conflict("PAYMENT_CLOSED", "This payment attempt is already closed");
        }
        // Never trust the amount from the browser: it must equal what the server stored.
        if (f.amount() != p.getAmount()) {
            markFailed(p, o, "Amount mismatch");
            throw ApiException.badRequest("AMOUNT_MISMATCH", "Payment amount does not match the order");
        }
        // The order must still be waiting for this kind of payment.
        boolean statusOk = p.getType() == PaymentType.INITIAL
                ? (o.getStatus() == OrderStatus.PENDING_PAYMENT || o.getStatus() == OrderStatus.PAYMENT_FAILED)
                : o.getStatus() == OrderStatus.AWAITING_APPROVAL;
        if (!statusOk) {
            markFailed(p, o, "Order status changed");
            throw ApiException.conflict("WRONG_STATUS", "The order is no longer waiting for this payment");
        }

        TossClient.Result result = toss.confirm(f.paymentKey(), f.tossOrderId(), f.amount());
        p.setPaymentKey(f.paymentKey());
        if (!result.success()) {
            markFailed(p, o, result.message());
            return mapper.order(o, userId);
        }
        p.setStatus(Enums.PaymentStatus.SUCCESS);
        p.setCompletedAt(Instant.now());
        if (p.getType() == PaymentType.INITIAL) {
            orderService.onInitialPaymentSuccess(o);
        } else {
            orderService.onExtraPaymentSuccess(o);
        }
        return mapper.order(o, userId);
    }

    @Transactional
    public OrderDto fail(Long userId, FailForm f) {
        Payment p = payments.findByTossOrderId(f.tossOrderId())
                .orElseThrow(() -> ApiException.notFound("Payment"));
        Order o = orderService.findForParticipant(userId, p.getOrder().getId());
        OrderService.requireBuyer(o, userId);
        if (p.getStatus() == Enums.PaymentStatus.READY) {
            String reason = f.message() != null && !f.message().isBlank() ? f.message()
                    : (f.code() != null ? f.code() : "Payment cancelled");
            markFailed(p, o, reason);
        }
        return mapper.order(o, userId);
    }

    /** Toss asks for a non-guessable customer key (2-50 chars), so derive a stable UUID per user. */
    private String customerKey(Long userId) {
        return UUID.nameUUIDFromBytes(("carrypick-customer-" + userId + "-" + cfg.tossClientKey())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private void markFailed(Payment p, Order o, String reason) {
        p.setStatus(Enums.PaymentStatus.FAILED);
        p.setFailReason(reason);
        p.setCompletedAt(Instant.now());
        if (p.getType() == PaymentType.INITIAL && o.getStatus() == OrderStatus.PENDING_PAYMENT) {
            orderService.onInitialPaymentFailed(o, reason);
        }
        // A failed EXTRA payment leaves the order in AWAITING_APPROVAL so the buyer can retry or reject.
    }
}
