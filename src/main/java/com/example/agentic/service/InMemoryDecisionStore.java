package com.example.agentic.service;

import com.example.agentic.model.Decision;
import com.example.agentic.model.DecisionStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/** Non-persistent store: used by unit tests and handy for experiments. Not a Spring bean. */
public class InMemoryDecisionStore implements DecisionStore {

    private final ConcurrentMap<String, Decision> decisions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> decisionIdByEventId = new ConcurrentHashMap<>();

    @Override
    public boolean existsByEventId(String eventId) {
        return decisionIdByEventId.containsKey(eventId);
    }

    @Override
    public boolean saveIfNew(Decision decision) {
        if (decisionIdByEventId.putIfAbsent(decision.eventId(), decision.decisionId()) != null) {
            return false;
        }
        decisions.put(decision.decisionId(), decision);
        return true;
    }

    @Override
    public Optional<Decision> find(String decisionId) {
        return Optional.ofNullable(decisions.get(decisionId));
    }

    @Override
    public List<Decision> all() {
        return decisions.values().stream()
                .sorted(Comparator.comparing(Decision::createdAt).reversed())
                .toList();
    }

    @Override
    public List<Decision> byStatus(DecisionStatus status) {
        return all().stream().filter(d -> d.status() == status).toList();
    }

    @Override
    public Optional<Decision> transitionIfPending(String decisionId, DecisionStatus target, Instant now) {
        AtomicReference<Decision> updated = new AtomicReference<>();
        decisions.computeIfPresent(decisionId, (id, current) -> {
            if (current.status() != DecisionStatus.PENDING_APPROVAL) {
                return current;
            }
            Decision next = current.withStatus(target, now);
            updated.set(next);
            return next;
        });
        return Optional.ofNullable(updated.get());
    }
}
