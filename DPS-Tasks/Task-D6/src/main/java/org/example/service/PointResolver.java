package org.example.service;

import org.example.repository.AirportRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A point is either a 3-letter airport code or a city name (matched against {@code city->>'en'}).
 */
@Component
public class PointResolver {

    private final AirportRepository airportRepository;

    public PointResolver(AirportRepository airportRepository) {
        this.airportRepository = airportRepository;
    }

    public Set<String> resolveAirportCodes(String point) {
        if (point == null || point.isBlank()) {
            throw new PointResolutionException("Point must not be blank");
        }
        String trimmed = point.trim();
        if (trimmed.length() == 3 && trimmed.chars().allMatch(Character::isLetter)) {
            String code = trimmed.toUpperCase();
            if (airportRepository.countByAirportCode(code) > 0) {
                return Set.of(code);
            }
        }
        List<String> byCity = airportRepository.findAirportCodesByCityNameEn(trimmed);
        if (!byCity.isEmpty()) {
            return new LinkedHashSet<>(byCity);
        }

        List<String> byAirport = airportRepository.findAirportCodesByAirportNameEn(trimmed);
        if (!byAirport.isEmpty()) {
            return new LinkedHashSet<>(byAirport);
        }

        throw new PointResolutionException("Unknown airport or city: " + trimmed);
    }
}
