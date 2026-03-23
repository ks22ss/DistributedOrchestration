package org.example.distributedorchestration.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.orchestrator.persistence.converter.TaskDependenciesJsonConverter;

/**
 * Persisted task row in {@code tasks}, keyed by {@link TaskEntityId}.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class TaskEntity {

    @EmbeddedId
    private TaskEntityId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TaskStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Convert(converter = TaskDependenciesJsonConverter.class)
    @Column(name = "dependencies_json", columnDefinition = "TEXT")
    private List<String> dependencies;

    public TaskEntity(
            TaskEntityId id,
            TaskStatus status,
            int retryCount,
            String payload,
            List<String> dependencies
    ) {
        this.id = id;
        this.status = status;
        this.retryCount = retryCount;
        this.payload = payload;
        this.dependencies = dependencies == null ? List.of() : dependencies;
    }
}
