package com.example.agentic.model;

public enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL;

    public boolean isAtLeast(Severity other) {
        return compareTo(other) >= 0;
    }
}
