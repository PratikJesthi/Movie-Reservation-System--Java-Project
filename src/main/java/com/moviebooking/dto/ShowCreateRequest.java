package com.moviebooking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowCreateRequest(@NotNull Long movieId, @NotNull Long screenId,
                                @NotNull LocalDateTime showTime,
                                @NotNull @Positive BigDecimal price) {
}
