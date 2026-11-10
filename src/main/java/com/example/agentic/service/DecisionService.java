package com.example.agentic.service;

import com.example.agentic.agent.SupervisorAgent;
import com.example.agentic.model.Decision;
import com.example.agentic.model.DecisionStatus;
import com.example.agentic.model.SupplierDelayEvent;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Runs the supervisor, stores decisions, and owns the human-in-the-loop transitions. */
public class DecisionService {

    private final SupervisorAgent supervisor;
    private final DecisionStore store;
    private final Clock clock;

    public DecisionService(SupervisorAgent supervisor, DecisionStore store, Clock clock) {
        this.supervisor = supervisor;
        this.store = store;
        this.clock = clock;
    }

    /**
     * Kafka delivers at-least-once, so the same event can arrive twice.
     * Returns empty for a duplicate so the caller can skip it.
     */
    public Optional<Decision> process(SupplierDelayEvent event) {
        if (store.existsByEventId(event.eventId())) {
            return Optional.empty();
        }
        Decision decision = supervisor.handle(event);
        // saveIfNew also guards against two consumers racing on the same event.
        return store.saveIfNew(decision) ? Optional.of(decision) : Optional.empty();
    }

    public Decision approve(String decisionId) {
        return transition(decisionId, DecisionStatus.APPROVED);
    }

    public Decision reject(String decisionId) {
        return transition(decisionId, DecisionStatus.REJECTED);
    }

    public Optional<Decision> find(String decisionId) {
        return store.find(decisionId);
    }

    public List<Decision> all() {
        return store.all();
    }

    public List<Decision> pendingApproval() {
        return store.byStatus(DecisionStatus.PENDING_APPROVAL);
    }

    private Decision transition(String decisionId, DecisionStatus target) {
        return store.transitionIfPending(decisionId, target, clock.instant())
                .orElseGet(() -> {
                    // Not updated: either it does not exist (404) or it is not pending (409).
                    Decision current = store.find(decisionId)
                            .orElseThrow(() -> new NoSuchElementException("No decision " + decisionId));
                    throw new IllegalStateException(
                            "Decision " + decisionId + " is " + current.status() + ", not PENDING_APPROVAL");
                });
    }
}
