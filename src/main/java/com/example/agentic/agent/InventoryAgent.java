package com.example.agentic.agent;

import com.example.agentic.data.InventoryRepository;
import com.example.agentic.llm.Llm;
import com.example.agentic.model.Alternative;
import com.example.agentic.model.InventoryReport;
import com.example.agentic.model.SupplierDelayEvent;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Inventory Agent: "check stock & alternatives". */
public class InventoryAgent {

    private final InventoryRepository repository;
    private final Llm llm;

    public InventoryAgent(InventoryRepository repository, Llm llm) {
        this.repository = repository;
        this.llm = llm;
    }

    public InventoryReport check(SupplierDelayEvent event) {
        var found = repository.find(event.sku());
        if (found.isEmpty()) {
            return new InventoryReport(event.sku(), 0, 0, 0, List.of(),
                    "SKU " + event.sku() + " is not present in inventory records.");
        }
        var stock = found.get();

        int cover = stock.dailyDemand() <= 0 ? 999 : stock.onHand() / stock.dailyDemand();

        // Only alternatives that arrive sooner than the delayed shipment are useful.
        List<Alternative> usable = stock.alternatives().stream()
                .filter(a -> a.leadTimeDays() < event.delayDays())
                .sorted(Comparator.comparingInt(Alternative::leadTimeDays))
                .toList();

        String alternatives = usable.isEmpty()
                ? "No alternative supplier can beat the delay."
                : "Faster alternatives: " + usable.stream()
                        .map(a -> "%s (%d-day lead, %d units)".formatted(
                                a.supplierId(), a.leadTimeDays(), a.availableQty()))
                        .collect(Collectors.joining(", ")) + ".";

        String draft = "SKU %s: %d units on hand (about %d days of cover) against a %d-day delay from %s. %s"
                .formatted(event.sku(), stock.onHand(), cover, event.delayDays(),
                        event.supplierId(), alternatives);

        String summary = llm.refine(
                "Summarise this inventory check for an operations manager in two sentences.", draft);

        return new InventoryReport(event.sku(), stock.onHand(), stock.dailyDemand(), cover, usable, summary);
    }
}
