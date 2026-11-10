package com.example.agentic.model;

import java.time.Instant;
import java.util.List;

/** The supervisor's output for one SupplierDelayEvent. Immutable; status changes make a copy. */
public record Decision(
        String decisionId,
        String eventId,
        String sku,
        Severity severity,
        DecisionStatus status,
        List<ProposedAction> actions,
        List<Notification> notifications,
        String summary,
        Instant createdAt,
        Instant updatedAt) {

    public Decision withStatus(DecisionStatus newStatus, Instant now) {
        return new Decision(decisionId, eventId, sku, severity, newStatus,
                actions, notifications, summary, createdAt, now);
    }
}
