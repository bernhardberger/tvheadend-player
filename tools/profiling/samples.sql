-- Leaf counts from a SEPARATE sampled-CPU diagnostic, not elapsed time or call counts.
-- Require useful samples and inspect unwind errors before attributing app functions.
SELECT p.name AS package, t.name AS thread, coalesce(f.name,'[unresolved]') AS leaf,
       count(*) AS samples
FROM perf_sample s JOIN thread t USING (utid) JOIN process p USING (upid)
LEFT JOIN stack_profile_callsite c ON c.id=s.callsite_id
LEFT JOIN stack_profile_frame f ON f.id=c.frame_id
WHERE p.name IN ('at.bernhardberger.tvhplayer', 'at.bernhardberger.tvhplayer.profile')
GROUP BY p.name,t.name,leaf ORDER BY samples DESC LIMIT 40;
