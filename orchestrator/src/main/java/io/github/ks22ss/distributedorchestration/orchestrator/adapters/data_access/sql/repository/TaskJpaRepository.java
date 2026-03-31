package io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntityId;
import java.time.Instant;
import java.util.List;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link TaskEntity} rows. */
public interface TaskJpaRepository extends JpaRepository<TaskEntity, TaskEntityId> {

    @Query("SELECT t FROM TaskEntity t WHERE t.id.workflowId = :workflowId")
    List<TaskEntity> findByWorkflowId(@Param("workflowId") String workflowId);

    @Query(
            "SELECT DISTINCT t.id.workflowId FROM TaskEntity t WHERE t.status = :status AND t.nextRetryAt"
                    + " IS NOT NULL AND t.nextRetryAt <= :now")
    List<String> findDistinctWorkflowIdsWithRetriesDue(
            @Param("status") TaskStatus status, @Param("now") Instant now);
}

