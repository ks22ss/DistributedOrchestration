package io.github.ks22ss.distributedorchestration.orchestrator.application.port.in;

import io.github.ks22ss.distributedorchestration.orchestrator.application.command.SubmitWorkflowCommand;
import io.github.ks22ss.distributedorchestration.orchestrator.application.result.SubmitWorkflowResult;

public interface SubmitWorkflowUseCase {

    SubmitWorkflowResult submit(SubmitWorkflowCommand command);
}

