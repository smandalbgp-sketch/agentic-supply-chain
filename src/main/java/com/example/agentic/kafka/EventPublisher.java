package com.example.agentic.kafka;

import com.example.agentic.model.Decision;
import com.example.agentic.model.SupplierDelayEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** All writes to Kafka go through here. Records are keyed so related messages share a partition (ordering). */
@Component
public class EventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String supplierDelayTopic;
    private final String decisionsTopic;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                          @Value("${app.topics.supplier-delay}") String supplierDelayTopic,
                          @Value("${app.topics.decisions}") String decisionsTopic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.supplierDelayTopic = supplierDelayTopic;
        this.decisionsTopic = decisionsTopic;
    }

    /** Keyed by SKU: all delays for one SKU land on one partition and are processed in order. */
    public void publishEvent(SupplierDelayEvent event) {
        kafka.send(supplierDelayTopic, event.sku(), toJson(event));
    }

    /** Keyed by eventId. Downstream systems (ERP, email sender) consume this topic to execute actions. */
    public void publishDecision(Decision decision) {
        kafka.send(decisionsTopic, decision.eventId(), toJson(decision));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value.getClass().getSimpleName(), e);
        }
    }
}
