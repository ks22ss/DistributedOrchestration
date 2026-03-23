package org.example.distributedorchestration.orchestrator.repository;

import org.example.distributedorchestration.orchestrator.persistence.entity.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link WorkflowEntity} rows.
 */
public interface WorkflowJpaRepository extends JpaRepository<WorkflowEntity, String> {}
