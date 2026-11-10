package com.example.agentic.llm;

/** Default: no model call, returns the deterministic draft unchanged. Runs with no API key. */
public class RuleBasedLlm implements Llm {

    @Override
    public String refine(String instruction, String draft) {
        return draft;
    }
}
