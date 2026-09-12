package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.MetadataCachePolicy
import java.io.File
import kotlin.time.Duration.Companion.days

internal fun appMetadataCachePolicy(root: File): MetadataCachePolicy = MetadataCachePolicy.create(
    root = root,
    metadataRetention = 7.days,
    artworkRetention = 30.days,
    artworkMaxBytes = 64L * 1024 * 1024,
)
