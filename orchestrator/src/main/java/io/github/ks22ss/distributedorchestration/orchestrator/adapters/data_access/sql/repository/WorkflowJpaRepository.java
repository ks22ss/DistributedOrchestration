package io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.WorkflowEntity;
import java.util.List;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link WorkflowEntity} rows. */
public interface WorkflowJpaRepository extends JpaRepository<WorkflowEntity, String> {

    List<WorkflowEntity> findByStatus(WorkflowStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WorkflowEntity w SET w.status = :next WHERE w.workflowId = :id AND w.status = :current")
    int tryTransitionStatus(
            @Param("id") String workflowId,
            @Param("current") WorkflowStatus current,
            @Param("next") WorkflowStatus next);
}

