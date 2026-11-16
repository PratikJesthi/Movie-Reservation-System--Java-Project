package com.moviebooking.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.moviebooking.dto.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void databaseConflictReturnsConflictStatusAndSafeMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "NULL not allowed for column CREATED_AT");
        ResponseEntity<ApiError> response = handler.databaseConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("The request conflicts with existing data.", response.getBody().message());
    }
}
