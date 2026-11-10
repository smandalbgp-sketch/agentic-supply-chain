package com.example.agentic.model;

import java.util.List;

public record InventoryReport(
        String sku,
        int onHand,
        int dailyDemand,
        int daysOfCover,
        List<Alternative> usableAlternatives,
        String summary) {
}
