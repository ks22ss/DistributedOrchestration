package io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.converter.TaskDependenciesJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.distributedorchestration.common.model.TaskStatus;

/** Persisted task row in {@code tasks}, keyed by {@link TaskEntityId}. */
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

    @Column(name = "compensation_payload", columnDefinition = "TEXT")
    private String compensationPayload;

    @Convert(converter = TaskDependenciesJsonConverter.class)
    @Column(name = "dependencies_json", columnDefinition = "TEXT")
    private List<String> dependencies;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    public TaskEntity(
            TaskEntityId id,
            TaskStatus status,
            int retryCount,
            String payload,
            String compensationPayload,
            List<String> dependencies) {
        this.id = id;
        this.status = status;
        this.retryCount = retryCount;
        this.payload = payload;
        this.compensationPayload = compensationPayload;
        this.dependencies = dependencies == null ? List.of() : dependencies;
    }
}

