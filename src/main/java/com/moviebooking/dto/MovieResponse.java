package com.moviebooking.dto;

public record MovieResponse(Long id, String title, String description, String genre,
                            Integer durationMinutes, String language, String posterUrl) {
}
