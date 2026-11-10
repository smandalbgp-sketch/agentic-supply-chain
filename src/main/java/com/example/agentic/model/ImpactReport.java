package com.example.agentic.model;

import java.math.BigDecimal;
import java.util.List;

public record ImpactReport(
        List<Order> impactedOrders,
        BigDecimal valueAtRisk,
        Severity severity,
        String summary) {
}
