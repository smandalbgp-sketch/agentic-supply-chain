package com.example.agentic.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    // Partitions cap how many app instances can consume in parallel. Replicas must be 1 on a
    // single-node dev broker and 3 on a typical production cluster (set app.topics.replicas).
    @Value("${app.topics.partitions:3}")
    private int partitions;

    @Value("${app.topics.replicas:1}")
    private int replicas;

    @Bean
    public NewTopic supplierDelayTopic(@Value("${app.topics.supplier-delay}") String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    /** Dead-letter topic must have at least as many partitions as the source topic. */
    @Bean
    public NewTopic supplierDelayDeadLetterTopic(@Value("${app.topics.supplier-delay}") String name) {
        return TopicBuilder.name(name + ".DLT").partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic decisionsTopic(@Value("${app.topics.decisions}") String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    /**
     * Retry a failing message 3 times (1s apart), then park it on "<topic>.DLT" so one bad message
     * cannot block the partition. Malformed JSON is never retried: it will not fix itself.
     * Spring Boot applies this bean to the @KafkaListener container automatically.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
