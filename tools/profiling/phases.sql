-- Per-call distributions. Nested phase names must not be added into a total.
WITH owned AS (
  SELECT DISTINCT t.upid FROM slice s JOIN thread_track tt ON tt.id=s.track_id
  JOIN thread t USING(utid) WHERE s.name='P44:input:down'
), costs AS (
  SELECT s.name,s.dur,
    (SELECT sum(max(0,min(sc.ts+sc.dur,s.ts+s.dur)-max(sc.ts,s.ts)))
     FROM sched sc WHERE sc.utid=t.utid AND sc.dur>0
       AND sc.ts<s.ts+s.dur AND sc.ts+sc.dur>s.ts) AS cpu_ns
  FROM slice s JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING(utid)
  WHERE t.upid IN (SELECT upid FROM owned) AND s.dur>0
    AND (s.name GLOB 'P44:*' OR s.name IN
      ('Recomposer:recompose','AndroidOwner:measureAndLayout','DrawFrame'))
)
SELECT name,count(*) AS calls,round(avg(dur)/1e6,3) AS mean_elapsed_ms,
       round(max(dur)/1e6,3) AS max_elapsed_ms,
       round(avg(cpu_ns)/1e6,3) AS mean_running_ms,
       round(max(cpu_ns)/1e6,3) AS max_running_ms
FROM costs GROUP BY name;
