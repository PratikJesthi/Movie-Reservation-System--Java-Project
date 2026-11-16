package com.moviebooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TicketQrServiceTest {

    private TicketQrService ticketQrService;

    @BeforeEach
    void setUp() {
        ticketQrService = new TicketQrService();
        ReflectionTestUtils.setField(ticketQrService, "baseUrl", "http://localhost:8080");
    }

    @Test
    void verifyUrlBuildsCorrectUrl() {
        String token = "abc-123-uuid";
        assertEquals("http://localhost:8080/verify/abc-123-uuid", ticketQrService.verifyUrl(token));
    }

    @Test
    void qrPngGeneratesValidPngBytes() {
        byte[] bytes = ticketQrService.qrPng("test-token");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        // Check PNG magic bytes: 0x89 0x50 0x4E 0x47 (PNG)
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        assertEquals((byte) 'G', bytes[3]);
    }
}
