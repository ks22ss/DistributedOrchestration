package org.example.distributedorchestration.orchestrator.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowRequest;
import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowResponse;
import org.example.distributedorchestration.orchestrator.service.WorkflowSubmissionService;
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
public class WorkflowController {

    private final WorkflowSubmissionService workflowSubmissionService;

    /**
     * Submits a workflow: validates DAG, persists workflow and tasks, then triggers execution after commit.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitWorkflowResponse submit(@Valid @RequestBody SubmitWorkflowRequest request) {
        return workflowSubmissionService.submit(request);
    }
}
