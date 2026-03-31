package io.github.ks22ss.distributedorchestration.orchestrator.adapters.presentation.web;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.presentation.web.dto.SubmitWorkflowRequest;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.presentation.web.dto.SubmitWorkflowResponse;
import io.github.ks22ss.distributedorchestration.orchestrator.application.command.SubmitWorkflowCommand;
import io.github.ks22ss.distributedorchestration.orchestrator.application.command.SubmitWorkflowTaskCommand;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.in.SubmitWorkflowUseCase;
import io.github.ks22ss.distributedorchestration.orchestrator.application.result.SubmitWorkflowResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for workflow orchestration. */
@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final SubmitWorkflowUseCase workflowSubmissionService;

    /**
     * Submits a workflow: validates DAG, persists workflow and tasks, then triggers execution after commit.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitWorkflowResponse submit(@Valid @RequestBody SubmitWorkflowRequest request) {
        log.info(
                "Submit workflow request received workflowId={} taskCount={}",
                request.workflowId(),
                request.tasks().size());
        SubmitWorkflowResult result = workflowSubmissionService.submit(toCommand(request));
        SubmitWorkflowResponse response = new SubmitWorkflowResponse(result.workflowId(), result.status());
        log.info(
                "Submit workflow request accepted workflowId={} status={}",
                response.workflowId(),
                response.status());
        return response;
    }

    private static SubmitWorkflowCommand toCommand(SubmitWorkflowRequest request) {
        List<SubmitWorkflowTaskCommand> tasks =
                request.tasks().stream()
                        .map(
                                t ->
                                        new SubmitWorkflowTaskCommand(
                                                t.taskId(),
                                                t.dependencies(),
                                                t.payload(),
                                                t.compensationPayload()))
                        .toList();
        return new SubmitWorkflowCommand(request.workflowId(), tasks);
    }
}

