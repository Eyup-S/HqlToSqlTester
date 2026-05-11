-- Tests COALESCE (Hibernate-standard, replaces Oracle NVL / NVL2)
SELECT
    e.id                                   AS id,
    coalesce(e.middleName, 'N/A')          AS middleName,
    coalesce(e.terminatedAt, current_date) AS effectiveEnd
FROM Employee e
