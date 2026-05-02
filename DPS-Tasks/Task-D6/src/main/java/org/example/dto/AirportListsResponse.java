package org.example.dto;

import java.util.List;

public record AirportListsResponse(List<String> sourceAirports, List<String> destinationAirports) {
}
