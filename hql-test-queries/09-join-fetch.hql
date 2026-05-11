-- Tests JOIN and JOIN FETCH — watches for N+1 and verifies generated SQL uses JOINs
SELECT e
FROM Employee e
JOIN FETCH e.department d
LEFT JOIN FETCH e.roles r
WHERE e.status = :status
  AND d.active = true
