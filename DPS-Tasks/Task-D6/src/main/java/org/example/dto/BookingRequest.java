package org.example.dto;

import java.util.List;

public record BookingRequest(
        List<Integer> flightIds,
        String bookingClass,
        String passengerId,
        String passengerName
) {
}
