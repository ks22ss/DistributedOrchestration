package org.example.distributedorchestration.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.distributedorchestration.common.model.WorkflowStatus;

/**
 * Persisted workflow aggregate root (source of truth row in {@code workflows}).
 */
@Entity
@Table(name = "workflows")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowEntity {

    @Id
    @Column(name = "workflow_id", nullable = false, length = 255)
    private String workflowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private WorkflowStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WorkflowEntity(String workflowId, WorkflowStatus status, Instant createdAt) {
        this.workflowId = workflowId;
        this.status = status;
        this.createdAt = createdAt;
    }
}
