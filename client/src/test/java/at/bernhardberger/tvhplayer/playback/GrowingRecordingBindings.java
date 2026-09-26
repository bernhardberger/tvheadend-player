package at.bernhardberger.tvhplayer.playback;

import at.bernhardberger.tvheadend.sdk.core.DvrCutpointsResult;
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult;
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding;
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult;
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission;
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability;
import at.bernhardberger.tvheadend.sdk.playback.GrowingRecordingFileLease;
import at.bernhardberger.tvheadend.sdk.playback.RecordingFileFailure;
import at.bernhardberger.tvheadend.sdk.playback.RecordingFileResult;
import java.lang.reflect.Constructor;
import kotlin.jvm.functions.Function0;
import kotlin.time.Duration;

/**
 * A growing recording binding as the SDK issues one for a recording TVHeadend still writes.
 * The SDK's test kit scripts completed recordings only, so this calls the binding's and the
 * admissions' constructors, which Kotlin keeps internal (the admissions' only through
 * reflection); admission and lease stay under the caller's session fence.
 */
final class GrowingRecordingBindings {
    private GrowingRecordingBindings() {
    }

    static RecordingPlaybackAdmission growing() {
        return admission(RecordingPlaybackAdmission.GrowingStartOverOnly.class);
    }

    static RecordingPlaybackAdmission completed() {
        return admission(RecordingPlaybackAdmission.Completed.class);
    }

    /** An admission with supported progress and no saved position. */
    private static RecordingPlaybackAdmission admission(Class<? extends RecordingPlaybackAdmission> type) {
        try {
            Constructor<? extends RecordingPlaybackAdmission> constructor =
                type.getDeclaredConstructor(Duration.class, RecordingProgressCapability.class);
            constructor.setAccessible(true);
            return constructor.newInstance(null, RecordingProgressCapability.SUPPORTED);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    static PlaybackBindingResult<PlaybackBinding.Recording> bound(
        Function0<RecordingPlaybackAdmission> admission,
        GrowingRecordingFileLease lease
    ) {
        return new PlaybackBindingResult.Bound<>(new PlaybackBinding.Recording(
            true,
            admission,
            continuation -> new RecordingFileResult.Failed(RecordingFileFailure.NOT_SUPPORTED),
            () -> new RecordingFileResult.Ok<>(lease),
            (growingLease, progress, continuation) -> DvrProgressResult.Accepted.INSTANCE,
            continuation -> DvrCutpointsResult.NotReady.INSTANCE));
    }
}
