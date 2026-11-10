package com.example.agentic.model;

import java.time.Instant;

/** The event that enters Kafka: a supplier says a shipment for a SKU will be late. */
public record SupplierDelayEvent(
        String eventId,
        String supplierId,
        String sku,
        int delayDays,
        String reason,
        Instant occurredAt) {
}
