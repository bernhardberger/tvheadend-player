package at.bernhardberger.tvhplayer.playback;

import at.bernhardberger.tvheadend.sdk.core.DvrCutpointsResult;
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult;
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding;
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult;
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission;
import at.bernhardberger.tvheadend.sdk.playback.GrowingRecordingFileLease;
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure;
import at.bernhardberger.tvheadend.sdk.playback.RecordingFileResult;
import kotlin.jvm.functions.Function0;

/**
 * A growing recording binding as the SDK issues one for a recording TVHeadend still writes.
 * The Kotlin caller supplies admissions from the SDK test factory; admission and lease
 * stay under the caller's session fence.
 */
final class GrowingRecordingBindings {
    private GrowingRecordingBindings() {
    }

    static PlaybackBindingResult<PlaybackBinding.Recording> bound(
        Function0<RecordingPlaybackAdmission> admission,
        GrowingRecordingFileLease lease
    ) {
        return new PlaybackBindingResult.Bound<>(new PlaybackBinding.Recording(
            true,
            admission,
            continuation -> new RecordingFileResult.Failed(TvheadendFailure.NOT_SUPPORTED),
            () -> new RecordingFileResult.Ok<>(lease),
            (growingLease, progress, continuation) -> DvrProgressResult.Accepted.INSTANCE,
            continuation -> new DvrCutpointsResult.Failed(TvheadendFailure.NOT_READY)));
    }
}
