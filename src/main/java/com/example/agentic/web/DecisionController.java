package com.example.agentic.web;

import com.example.agentic.kafka.EventPublisher;
import com.example.agentic.model.Decision;
import com.example.agentic.service.DecisionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Decisions plus the human-in-the-loop approval endpoints. */
@RestController
@RequestMapping("/api")
public class DecisionController {

    private final DecisionService decisions;
    private final EventPublisher publisher;

    public DecisionController(DecisionService decisions, EventPublisher publisher) {
        this.decisions = decisions;
        this.publisher = publisher;
    }

    @GetMapping("/decisions")
    public List<Decision> all() {
        return decisions.all();
    }

    @GetMapping("/decisions/{id}")
    public ResponseEntity<Decision> one(@PathVariable String id) {
        return decisions.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The approval queue: decisions waiting for a person. */
    @GetMapping("/approvals")
    public List<Decision> pending() {
        return decisions.pendingApproval();
    }

    @PostMapping("/approvals/{id}/approve")
    public Decision approve(@PathVariable String id) {
        Decision decision = decisions.approve(id);
        publisher.publishDecision(decision);   // downstream systems now execute the approved actions
        return decision;
    }

    @PostMapping("/approvals/{id}/reject")
    public Decision reject(@PathVariable String id) {
        Decision decision = decisions.reject(id);
        publisher.publishDecision(decision);
        return decision;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}
