package org.example.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RouteLeg(
        int flightId,
        String routeNo,
        String departureAirport,
        String arrivalAirport,
        OffsetDateTime scheduledDeparture,
        OffsetDateTime scheduledArrival,
        BigDecimal price
) {
}
