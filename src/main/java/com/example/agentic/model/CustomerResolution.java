package com.example.agentic.model;

import java.math.BigDecimal;
import java.util.List;

public record CustomerResolution(List<Notification> notifications, BigDecimal proposedCredit) {
}
