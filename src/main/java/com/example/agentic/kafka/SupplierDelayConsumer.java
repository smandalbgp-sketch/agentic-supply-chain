package com.example.agentic.kafka;

import com.example.agentic.model.SupplierDelayEvent;
import com.example.agentic.service.DecisionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Entry point of the flow: Kafka -> Supervisor Agent -> decision back onto Kafka. */
@Component
public class SupplierDelayConsumer {

    private static final Logger log = LoggerFactory.getLogger(SupplierDelayConsumer.class);

    private final DecisionService decisions;
    private final EventPublisher publisher;
    private final ObjectMapper mapper;

    public SupplierDelayConsumer(DecisionService decisions, EventPublisher publisher, ObjectMapper mapper) {
        this.decisions = decisions;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.topics.supplier-delay}", groupId = "${spring.kafka.consumer.group-id}")
    public void onSupplierDelay(String payload) throws JsonProcessingException {
        SupplierDelayEvent event = mapper.readValue(payload, SupplierDelayEvent.class);
        log.info("Received delay event {} sku={} delay={}d", event.eventId(), event.sku(), event.delayDays());

        decisions.process(event).ifPresentOrElse(
                decision -> {
                    log.info("Decision {} for event {}: {} ({}), {} action(s)",
                            decision.decisionId(), event.eventId(), decision.status(),
                            decision.severity(), decision.actions().size());
                    publisher.publishDecision(decision);
                },
                () -> log.info("Event {} already processed, skipping", event.eventId()));
    }
}
