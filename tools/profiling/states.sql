-- Off-CPU is not synonymous with blocked: R/R+ are runnable scheduling delay.
-- D identifies uninterruptible waiting, not its cause. Nested sections overlap.
-- For text atrace validate the P44 marker PID against capture metadata first.
WITH marked_processes AS (
  SELECT DISTINCT t.upid FROM slice s JOIN thread_track tt ON tt.id=s.track_id
  JOIN thread t USING (utid) WHERE s.name='P44:input:down'
), phases AS (
  SELECT s.id, s.name, s.ts, s.dur, t.utid, p.name AS package
  FROM slice s JOIN thread_track tt ON tt.id=s.track_id
  JOIN thread t USING (utid) JOIN process p USING (upid)
  WHERE (p.name IN ('at.bernhardberger.tvhplayer', 'at.bernhardberger.tvhplayer.profile')
         OR (p.name IS NULL AND p.upid IN (SELECT upid FROM marked_processes)))
    AND (t.is_main_thread=1 OR t.name='RenderThread') AND s.dur>0
    AND (s.name GLOB '*recompose*' OR s.name GLOB '*measureAndLayout*'
         OR s.name GLOB 'DrawFrame*' OR s.name GLOB 'P44:*')
  ORDER BY s.dur DESC LIMIT 20
)
SELECT s.id, s.package, s.name, st.state,
       round(sum(max(0,min(st.ts+st.dur,s.ts+s.dur)-max(st.ts,s.ts)))/1e6,3) AS overlap_ms
FROM phases s JOIN thread_state st ON st.utid=s.utid AND st.dur>0
  AND st.ts<s.ts+s.dur AND st.ts+st.dur>s.ts
GROUP BY s.id,st.state ORDER BY s.id,overlap_ms DESC;
