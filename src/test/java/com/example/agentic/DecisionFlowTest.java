package com.example.agentic;

import com.example.agentic.agent.CustomerServiceAgent;
import com.example.agentic.agent.ImpactAnalysisAgent;
import com.example.agentic.agent.InventoryAgent;
import com.example.agentic.agent.SupervisorAgent;
import com.example.agentic.data.InventoryRepository;
import com.example.agentic.data.OrderRepository;
import com.example.agentic.llm.RuleBasedLlm;
import com.example.agentic.model.Decision;
import com.example.agentic.model.DecisionStatus;
import com.example.agentic.model.Severity;
import com.example.agentic.model.SupplierDelayEvent;
import com.example.agentic.service.DecisionService;
import com.example.agentic.service.InMemoryDecisionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure-Java tests of the agent flow: no Kafka, no Spring context, no LLM. */
class DecisionFlowTest {

    private DecisionService service;

    @BeforeEach
    void setUp() {
        var llm = new RuleBasedLlm();
        var inventory = new InventoryRepository();
        var orders = new OrderRepository();
        var supervisor = new SupervisorAgent(
                new InventoryAgent(inventory, llm),
                new ImpactAnalysisAgent(inventory, orders, llm),
                new CustomerServiceAgent(llm),
                llm, Runnable::run, Clock.systemUTC());
        service = new DecisionService(supervisor, new InMemoryDecisionStore(), Clock.systemUTC());
    }

    private static SupplierDelayEvent event(String id, String sku, int delayDays) {
        return new SupplierDelayEvent(id, "SUP-A", sku, delayDays, "port congestion", Instant.now());
    }

    @Test
    void mediumImpactIsAutoExecuted() {
        Decision d = service.process(event("e1", "SKU-1001", 5)).orElseThrow();

        assertThat(d.status()).isEqualTo(DecisionStatus.AUTO_EXECUTED);
        assertThat(d.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(d.notifications()).hasSize(1);
        assertThat(d.notifications().get(0).orderId()).isEqualTo("ORD-2");
        assertThat(d.actions()).hasSize(2);
        assertThat(d.actions().get(1).requiresApproval()).isFalse();
    }

    @Test
    void criticalImpactWaitsForHumanApproval() {
        Decision d = service.process(event("e2", "SKU-3003", 12)).orElseThrow();

        assertThat(d.status()).isEqualTo(DecisionStatus.PENDING_APPROVAL);
        assertThat(d.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(d.notifications()).hasSize(2);
        assertThat(d.actions()).hasSize(3);
        assertThat(d.actions().get(1).requiresApproval()).isTrue();   // reroute gated at HIGH+
        assertThat(d.actions().get(2).amount()).isEqualByComparingTo(new BigDecimal("5150.00"));
        assertThat(service.pendingApproval()).containsExactly(d);
    }

    @Test
    void stockCoveringAllOrdersNeedsNoAction() {
        Decision d = service.process(event("e3", "SKU-2002", 2)).orElseThrow();
        assertThat(d.status()).isEqualTo(DecisionStatus.NO_ACTION_NEEDED);
    }

    @Test
    void unknownOrMissingSkuDoesNotCrash() {
        assertThat(service.process(event("e4", "SKU-NOPE", 5)).orElseThrow().status())
                .isEqualTo(DecisionStatus.NO_ACTION_NEEDED);
        assertThat(service.process(event("e5", null, 5)).orElseThrow().status())
                .isEqualTo(DecisionStatus.NO_ACTION_NEEDED);
    }

    @Test
    void duplicateEventIsSkipped() {
        service.process(event("dup", "SKU-3003", 12));
        assertThat(service.process(event("dup", "SKU-3003", 12))).isEmpty();
        assertThat(service.all()).hasSize(1);
    }

    @Test
    void approveThenSecondApproveIsRejected() {
        Decision d = service.process(event("e6", "SKU-3003", 12)).orElseThrow();

        assertThat(service.approve(d.decisionId()).status()).isEqualTo(DecisionStatus.APPROVED);
        assertThat(service.pendingApproval()).isEmpty();
        assertThatThrownBy(() -> service.approve(d.decisionId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.reject(d.decisionId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectMovesToRejected() {
        Decision d = service.process(event("e7", "SKU-3003", 12)).orElseThrow();
        assertThat(service.reject(d.decisionId()).status()).isEqualTo(DecisionStatus.REJECTED);
    }

    @Test
    void unknownDecisionIdIsNotFound() {
        assertThatThrownBy(() -> service.approve("nope")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void severityBoundaries() {
        assertThat(ImpactAnalysisAgent.severityFor(BigDecimal.ZERO)).isEqualTo(Severity.LOW);
        assertThat(ImpactAnalysisAgent.severityFor(new BigDecimal("20000"))).isEqualTo(Severity.HIGH);
        assertThat(ImpactAnalysisAgent.severityFor(new BigDecimal("60000"))).isEqualTo(Severity.CRITICAL);
    }
}
