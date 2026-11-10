package com.example.agentic.model;

public enum DecisionStatus {
    /** No customer order is at risk; nothing to do. */
    NO_ACTION_NEEDED,
    /** Low-impact response, executed without a human. */
    AUTO_EXECUTED,
    /** Contains a high-impact action (refund/credit, big reroute): waiting for a human. */
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
