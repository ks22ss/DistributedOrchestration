package io.github.ks22ss.distributedorchestration.orchestrator.adapters.configuration;

import io.github.ks22ss.distributedorchestration.orchestrator.domain.policy.RunnableTaskSelector;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.policy.WorkflowDagValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainPolicyConfig {

    @Bean
    public WorkflowDagValidator workflowDagValidator() {
        return new WorkflowDagValidator();
    }

    @Bean
    public RunnableTaskSelector runnableTaskSelector() {
        return new RunnableTaskSelector();
    }
}

