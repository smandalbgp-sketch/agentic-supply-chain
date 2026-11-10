package com.example.agentic.agent;

import com.example.agentic.llm.Llm;
import com.example.agentic.model.ActionType;
import com.example.agentic.model.Alternative;
import com.example.agentic.model.CustomerResolution;
import com.example.agentic.model.Decision;
import com.example.agentic.model.DecisionStatus;
import com.example.agentic.model.ImpactReport;
import com.example.agentic.model.InventoryReport;
import com.example.agentic.model.ProposedAction;
import com.example.agentic.model.Severity;
import com.example.agentic.model.SupplierDelayEvent;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Supervisor Agent: orchestrates the specialist agents.
 *
 *   Inventory ─┐ (parallel)
 *              ├─> Customer Service -> build actions -> approval gate
 *   Impact ────┘
 */
public class SupervisorAgent {

    private final InventoryAgent inventoryAgent;
    private final ImpactAnalysisAgent impactAgent;
    private final CustomerServiceAgent customerServiceAgent;
    private final Llm llm;
    private final Executor executor;
    private final Clock clock;

    public SupervisorAgent(InventoryAgent inventoryAgent, ImpactAnalysisAgent impactAgent,
                           CustomerServiceAgent customerServiceAgent, Llm llm,
                           Executor executor, Clock clock) {
        this.inventoryAgent = inventoryAgent;
        this.impactAgent = impactAgent;
        this.customerServiceAgent = customerServiceAgent;
        this.llm = llm;
        this.executor = executor;
        this.clock = clock;
    }

    public Decision handle(SupplierDelayEvent event) {
        // Independent specialists run in parallel.
        CompletableFuture<InventoryReport> inventoryFuture =
                CompletableFuture.supplyAsync(() -> inventoryAgent.check(event), executor);
        CompletableFuture<ImpactReport> impactFuture =
                CompletableFuture.supplyAsync(() -> impactAgent.evaluate(event), executor);
        InventoryReport inventory = inventoryFuture.join();
        ImpactReport impact = impactFuture.join();

        var now = clock.instant();
        String decisionId = UUID.randomUUID().toString();

        if (impact.impactedOrders().isEmpty()) {
            return new Decision(decisionId, event.eventId(), event.sku(), Severity.LOW,
                    DecisionStatus.NO_ACTION_NEEDED, List.of(), List.of(),
                    "No customer orders are at risk. " + inventory.summary(), now, now);
        }

        CustomerResolution resolution = customerServiceAgent.resolve(event, inventory, impact);
        List<ProposedAction> actions = buildActions(event, inventory, impact, resolution);

        boolean needsHuman = actions.stream().anyMatch(ProposedAction::requiresApproval);
        DecisionStatus status = needsHuman ? DecisionStatus.PENDING_APPROVAL : DecisionStatus.AUTO_EXECUTED;

        String draft = "Supplier %s delay of %d days on %s. %s %s Proposed: %s."
                .formatted(event.supplierId(), event.delayDays(), event.sku(),
                        inventory.summary(), impact.summary(),
                        actions.stream().map(ProposedAction::description).collect(Collectors.joining("; ")));
        String summary = llm.refine(
                "Write a three-sentence executive summary of this supply-chain incident and the proposed response.",
                draft);

        return new Decision(decisionId, event.eventId(), event.sku(), impact.severity(), status,
                actions, resolution.notifications(), summary, now, now);
    }

    private List<ProposedAction> buildActions(SupplierDelayEvent event, InventoryReport inventory,
                                              ImpactReport impact, CustomerResolution resolution) {
        List<ProposedAction> actions = new ArrayList<>();

        // Low-risk: telling customers the truth never needs approval.
        actions.add(new ProposedAction(ActionType.NOTIFY_CUSTOMERS,
                "Send delay notifications to %d customer(s)".formatted(resolution.notifications().size()),
                BigDecimal.ZERO, false));

        // Rerouting commits money with another supplier: gate it once impact is high.
        if (!inventory.usableAlternatives().isEmpty()) {
            Alternative best = inventory.usableAlternatives().get(0);
            actions.add(new ProposedAction(ActionType.REROUTE_TO_ALTERNATE_SUPPLIER,
                    "Reroute %s via %s (%d-day lead time)".formatted(
                            event.sku(), best.supplierId(), best.leadTimeDays()),
                    BigDecimal.ZERO, impact.severity().isAtLeast(Severity.HIGH)));
        }

        // Money going back to customers: always a human decision.
        if (resolution.proposedCredit().signum() > 0) {
            actions.add(new ProposedAction(ActionType.ISSUE_CREDIT,
                    "Issue goodwill credits totalling " + resolution.proposedCredit().toPlainString(),
                    resolution.proposedCredit(), true));
        }
        return List.copyOf(actions);
    }
}
