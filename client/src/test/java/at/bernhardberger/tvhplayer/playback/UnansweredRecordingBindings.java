package at.bernhardberger.tvhplayer.playback;

import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding;
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult;
import kotlinx.coroutines.DelayKt;

/**
 * A recording binding whose file never answers, as a server that stopped serving it. The real
 * player then buffers on instead of failing the fake's unreadable file after its load retries.
 */
final class UnansweredRecordingBindings {
    private UnansweredRecordingBindings() {
    }

    static PlaybackBindingResult<PlaybackBinding.Recording> unanswered(
        PlaybackBindingResult<PlaybackBinding.Recording> result
    ) {
        if (!(result instanceof PlaybackBindingResult.Bound)) return result;
        PlaybackBinding.Recording binding = ((PlaybackBindingResult.Bound<PlaybackBinding.Recording>) result).getBinding();
        return new PlaybackBindingResult.Bound<>(new PlaybackBinding.Recording(
            binding.getStartedGrowing(),
            binding::getAdmission,
            DelayKt::awaitCancellation,
            binding::bindGrowingRecording,
            binding::reportProgress,
            binding::cutpoints,
            () -> null));
    }
}
