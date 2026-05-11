-- Tests aggregate functions and HAVING — standard HQL, should work on both dialects
SELECT
    e.department          AS department,
    count(e)              AS headCount,
    avg(e.salary)         AS avgSalary,
    max(e.salary)         AS maxSalary,
    min(e.hireDate)       AS earliestHire
FROM Employee e
GROUP BY e.department
HAVING count(e) >= :minHeadcount
ORDER BY count(e) DESC
