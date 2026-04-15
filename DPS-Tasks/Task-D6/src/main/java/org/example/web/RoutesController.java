package org.example.web;

import org.example.dto.RouteOption;
import org.example.service.RouteSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RoutesController {

    private final RouteSearchService routeSearchService;

    public RoutesController(RouteSearchService routeSearchService) {
        this.routeSearchService = routeSearchService;
    }

    /**
     * @param maxConnections 0–3 for a bounded number of intermediate stops, or "unbound"
     */
    @GetMapping
    public List<RouteOption> search(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam LocalDate departureDate,
            @RequestParam String bookingClass,
            @RequestParam(defaultValue = "unbound") String maxConnections
    ) {
        Integer mc = parseMaxConnections(maxConnections);
        return routeSearchService.findRoutes(from, to, departureDate, bookingClass, mc);
    }

    private static Integer parseMaxConnections(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("unbound")) {
            return null;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < 0 || v > 3) {
                throw new IllegalArgumentException("maxConnections must be 0–3 or unbound");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("maxConnections must be 0–3 or unbound");
        }
    }
}
