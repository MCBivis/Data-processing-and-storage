package org.example.service;

import org.example.dto.AirportListsResponse;
import org.example.dto.AirportSummary;
import org.example.dto.CityListsResponse;
import org.example.repository.ReferenceDataRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReferenceDataService {

    private final ReferenceDataRepository referenceDataRepository;

    public ReferenceDataService(ReferenceDataRepository referenceDataRepository) {
        this.referenceDataRepository = referenceDataRepository;
    }

    public CityListsResponse listCities() {
        List<String> sources = referenceDataRepository.listDistinctSourceCitiesEn();
        List<String> destinations = referenceDataRepository.listDistinctDestinationCitiesEn();
        return new CityListsResponse(sources, destinations);
    }

    public AirportListsResponse listAirports() {
        List<String> sources = referenceDataRepository.listDistinctSourceAirportNamesEn();
        List<String> destinations = referenceDataRepository.listDistinctDestinationAirportNamesEn();
        return new AirportListsResponse(sources, destinations);
    }

    public List<AirportSummary> airportsInCity(String cityName) {
        return referenceDataRepository.findAirportsInCity(cityName.trim());
    }
}
