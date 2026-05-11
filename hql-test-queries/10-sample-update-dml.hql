-- Tests DML: UPDATE — will be translated to SQL and ROLLED BACK, never committed
-- Use this to verify Hibernate generates the correct column names and WHERE clause.
UPDATE Employee e
SET e.status = :newStatus,
    e.updatedAt = current_timestamp
WHERE e.department = :dept
  AND e.status = :oldStatus
