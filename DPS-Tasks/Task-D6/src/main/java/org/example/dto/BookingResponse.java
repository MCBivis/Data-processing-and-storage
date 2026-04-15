package org.example.dto;

import java.math.BigDecimal;

public record BookingResponse(
        String bookRef,
        String ticketNo,
        BigDecimal totalAmount
) {
}
