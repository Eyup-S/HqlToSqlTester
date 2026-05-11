-- Tests numeric casting: Oracle TO_NUMBER vs Hibernate 6 cast()
-- Hibernate 6 introduced a proper cast() function in HQL.
-- cast(expr AS type) compiles to the dialect's native CAST or CONVERT.
SELECT
    e.id                          AS id,
    cast(e.salaryStr AS double)   AS salaryAsDouble,
    cast(e.salaryStr AS integer)  AS salaryAsInt,
    abs(e.salary)                 AS absSalary,
    mod(cast(e.salary AS integer), 1000) AS salaryMod
FROM Employee e
WHERE e.salaryStr IS NOT NULL
