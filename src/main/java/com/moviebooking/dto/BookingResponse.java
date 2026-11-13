package com.moviebooking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(Long id, Long showId, String movieTitle, String theatreName,
                              String screenName, LocalDateTime showTime, List<String> seats,
                              BigDecimal totalAmount, String status, LocalDateTime createdAt) {
}
