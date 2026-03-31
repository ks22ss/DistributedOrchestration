package io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite primary key matching {@code (task_id, workflow_id)}. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaskEntityId implements Serializable {

    @Column(name = "task_id", nullable = false, length = 255)
    private String taskId;

    @Column(name = "workflow_id", nullable = false, length = 255)
    private String workflowId;
}

