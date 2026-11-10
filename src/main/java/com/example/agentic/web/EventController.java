package com.example.agentic.web;

import com.example.agentic.kafka.EventPublisher;
import com.example.agentic.model.SupplierDelayEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Stand-in for the supplier system: publishes a "Supplier Delay Event" to Kafka. */
@RestController
@RequestMapping("/api/events")
public class EventController {

    public record SupplierDelayRequest(String supplierId, String sku, int delayDays, String reason) {
    }

    private final EventPublisher publisher;

    public EventController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/supplier-delay")
    public ResponseEntity<Map<String, Object>> supplierDelay(@RequestBody SupplierDelayRequest request) {
        if (isBlank(request.supplierId()) || isBlank(request.sku()) || request.delayDays() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "supplierId and sku are required, and delayDays must be > 0");
        }
        var event = new SupplierDelayEvent(UUID.randomUUID().toString(), request.supplierId(),
                request.sku(), request.delayDays(), request.reason(), Instant.now());
        publisher.publishEvent(event);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "eventId", event.eventId(),
                "message", "Published to Kafka. Check GET /api/decisions in a moment."));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
