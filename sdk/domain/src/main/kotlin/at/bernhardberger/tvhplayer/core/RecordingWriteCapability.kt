package at.bernhardberger.tvhplayer.core

/**
 * Whether the current HTSP session may create, cancel, or delete recordings.
 *
 * Starts [Unknown] so the UI never flashes dead write controls. Becomes
 * [Allowed] only after a positive signal (authenticate `dvr=1` and/or a
 * successful `getDvrConfigs` / write RPC). [Denied] latches on `noaccess` or
 * permission-like errors and is not cleared until a new connection starts.
 */
enum class RecordingWriteCapability {
    Unknown,
    Allowed,
    Denied,
}

/** True only when write actions should be shown and enabled. */
fun RecordingWriteCapability.canModifyRecordings(): Boolean =
    this == RecordingWriteCapability.Allowed
