-- FrameTimeline duration is elapsed frame lifetime, NOT UI CPU time or key latency.
SELECT p.pid, p.name, f.layer_name, count(*) AS frames,
       round(avg(f.dur) / 1e6, 3) AS mean_ms,
       round(percentile(f.dur, 50) / 1e6, 3) AS p50_ms,
       round(percentile(f.dur, 95) / 1e6, 3) AS p95_ms,
       round(max(f.dur) / 1e6, 3) AS max_ms,
       sum(f.dur < 0) AS incomplete_frames
FROM actual_frame_timeline_slice f JOIN process p USING (upid)
WHERE p.name = 'at.bernhardberger.tvhplayer.profile'
GROUP BY p.pid, p.name, f.layer_name;
