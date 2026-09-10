-- Ordered main-thread evidence: a committed Guide enter/dispose distinguishes
-- fresh destination construction from remeasurement within the same lifetime.
SELECT s.id, s.name, s.ts, s.dur / 1e6 AS elapsed_ms,
  (SELECT COALESCE(SUM(MAX(0, MIN(r.ts + r.dur, s.ts + s.dur) - MAX(r.ts, s.ts))), 0) / 1e6
   FROM sched r WHERE r.utid = t.utid AND r.ts < s.ts + s.dur
     AND r.ts + r.dur > s.ts) AS running_ms
FROM slice s
JOIN thread_track tt ON tt.id = s.track_id
JOIN thread t ON t.utid = tt.utid
JOIN process p ON p.upid = t.upid
WHERE t.tid = p.pid AND p.name = 'at.bernhardberger.tvhplayer'
  AND s.dur >= 0 AND (
    s.name GLOB 'P46:*:guide' OR s.name GLOB 'P44:sidebarRequest:*'
    OR s.name IN ('P44:guideIndex', 'P44:measure:guide', 'P44:place:guide',
      'AndroidOwner:measureAndLayout'))
ORDER BY s.ts;
