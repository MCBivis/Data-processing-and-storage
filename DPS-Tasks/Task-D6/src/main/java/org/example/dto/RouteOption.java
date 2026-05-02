package org.example.dto;

import java.math.BigDecimal;
import java.util.List;

public record RouteOption(List<RouteLeg> legs, BigDecimal totalPrice) {
}
