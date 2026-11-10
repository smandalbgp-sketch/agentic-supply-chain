package com.example.agentic.llm;

/**
 * The only thing agents ask of a language model: polish wording.
 *
 * Facts (stock levels, affected orders, money) are ALWAYS computed by deterministic code and passed
 * in as {@code draft}. The model may rephrase but must not add facts. That keeps decisions auditable
 * and means an LLM outage degrades to plain text instead of stopping the pipeline.
 */
public interface Llm {

    String refine(String instruction, String draft);
}
