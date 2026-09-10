-- Sample-weighted native allocation/free estimates, NOT Java objects or total heap size.
SELECT p.pid,a.heap_name,count(*) AS rows,
       sum(CASE WHEN a.count>0 THEN a.count ELSE 0 END) AS allocated_count_estimate,
       sum(CASE WHEN a.size>0 THEN a.size ELSE 0 END) AS allocated_bytes_estimate,
       -sum(CASE WHEN a.size<0 THEN a.size ELSE 0 END) AS freed_bytes_estimate
FROM heap_profile_allocation a JOIN process p USING(upid)
WHERE p.name IN ('at.bernhardberger.tvhplayer','at.bernhardberger.tvhplayer.profile')
GROUP BY p.pid,a.heap_name;
