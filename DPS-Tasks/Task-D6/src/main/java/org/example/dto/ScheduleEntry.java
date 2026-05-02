package org.example.dto;

import java.util.List;

public record ScheduleEntry(
        String flightNo,
        List<Integer> daysOfWeek,
        String time,
        String airportCode,
        String airportNameEn
) {
}
