package org.example.dto;

import java.util.List;

public record CityListsResponse(List<String> sourceCities, List<String> destinationCities) {
}
