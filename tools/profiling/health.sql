-- Require no returned rows; separately require expected app frames in rail.sql.
SELECT name, severity, value FROM stats
WHERE value != 0 AND severity != 'info';
