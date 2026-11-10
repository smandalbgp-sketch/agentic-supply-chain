package com.example.agentic.model;

import java.math.BigDecimal;

/** An open customer order. promisedInDays = days from now until the delivery we promised. */
public record Order(
        String orderId,
        String customerName,
        String sku,
        int quantity,
        BigDecimal value,
        int promisedInDays) {
}
