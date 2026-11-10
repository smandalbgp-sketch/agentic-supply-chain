package com.example.agentic.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/** Spring AI implementation. If the model call fails, falls back to the deterministic draft. */
public class SpringAiLlm implements Llm {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlm.class);

    private final ChatClient chat;

    public SpringAiLlm(ChatClient.Builder builder) {
        this.chat = builder
                .defaultSystem("You assist an enterprise supply-chain operations system. "
                        + "Use ONLY the facts provided. Never invent numbers, dates, names or commitments.")
                .build();
    }

    @Override
    public String refine(String instruction, String draft) {
        try {
            String out = chat.prompt()
                    .user(instruction + "\n\nFACTS:\n" + draft)
                    .call()
                    .content();
            return (out == null || out.isBlank()) ? draft : out.trim();
        } catch (RuntimeException e) {
            log.warn("LLM call failed, using deterministic draft: {}", e.toString());
            return draft;
        }
    }
}
