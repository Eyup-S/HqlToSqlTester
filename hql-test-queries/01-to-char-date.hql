-- Tests date formatting: Oracle TO_CHAR equivalent in HQL (use function() or cast)
-- Replace "Employee" and "createdAt" with your actual entity and field names.
SELECT
    e.id                                          AS id,
    e.createdAt                                   AS rawDate,
    function('to_char', e.createdAt, 'YYYY-MM-DD') AS formattedDate
FROM Employee e
WHERE e.createdAt IS NOT NULL
