package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_LEARNED_MAX_MILLIS
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_LEARNED_MIN_MILLIS
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_RECENT_VERDICTS
import at.bernhardberger.tvhplayer.settings.StartupBufferLearningState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Outcome of one observed live start. Cut-short windows produce no verdict at all. */
enum class StartupBufferVerdict { TROUBLE, CLEAN }

/** The start-up buffer the next live start uses, and whether it was learned. */
data class StartupBufferInEffect(val millis: Int, val automatic: Boolean)

object StartupBufferPolicy {
    const val STEP_MILLIS = 500
    const val TROUBLES_PER_STEP_UP = 2
    const val CLEAN_WINDOWS_PER_STEP_DOWN = STARTUP_BUFFER_RECENT_VERDICTS
    const val WINDOW_MILLIS = 30_000L
    const val UNDERRUNS_FOR_TROUBLE = 3

    /** Learned state for [profile]; a state learned for another server starts over. */
    fun learningFor(stored: StartupBufferLearningState, profile: String?): StartupBufferLearningState =
        if (stored.profile == profile) stored else StartupBufferLearningState(profile = profile)

    /**
     * One learning step, favouring a fast start. Trouble raises the level only
     * when it is the second trouble among the last five verdicts (cap 3 s); five
     * clean windows in a row lower it (floor 0.5 s). Either move starts the
     * verdict history over.
     */
    fun after(
        stored: StartupBufferLearningState,
        verdict: StartupBufferVerdict,
        profile: String?,
    ): StartupBufferLearningState {
        val current = learningFor(stored, profile)
        val recent = (current.recentVerdicts + verdict).takeLast(STARTUP_BUFFER_RECENT_VERDICTS)
        return when {
            verdict == StartupBufferVerdict.TROUBLE &&
                recent.count { it == StartupBufferVerdict.TROUBLE } >= TROUBLES_PER_STEP_UP -> current.copy(
                levelMillis = (current.levelMillis + STEP_MILLIS).coerceAtMost(STARTUP_BUFFER_LEARNED_MAX_MILLIS),
                recentVerdicts = emptyList(),
            )
            recent.size >= CLEAN_WINDOWS_PER_STEP_DOWN &&
                recent.takeLast(CLEAN_WINDOWS_PER_STEP_DOWN).all { it == StartupBufferVerdict.CLEAN } -> current.copy(
                levelMillis = (current.levelMillis - STEP_MILLIS).coerceAtLeast(STARTUP_BUFFER_LEARNED_MIN_MILLIS),
                recentVerdicts = emptyList(),
            )
            else -> current.copy(recentVerdicts = recent)
        }
    }

    fun inEffect(settingMillis: Int, stored: StartupBufferLearningState, profile: String?): StartupBufferInEffect =
        if (settingMillis == STARTUP_BUFFER_AUTOMATIC) {
            StartupBufferInEffect(learningFor(stored, profile).levelMillis, automatic = true)
        } else {
            StartupBufferInEffect(settingMillis, automatic = false)
        }
}

/**
 * The observation window after one live start, as a pure state machine. A
 * window is armed by a live start, runs while playback plays, and ends in at
 * most one verdict: trouble as soon as it is seen, clean once [elapsed] reports
 * the full window, or nothing when it is cut short.
 */
internal class StartupBufferWindow {
    enum class Phase { IDLE, ARMED, RUNNING }

    var phase: Phase = Phase.IDLE
        private set
    private var underruns = 0

    /** A live start (tune, retune or timeshift seek) replaces any earlier window. */
    fun arm() {
        phase = Phase.ARMED
        underruns = 0
    }

    /** Zap, pause, seek, background, stop, error or recording: no verdict. */
    fun cut() {
        phase = Phase.IDLE
        underruns = 0
    }

    /** Playback began; returns true when this started the window. */
    fun startIfArmed(): Boolean {
        if (phase != Phase.ARMED) return false
        phase = Phase.RUNNING
        return true
    }

    fun rebuffered(): StartupBufferVerdict? = if (phase == Phase.RUNNING) finish(StartupBufferVerdict.TROUBLE) else null

    fun underrun(): StartupBufferVerdict? {
        if (phase != Phase.RUNNING) return null
        underruns++
        return if (underruns >= StartupBufferPolicy.UNDERRUNS_FOR_TROUBLE) finish(StartupBufferVerdict.TROUBLE) else null
    }

    fun elapsed(): StartupBufferVerdict? = if (phase == Phase.RUNNING) finish(StartupBufferVerdict.CLEAN) else null

    private fun finish(verdict: StartupBufferVerdict): StartupBufferVerdict {
        cut()
        return verdict
    }
}

/**
 * Server identity the Automatic level is learned for: the same per-server
 * identity remembered audio choices use, replaced whenever the server profile
 * changes, and null while no server profile is available.
 */
fun AppProfileOwner.startupBufferIdentity(): Flow<String?> = serverProfile
    .map { profile -> if (profile is ServerProfileReadResult.Available) audioProfileId else null }
    .distinctUntilChanged()
