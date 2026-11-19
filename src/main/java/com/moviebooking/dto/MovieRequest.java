package com.moviebooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MovieRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotBlank @Size(max = 100) String genre,
        @NotNull @Positive Integer durationMinutes,
        @NotBlank @Size(max = 80) String language,
        String posterUrl,
        LocalDate releaseDate,
        @Pattern(regexp = "U|UA|A", message = "Certificate must be U, UA or A.") String certificate) {
}
