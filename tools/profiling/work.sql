-- Scheduled CPU overlap is work on this thread, not elapsed frame lifetime.
-- Nested sections overlap: never sum these rows into a total cost.
-- Reject unhealthy traces separately. This query does not certify trace health.
-- Text atrace: validate the P44 marker PID against capture metadata first.
WITH marked_processes AS (
  SELECT DISTINCT t.upid FROM slice s JOIN thread_track tt ON tt.id=s.track_id
  JOIN thread t USING (utid) WHERE s.name='P44:input:down'
)
SELECT p.pid, p.name AS process, t.name AS thread, s.id, s.name,
       s.ts, s.dur,
       round(s.dur / 1e6, 3) AS elapsed_ms,
       round((SELECT sum(max(0, min(sc.ts + sc.dur, s.ts + s.dur) - max(sc.ts, s.ts)))
              FROM sched sc
              WHERE sc.utid = t.utid AND sc.dur > 0
                AND sc.ts < s.ts + s.dur AND sc.ts + sc.dur > s.ts) / 1e6, 3) AS running_ms
FROM slice s
JOIN thread_track tt ON tt.id = s.track_id
JOIN thread t USING (utid)
JOIN process p USING (upid)
WHERE (p.name IN ('at.bernhardberger.tvhplayer', 'at.bernhardberger.tvhplayer.profile')
       OR (p.name IS NULL AND p.upid IN (SELECT upid FROM marked_processes)))
  AND (t.is_main_thread = 1 OR t.name = 'RenderThread')
  AND s.dur > 0
  AND (s.name IN ('Recomposer:recompose', 'Compose:recompose',
                 'AndroidOwner:measureAndLayout', 'DrawFrame')
       OR s.name GLOB 'P44:*')
ORDER BY s.dur DESC
LIMIT 40;
