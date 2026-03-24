package org.example.distributedorchestration.orchestrator.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowResponse;
import org.example.distributedorchestration.orchestrator.application.service.WorkflowSubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WorkflowController.class)
@Import(GlobalExceptionHandler.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkflowSubmissionService workflowSubmissionService;

    @Test
    void postWorkflowsReturns201() throws Exception {
        when(workflowSubmissionService.submit(any()))
                .thenReturn(new SubmitWorkflowResponse("wf-1", "RUNNING"));

        mockMvc.perform(
                        post("/workflows")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "workflowId": "wf-1",
                                          "tasks": [
                                            { "taskId": "a", "dependencies": [], "payload": "x" }
                                          ]
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowId").value("wf-1"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void postWorkflowsReturns400WhenTasksEmpty() throws Exception {
        mockMvc.perform(
                        post("/workflows")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"workflowId\":\"wf-1\",\"tasks\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
