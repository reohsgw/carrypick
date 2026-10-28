package com.carrypick.web;

import com.carrypick.config.AuthUser;
import com.carrypick.domain.Notification;
import com.carrypick.repo.NotificationRepository;
import com.carrypick.service.OrderService;
import com.carrypick.service.PaymentService;
import com.carrypick.web.dto.Dtos.NotificationDto;
import com.carrypick.web.dto.Dtos.OrderDto;
import com.carrypick.web.dto.Dtos.PaymentPrepareDto;
import com.carrypick.web.dto.Forms.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Orders, payments and notifications. */
@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders;
    private final PaymentService payments;
    private final NotificationRepository notifications;

    public OrderController(OrderService orders, PaymentService payments, NotificationRepository notifications) {
        this.orders = orders;
        this.payments = payments;
        this.notifications = notifications;
    }

    // ---- orders

    /** B4: book an offer (Flow A). */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto book(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody BookForm form) {
        return orders.bookOffer(me.id(), form);
    }

    /** B7 (role=buyer) and T1 (role=traveler). */
    @GetMapping("/orders/me")
    public List<OrderDto> myOrders(@AuthenticationPrincipal AuthUser me,
                                   @RequestParam(defaultValue = "buyer") String role) {
        return orders.myOrders(me.id(), role);
    }

    /** B8 / T7 */
    @GetMapping("/orders/{id}")
    public OrderDto get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return orders.get(me.id(), id);
    }

    /** T7: actual price + receipt image (multipart form). */
    @PostMapping(value = "/orders/{id}/purchase", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrderDto purchase(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                             @RequestParam long actualPrice, @RequestPart("receipt") MultipartFile receipt) {
        return orders.markPurchased(me.id(), id, actualPrice, receipt);
    }

    /** B8: approve the over-budget price. Returns the extra payment to open in the payment window. */
    @PostMapping("/orders/{id}/approve")
    public PaymentPrepareDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return payments.prepare(me.id(), id);
    }

    /** B8 */
    @PostMapping("/orders/{id}/reject")
    public OrderDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return orders.rejectOverBudget(me.id(), id);
    }

    /** T7 */
    @PostMapping("/orders/{id}/deliver")
    public OrderDto deliver(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return orders.markDelivered(me.id(), id);
    }

    /** B8 */
    @PostMapping("/orders/{id}/confirm")
    public OrderDto confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return orders.confirmReceived(me.id(), id);
    }

    /** B8 / T7 */
    @PostMapping("/orders/{id}/cancel")
    public OrderDto cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                           @RequestBody(required = false) CancelForm form) {
        return orders.cancel(me.id(), id, form == null ? null : form.reason());
    }

    // ---- payments (B6)

    @PostMapping("/payments/prepare")
    public PaymentPrepareDto prepare(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PrepareForm form) {
        return payments.prepare(me.id(), form.orderId());
    }

    @PostMapping("/payments/confirm")
    public OrderDto confirmPayment(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ConfirmForm form) {
        return payments.confirm(me.id(), form);
    }

    @PostMapping("/payments/fail")
    public OrderDto failPayment(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody FailForm form) {
        return payments.fail(me.id(), form);
    }

    // ---- notifications (C3)

    @GetMapping("/notifications")
    public List<NotificationDto> notifications(@AuthenticationPrincipal AuthUser me) {
        return notifications.findTop50ByUserIdOrderByCreatedAtDesc(me.id()).stream().map(NotificationDto::of).toList();
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public void readAll(@AuthenticationPrincipal AuthUser me) {
        for (Notification n : notifications.findTop50ByUserIdOrderByCreatedAtDesc(me.id())) {
            if (!n.isRead()) n.setRead(true);
        }
    }
}
