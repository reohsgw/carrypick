package com.carrypick.web.dto;

import com.carrypick.domain.Enums;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Request bodies sent by the React app. */
public final class Forms {
    private Forms() {}

    public record SignupForm(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 40) String name,
            Enums.Mode mode) {}

    public record LoginForm(@NotBlank String email, @NotBlank String password) {}

    public record UpdateMeForm(@Size(max = 40) String name, Enums.Mode mode) {}

    public record TripForm(
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @Size(max = 60) String city,
            @NotNull LocalDate departDate,
            @NotNull LocalDate returnDate,
            @Size(max = 500) String luggageNote) {}

    public record OfferForm(
            @NotNull Long tripId,
            @NotBlank @Size(max = 120) String productName,
            @Size(max = 80) String store,
            @Size(max = 40) String category,
            @Size(max = 1000) String description,
            @Size(max = 500) String imageUrl,
            @Positive long estPrice,
            @PositiveOrZero long reward,
            @Min(1) @Max(99) int quantity) {}

    public record RequestForm(
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @NotBlank @Size(max = 120) String productName,
            @Size(max = 1000) String productLink,
            @Size(max = 500) String imageUrl,
            @Size(max = 200) String options,
            @Size(max = 1000) String note,
            @Positive long maxBudget,
            @PositiveOrZero long reward,
            @NotNull LocalDate deadline) {}

    public record BookForm(@NotNull Long offerId, @Min(1) @Max(99) int quantity) {}

    public record AcceptForm(Long tripId) {}

    public record CancelForm(@Size(max = 200) String reason) {}

    public record PrepareForm(@NotNull Long orderId) {}

    public record ConfirmForm(@NotBlank String paymentKey, @NotBlank String tossOrderId, @Positive long amount) {}

    public record FailForm(@NotBlank String tossOrderId, String code, String message) {}
}
