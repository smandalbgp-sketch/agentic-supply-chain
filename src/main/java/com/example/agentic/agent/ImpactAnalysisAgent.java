package com.example.agentic.agent;

import com.example.agentic.data.InventoryRepository;
import com.example.agentic.data.OrderRepository;
import com.example.agentic.llm.Llm;
import com.example.agentic.model.ImpactReport;
import com.example.agentic.model.Order;
import com.example.agentic.model.Severity;
import com.example.agentic.model.SupplierDelayEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Impact Analysis Agent: "evaluate affected orders".
 *
 * Rule: serve orders from on-hand stock, earliest promise first. An order that stock cannot cover
 * is impacted only if its promised date falls BEFORE the delayed shipment arrives.
 */
public class ImpactAnalysisAgent {

    static final BigDecimal MEDIUM_FROM = new BigDecimal("1");
    static final BigDecimal HIGH_FROM = new BigDecimal("20000");
    static final BigDecimal CRITICAL_FROM = new BigDecimal("60000");

    private final InventoryRepository inventory;
    private final OrderRepository orders;
    private final Llm llm;

    public ImpactAnalysisAgent(InventoryRepository inventory, OrderRepository orders, Llm llm) {
        this.inventory = inventory;
        this.orders = orders;
        this.llm = llm;
    }

    public ImpactReport evaluate(SupplierDelayEvent event) {
        int remainingStock = inventory.find(event.sku())
                .map(InventoryRepository.Stock::onHand).orElse(0);

        List<Order> byPromise = orders.openOrdersFor(event.sku()).stream()
                .sorted(Comparator.comparingInt(Order::promisedInDays))
                .toList();

        List<Order> impacted = new ArrayList<>();
        for (Order order : byPromise) {
            if (remainingStock >= order.quantity()) {
                remainingStock -= order.quantity();          // fully covered from stock
            } else if (order.promisedInDays() < event.delayDays()) {
                impacted.add(order);                          // will be late
            }
        }

        BigDecimal atRisk = impacted.stream().map(Order::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Severity severity = severityFor(atRisk);

        String draft = impacted.isEmpty()
                ? "No open orders for %s are at risk from a %d-day delay."
                        .formatted(event.sku(), event.delayDays())
                : "%d order(s) for %s will miss their promised date, with %s in order value at risk (severity %s)."
                        .formatted(impacted.size(), event.sku(), atRisk.toPlainString(), severity);

        String summary = llm.refine(
                "Summarise this order-impact analysis in two sentences for an operations manager.", draft);

        return new ImpactReport(List.copyOf(impacted), atRisk, severity, summary);
    }

    public static Severity severityFor(BigDecimal atRisk) {
        if (atRisk.compareTo(MEDIUM_FROM) < 0) return Severity.LOW;
        if (atRisk.compareTo(HIGH_FROM) < 0) return Severity.MEDIUM;
        if (atRisk.compareTo(CRITICAL_FROM) < 0) return Severity.HIGH;
        return Severity.CRITICAL;
    }
}
