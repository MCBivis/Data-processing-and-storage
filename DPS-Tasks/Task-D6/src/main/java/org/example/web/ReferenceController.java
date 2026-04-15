package org.example.web;

import org.example.dto.AirportListsResponse;
import org.example.dto.AirportSummary;
import org.example.dto.CityListsResponse;
import org.example.service.ReferenceDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReferenceController {

    private final ReferenceDataService referenceDataService;

    public ReferenceController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/cities")
    public CityListsResponse cities() {
        return referenceDataService.listCities();
    }

    @GetMapping("/airports")
    public AirportListsResponse airports() {
        return referenceDataService.listAirports();
    }

    @GetMapping("/cities/{city}/airports")
    public List<AirportSummary> airportsInCity(@PathVariable("city") String city) {
        return referenceDataService.airportsInCity(city);
    }
}
