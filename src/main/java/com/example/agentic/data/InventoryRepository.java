package com.example.agentic.data;

import com.example.agentic.model.Alternative;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Demo data. Replace with a real inventory/ERP lookup (JPA, REST client, MCP tool...). */
public class InventoryRepository {

    public record Stock(int onHand, int dailyDemand, List<Alternative> alternatives) {
    }

    private final Map<String, Stock> stock = Map.of(
            "SKU-1001", new Stock(120, 40, List.of(
                    new Alternative("SUP-B", 3, 500),
                    new Alternative("SUP-C", 6, 300))),
            "SKU-2002", new Stock(900, 30, List.of()),
            "SKU-3003", new Stock(10, 20, List.of(
                    new Alternative("SUP-D", 10, 100))));

    public Optional<Stock> find(String sku) {
        return sku == null ? Optional.empty() : Optional.ofNullable(stock.get(sku));
    }
}
