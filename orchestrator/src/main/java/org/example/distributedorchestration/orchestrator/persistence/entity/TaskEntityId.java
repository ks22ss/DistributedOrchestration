package org.example.distributedorchestration.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for {@link TaskEntity} matching {@code (task_id, workflow_id)}.
 */
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
