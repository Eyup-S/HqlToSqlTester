-- Tests current_date / current_timestamp / local_date — portable HQL keywords
-- These replace Oracle SYSDATE. Hibernate 6 maps them to the dialect's native equivalent.
SELECT
    e.id                                           AS id,
    current_date                                   AS today,
    current_timestamp                              AS nowWithTz,
    local_date                                     AS localToday,
    local_datetime                                 AS localNow,
    (current_date - e.hireDate)                    AS daysSinceHire
FROM Employee e
WHERE e.hireDate <= current_date
