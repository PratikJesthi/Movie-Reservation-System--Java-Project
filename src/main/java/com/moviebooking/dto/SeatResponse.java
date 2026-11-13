package com.moviebooking.dto;

public record SeatResponse(Long id, String rowLabel, Integer seatNumber, String status) {
}
