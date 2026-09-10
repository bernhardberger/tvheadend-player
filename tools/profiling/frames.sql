-- App UI frame lifetimes; not CPU work, input latency, or physical presentation quality.
SELECT p.pid, layer_name, count(*) AS frames,
       sum(CASE WHEN dur<0 THEN 1 ELSE 0 END) AS incomplete,
       round(avg(CASE WHEN dur>=0 THEN dur END)/1e6,3) AS mean_lifetime_ms,
       round(max(dur)/1e6,3) AS max_lifetime_ms
FROM actual_frame_timeline_slice f JOIN process p USING(upid)
WHERE p.name IN ('at.bernhardberger.tvhplayer', 'at.bernhardberger.tvhplayer.profile')
  AND (layer_name GLOB '*JourneyProfileActivity*' OR layer_name GLOB '*MainActivity*')
  AND layer_name NOT GLOB '*SurfaceView*'
GROUP BY p.pid,layer_name;
