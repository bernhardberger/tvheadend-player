-- Check trace health and match the returned PID to the recorded device PID first.
-- firstFrame is application callback arrival, READY can recur, and admitted is
-- after command serialization. Repeated admissions must not be silently paired.
WITH owners AS (
  SELECT DISTINCT t.upid FROM slice s
  JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING(utid)
  WHERE s.name GLOB 'P44:tune:*' OR s.name GLOB 'P44:firstFrame:*'
  UNION
  SELECT upid FROM process WHERE name='at.bernhardberger.tvhplayer'
)
SELECT p.pid, s.id AS slice_id, s.ts AS timestamp_ns, s.dur AS duration_ns, s.name
FROM slice s JOIN thread_track tt ON tt.id=s.track_id
JOIN thread t USING(utid) JOIN process p USING(upid)
WHERE t.upid IN (SELECT upid FROM owners) AND t.is_main_thread=1
  AND (s.name GLOB 'P44:tune:*' OR s.name GLOB 'P44:ready:*'
       OR s.name GLOB 'P44:firstFrame:*' OR s.name GLOB 'P44:video:*'
       OR s.name='bindApplication' OR s.name='activityResume')
ORDER BY s.ts;
