package com.moviebooking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowResponse(Long id, Long movieId, Long screenId, String theatreName,
                           String city, String screenName, LocalDateTime showTime,
                           BigDecimal price) {
}
