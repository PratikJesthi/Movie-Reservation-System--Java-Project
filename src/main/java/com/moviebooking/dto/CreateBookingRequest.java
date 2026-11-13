package com.moviebooking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CreateBookingRequest(
        @NotNull @Positive Long showId,
        @NotEmpty List<@NotNull @Positive Long> seatIds) {
}
