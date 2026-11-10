package com.example.agentic.persistence;

import com.example.agentic.model.Decision;
import com.example.agentic.model.DecisionStatus;
import com.example.agentic.model.Notification;
import com.example.agentic.model.ProposedAction;
import com.example.agentic.service.DecisionStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** MySQL-backed store (also works on the H2 profile). */
@Component
public class JpaDecisionStore implements DecisionStore {

    private static final TypeReference<List<ProposedAction>> ACTIONS = new TypeReference<>() { };
    private static final TypeReference<List<Notification>> NOTIFICATIONS = new TypeReference<>() { };

    private final DecisionRepository repository;
    private final ObjectMapper mapper;

    public JpaDecisionStore(DecisionRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return repository.existsByEventId(eventId);
    }

    /** Deliberately not @Transactional: a unique-key failure must not poison a surrounding transaction. */
    @Override
    public boolean saveIfNew(Decision decision) {
        try {
            repository.saveAndFlush(toEntity(decision));
            return true;
        } catch (DataIntegrityViolationException e) {
            // Duplicate event -> false. Any other integrity problem (e.g. value too long) is a real error.
            if (repository.existsByEventId(decision.eventId())) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public Optional<Decision> find(String decisionId) {
        return repository.findById(decisionId).map(this::toDecision);
    }

    @Override
    public List<Decision> all() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toDecision).toList();
    }

    @Override
    public List<Decision> byStatus(DecisionStatus status) {
        return repository.findByStatusOrderByCreatedAtDesc(status).stream().map(this::toDecision).toList();
    }

    @Override
    @Transactional
    public Optional<Decision> transitionIfPending(String decisionId, DecisionStatus target, Instant now) {
        int changed = repository.transition(decisionId, DecisionStatus.PENDING_APPROVAL, target, now);
        return changed == 0 ? Optional.empty() : repository.findById(decisionId).map(this::toDecision);
    }

    // ---- mapping ----

    private DecisionEntity toEntity(Decision d) {
        return new DecisionEntity(d.decisionId(), d.eventId(), d.sku(), d.severity(), d.status(),
                write(d.actions()), write(d.notifications()), d.summary(), d.createdAt(), d.updatedAt());
    }

    private Decision toDecision(DecisionEntity e) {
        return new Decision(e.getDecisionId(), e.getEventId(), e.getSku(), e.getSeverity(), e.getStatus(),
                read(e.getActionsJson(), ACTIONS), read(e.getNotificationsJson(), NOTIFICATIONS),
                e.getSummary(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise " + value.getClass().getSimpleName(), ex);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Corrupt JSON in decisions table", ex);
        }
    }
}
