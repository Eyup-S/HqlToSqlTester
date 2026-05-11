-- Tests date truncation: Oracle TRUNC(date) vs Postgres DATE_TRUNC
-- In HQL use function('trunc', ...) for Oracle; for portable HQL use
-- cast(e.createdAt as date) or a @Formula mapped field instead.
SELECT
    e.id                                              AS id,
    function('trunc', e.createdAt)                    AS truncatedDate,
    function('trunc', e.salary, 0)                    AS truncatedSalary
FROM Employee e
WHERE function('trunc', e.createdAt) >= :fromDate
