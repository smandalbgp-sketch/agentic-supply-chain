package com.example.agentic.model;

import java.math.BigDecimal;

public record ProposedAction(
        ActionType type,
        String description,
        BigDecimal amount,
        boolean requiresApproval) {
}
