package org.example.dto;

import java.time.OffsetDateTime;

public record CheckInResponse(int boardingNo, OffsetDateTime boardingTime) {
}
