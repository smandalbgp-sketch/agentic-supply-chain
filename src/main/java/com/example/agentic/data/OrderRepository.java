package com.example.agentic.data;

import com.example.agentic.model.Order;

import java.math.BigDecimal;
import java.util.List;

/** Demo data. Replace with your order system. */
public class OrderRepository {

    private final List<Order> orders = List.of(
            new Order("ORD-1", "Acme Corp", "SKU-1001", 100, new BigDecimal("25000.00"), 2),
            new Order("ORD-2", "Globex", "SKU-1001", 60, new BigDecimal("15000.00"), 4),
            new Order("ORD-3", "Initech", "SKU-1001", 40, new BigDecimal("9000.00"), 10),
            new Order("ORD-4", "Umbrella", "SKU-2002", 50, new BigDecimal("4000.00"), 5),
            new Order("ORD-5", "Hooli", "SKU-3003", 30, new BigDecimal("62000.00"), 3),
            new Order("ORD-6", "Stark Industries", "SKU-3003", 20, new BigDecimal("41000.00"), 7));

    public List<Order> openOrdersFor(String sku) {
        return orders.stream().filter(o -> o.sku().equals(sku)).toList();
    }
}
