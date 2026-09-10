-- App-dispatch to focused-item callback, NOT shell injection or key-to-photon.
-- Only isolated inputs with exactly one callback before the next input qualify.
-- Missing/multiple callbacks remain NULL, not zero or a guessed causal pairing.
-- Text atrace has no process names: validate its marker PID against capture metadata.
WITH inputs AS (
  SELECT s.id, s.ts, t.utid,
         lead(s.ts) OVER (PARTITION BY t.utid ORDER BY s.ts) AS next_ts
  FROM slice s JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING (utid)
  JOIN process p USING (upid)
  WHERE (p.name IN ('at.bernhardberger.tvhplayer','at.bernhardberger.tvhplayer.profile') OR p.name IS NULL)
    AND t.is_main_thread=1 AND s.name='P44:input:down'
), focuses AS (
  SELECT s.id, s.ts, s.name, t.utid
  FROM slice s JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING (utid)
  WHERE s.name GLOB 'P44:focus:*'
)
SELECT i.id AS input_slice, count(f.id) AS callbacks,
       CASE WHEN count(f.id)=1 THEN min(f.name) END AS destination_kind,
       CASE WHEN count(f.id)=1 THEN round((min(f.ts)-i.ts)/1e6,3) END AS callback_ms
FROM inputs i LEFT JOIN focuses f ON f.utid=i.utid AND f.ts>=i.ts
  AND f.ts<min(coalesce(i.next_ts,i.ts+2000000000),i.ts+2000000000)
GROUP BY i.id ORDER BY i.ts;
