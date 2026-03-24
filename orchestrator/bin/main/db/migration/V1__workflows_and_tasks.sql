-- Step 3 schema (spec) plus dependencies_json for DAG edges between tasks.
CREATE TABLE workflows (
    workflow_id VARCHAR(255) PRIMARY KEY,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tasks (
    task_id VARCHAR(255) NOT NULL,
    workflow_id VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    payload TEXT,
    dependencies_json TEXT,
    PRIMARY KEY (task_id, workflow_id),
    CONSTRAINT fk_tasks_workflow FOREIGN KEY (workflow_id) REFERENCES workflows (workflow_id) ON DELETE CASCADE
);

CREATE INDEX idx_tasks_workflow_id ON tasks (workflow_id);
