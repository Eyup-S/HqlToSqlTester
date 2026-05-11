-- Tests CASE WHEN (portable HQL, replaces Oracle DECODE)
SELECT
    e.id     AS id,
    e.status AS status,
    CASE e.status
        WHEN 'ACTIVE'     THEN 'Working'
        WHEN 'TERMINATED' THEN 'Left'
        ELSE                   'Unknown'
    END      AS statusLabel,
    CASE
        WHEN e.salary > 50000 THEN 'Senior'
        WHEN e.salary > 30000 THEN 'Mid'
        ELSE                       'Junior'
    END      AS salaryBand
FROM Employee e
