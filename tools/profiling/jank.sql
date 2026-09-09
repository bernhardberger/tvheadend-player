SELECT f.jank_type, f.present_type, f.on_time_finish, count(*) AS frames
FROM actual_frame_timeline_slice f JOIN process p USING (upid)
WHERE p.name = 'at.bernhardberger.tvhplayer.profile'
GROUP BY f.jank_type, f.present_type, f.on_time_finish;
