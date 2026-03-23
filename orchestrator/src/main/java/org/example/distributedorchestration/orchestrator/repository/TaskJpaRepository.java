package org.example.distributedorchestration.orchestrator.repository;

import java.util.List;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntity;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link TaskEntity} rows.
 */
public interface TaskJpaRepository extends JpaRepository<TaskEntity, TaskEntityId> {

    @Query("SELECT t FROM TaskEntity t WHERE t.id.workflowId = :workflowId")
    List<TaskEntity> findByWorkflowId(@Param("workflowId") String workflowId);
}
