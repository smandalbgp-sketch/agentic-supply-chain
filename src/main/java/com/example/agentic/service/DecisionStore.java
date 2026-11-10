package com.example.agentic.service;

import com.example.agentic.model.Decision;
import com.example.agentic.model.DecisionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where decisions live. Two implementations: InMemoryDecisionStore (tests, quick demos) and
 * JpaDecisionStore (MySQL). The agents and DecisionService only know this interface.
 */
public interface DecisionStore {

    boolean existsByEventId(String eventId);

    /** Saves the decision unless one already exists for the same eventId. Returns false for a duplicate. */
    boolean saveIfNew(Decision decision);

    Optional<Decision> find(String decisionId);

    /** Newest first. */
    List<Decision> all();

    /** Newest first. */
    List<Decision> byStatus(DecisionStatus status);

    /**
     * Atomically moves a decision from PENDING_APPROVAL to {@code target}.
     * Returns empty if the decision does not exist OR is no longer pending, so two people
     * approving at the same moment cannot both succeed.
     */
    Optional<Decision> transitionIfPending(String decisionId, DecisionStatus target, Instant now);
}
