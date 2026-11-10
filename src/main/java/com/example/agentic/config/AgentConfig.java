package com.example.agentic.config;

import com.example.agentic.agent.CustomerServiceAgent;
import com.example.agentic.agent.ImpactAnalysisAgent;
import com.example.agentic.agent.InventoryAgent;
import com.example.agentic.agent.SupervisorAgent;
import com.example.agentic.data.InventoryRepository;
import com.example.agentic.data.OrderRepository;
import com.example.agentic.llm.Llm;
import com.example.agentic.llm.RuleBasedLlm;
import com.example.agentic.llm.SpringAiLlm;
import com.example.agentic.service.DecisionService;
import com.example.agentic.service.DecisionStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Wires the plain-Java agents into Spring. The agents themselves have no Spring dependency. */
@Configuration
public class AgentConfig {

    @Bean
    @ConditionalOnProperty(name = "agents.llm.enabled", havingValue = "true")
    public Llm springAiLlm(ChatClient.Builder builder) {
        return new SpringAiLlm(builder);
    }

    @Bean
    @ConditionalOnProperty(name = "agents.llm.enabled", havingValue = "false", matchIfMissing = true)
    public Llm ruleBasedLlm() {
        return new RuleBasedLlm();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public InventoryRepository inventoryRepository() {
        return new InventoryRepository();
    }

    @Bean
    public OrderRepository orderRepository() {
        return new OrderRepository();
    }

    @Bean
    public InventoryAgent inventoryAgent(InventoryRepository repository, Llm llm) {
        return new InventoryAgent(repository, llm);
    }

    @Bean
    public ImpactAnalysisAgent impactAnalysisAgent(InventoryRepository inventory, OrderRepository orders, Llm llm) {
        return new ImpactAnalysisAgent(inventory, orders, llm);
    }

    @Bean
    public CustomerServiceAgent customerServiceAgent(Llm llm) {
        return new CustomerServiceAgent(llm);
    }

    /** Small dedicated pool so the inventory and impact agents run in parallel. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentExecutor() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "agent-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public SupervisorAgent supervisorAgent(InventoryAgent inventoryAgent, ImpactAnalysisAgent impactAgent,
                                           CustomerServiceAgent customerServiceAgent, Llm llm,
                                           ExecutorService agentExecutor, Clock clock) {
        return new SupervisorAgent(inventoryAgent, impactAgent, customerServiceAgent, llm, agentExecutor, clock);
    }

    @Bean
    public DecisionService decisionService(SupervisorAgent supervisor, DecisionStore store, Clock clock) {
        return new DecisionService(supervisor, store, clock);
    }
}
