-- Tests portable string functions in Hibernate 6 HQL
-- concat, upper, lower, length, trim, substring, locate
SELECT
    e.id                                        AS id,
    concat(e.firstName, ' ', e.lastName)        AS fullName,
    upper(e.email)                              AS emailUpper,
    lower(e.department)                         AS deptLower,
    length(e.lastName)                          AS lastNameLen,
    trim(e.firstName)                           AS firstNameTrimmed,
    substring(e.email, 1, locate('@', e.email) - 1) AS emailLocalPart
FROM Employee e
WHERE e.email IS NOT NULL
