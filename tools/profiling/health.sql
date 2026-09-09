-- Require no returned rows; separately require expected app frames in rail.sql.
SELECT name, severity, value FROM stats
WHERE value != 0 AND severity != 'info'
UNION ALL
SELECT 'incomplete_app_frames', 'error', count(*)
FROM actual_frame_timeline_slice f JOIN process p USING (upid)
WHERE p.name = 'at.bernhardberger.tvhplayer.profile' AND f.dur < 0
HAVING count(*) > 0;
