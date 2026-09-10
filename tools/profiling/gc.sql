-- GC-related elapsed slices. Concurrent background GC duration is NOT a main-thread pause.
SELECT p.pid,t.name AS thread,s.name,count(*) AS events,
       round(max(s.dur)/1e6,3) AS max_elapsed_ms
FROM slice s JOIN thread_track tt ON tt.id=s.track_id
JOIN thread t USING(utid) JOIN process p USING(upid)
WHERE s.dur>=0 AND (s.name GLOB '*GC*' OR s.name GLOB '*gc*' OR s.name GLOB '*SuspendAll*')
  AND p.upid IN (
    SELECT t2.upid FROM slice s2 JOIN thread_track tt2 ON tt2.id=s2.track_id
    JOIN thread t2 USING(utid) WHERE s2.name='P44:input:down'
  )
GROUP BY p.pid,t.name,s.name;
