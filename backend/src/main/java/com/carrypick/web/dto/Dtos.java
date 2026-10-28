package com.carrypick.web.dto;

import com.carrypick.domain.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response bodies. Entities are never returned directly. */
public final class Dtos {
    private Dtos() {}

    public record UserDto(Long id, String email, String name, Enums.Mode mode, boolean test) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getName(), u.getCurrentMode(), u.isTest());
        }
    }

    public record PersonDto(Long id, String name) {
        public static PersonDto of(User u) {
            return u == null ? null : new PersonDto(u.getId(), u.getName());
        }
    }

    public record AuthResponse(String token, UserDto user) {}

    public record Quote(long budget, long reward, long fee, long tax, long total) {}

    public record TripDto(Long id, String countryCode, String city, LocalDate departDate, LocalDate returnDate,
                          String luggageNote) {
        public static TripDto of(Trip t) {
            return new TripDto(t.getId(), t.getCountryCode(), t.getCity(), t.getDepartDate(), t.getReturnDate(),
                    t.getLuggageNote());
        }
    }

    public record OfferDto(Long id, PersonDto traveler, TripDto trip, String countryCode, String productName,
                           String store, String category, String description, String imageUrl, long estPrice,
                           long reward, int quantity, Enums.OfferStatus status, boolean bookable, Quote unitQuote) {}

    public record RequestDto(Long id, PersonDto buyer, PersonDto traveler, String countryCode, String productName,
                             String productLink, String imageUrl, String options, String note, long maxBudget,
                             long reward, LocalDate deadline, Enums.RequestStatus status, Quote quote,
                             Instant createdAt, Long orderId) {}

    public record PaymentDto(Long id, Enums.PaymentType type, long amount, Enums.PaymentStatus status,
                             String failReason, boolean simulated, Instant createdAt) {
        public static PaymentDto of(Payment p) {
            return new PaymentDto(p.getId(), p.getType(), p.getAmount(), p.getStatus(), p.getFailReason(),
                    p.isSimulated(), p.getCreatedAt());
        }
    }

    public record SettlementDto(long travelerPayout, long buyerRefund, long platformFee, boolean mock,
                                Instant createdAt) {
        public static SettlementDto of(Settlement s) {
            return s == null ? null : new SettlementDto(s.getTravelerPayout(), s.getBuyerRefund(),
                    s.getPlatformFee(), s.isMock(), s.getCreatedAt());
        }
    }

    public record OrderDto(Long id, String myRole, Enums.OrderStatus status, String source, Long offerId,
                           Long requestId, PersonDto buyer, PersonDto traveler, String productName,
                           String countryCode, String imageUrl, String category, String options, String productLink,
                           int quantity,
                           long budget, long reward, long fee, long tax, long initialCharge, Long actualPrice,
                           String receiptUrl, long extraItemAmount, long extraFee, long extraCharge,
                           LocalDate expectedDelivery, String cancelReason,
                           Instant createdAt, Instant paidAt, Instant purchasedAt, Instant deliveredAt,
                           Instant completedAt, Instant settledAt, Instant cancelledAt,
                           List<PaymentDto> payments, SettlementDto settlement) {}

    public record NotificationDto(Long id, Long orderId, String kind, String actorName, String productName,
                                  Long amount, String message, boolean read, Instant createdAt) {
        public static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getOrderId(), n.getKind(), n.getActorName(), n.getProductName(),
                    n.getAmount(), n.getMessage(), n.isRead(), n.getCreatedAt());
        }
    }

    /** One upcoming trip on the buyer home "departures board". */
    public record BoardRow(Long tripId, String countryCode, String city, LocalDate departDate, LocalDate returnDate,
                           String travelerName, int offerCount) {}

    public record ReceiptItem(String name, Double price, Integer quantity, String category) {}

    /** Result of the AI receipt reader. Amounts in the receipt's own currency plus a KRW estimate. */
    public record ReceiptScan(boolean mock, String model, String store, String date, String currency, Double total,
                              Long totalKrw, Double krwPerUnit, List<ReceiptItem> items, List<String> customsFlags,
                              String note) {}

    public record RankingItem(int rank, String productName, long orderCount, Long offerId, String imageUrl,
                              String category) {}

    public record RankingDto(String countryCode, boolean demoData, int days, List<RankingItem> items) {}

    public record SearchDto(List<OfferDto> offers, List<RequestDto> requests) {}

    public record PaymentPrepareDto(String mode, String clientKey, String tossOrderId, String orderName,
                                    long amount, String type, String customerKey) {}

    public record ConfigDto(String paymentMode, String tossClientKey, double feeRate, long taxThresholdKrw,
                            double taxRate, List<Countries.Country> countries) {}
}
