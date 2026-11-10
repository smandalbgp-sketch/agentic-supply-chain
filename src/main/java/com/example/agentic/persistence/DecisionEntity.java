package com.example.agentic.persistence;

import com.example.agentic.model.DecisionStatus;
import com.example.agentic.model.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One row per decision. The unique constraint on event_id is the database-level guarantee that an
 * event is processed once, even if two consumers race. Actions and notifications are stored as JSON
 * text: they are only ever read together with the decision, never queried on their own.
 */
@Entity
@Table(name = "decisions",
        uniqueConstraints = @UniqueConstraint(name = "uk_decisions_event_id", columnNames = "event_id"),
        indexes = @Index(name = "idx_decisions_status", columnList = "status"))
public class DecisionEntity {

    @Id
    @Column(name = "decision_id", length = 64)
    private String decisionId;

    @Column(name = "event_id", nullable = false, length = 200)
    private String eventId;

    @Column(name = "sku", length = 100)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DecisionStatus status;

    @Column(name = "actions_json", nullable = false, columnDefinition = "text")
    private String actionsJson;

    @Column(name = "notifications_json", nullable = false, columnDefinition = "text")
    private String notificationsJson;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DecisionEntity() {
        // required by JPA
    }

    public DecisionEntity(String decisionId, String eventId, String sku, Severity severity,
                          DecisionStatus status, String actionsJson, String notificationsJson,
                          String summary, Instant createdAt, Instant updatedAt) {
        this.decisionId = decisionId;
        this.eventId = eventId;
        this.sku = sku;
        this.severity = severity;
        this.status = status;
        this.actionsJson = actionsJson;
        this.notificationsJson = notificationsJson;
        this.summary = summary;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getDecisionId() { return decisionId; }
    public String getEventId() { return eventId; }
    public String getSku() { return sku; }
    public Severity getSeverity() { return severity; }
    public DecisionStatus getStatus() { return status; }
    public String getActionsJson() { return actionsJson; }
    public String getNotificationsJson() { return notificationsJson; }
    public String getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
