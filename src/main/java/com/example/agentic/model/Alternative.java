package com.example.agentic.model;

/** Another supplier that could ship the same SKU. */
public record Alternative(String supplierId, int leadTimeDays, int availableQty) {
}
