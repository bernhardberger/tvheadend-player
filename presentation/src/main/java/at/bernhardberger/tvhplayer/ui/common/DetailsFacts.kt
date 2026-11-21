package at.bernhardberger.tvhplayer.ui.common

import android.content.res.Resources
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.EpgCreditRole
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvhplayer.core.humanLanguageName
import at.bernhardberger.tvhplayer.core.programmeAgeRating
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.core.recordingErrorCount
import at.bernhardberger.tvhplayer.core.recordingPaddedStart
import at.bernhardberger.tvhplayer.core.recordingPaddedStop
import at.bernhardberger.tvhplayer.core.recordingPicture
import at.bernhardberger.tvhplayer.core.recordingRecordedSpan
import at.bernhardberger.tvhplayer.core.recordingSizeBytes
import at.bernhardberger.tvhplayer.presentation.R
import java.util.Locale

/** Recording-only facts; the header keeps programme timing rather than padded file timing. */
@OptIn(ExperimentalTvheadendApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun recordingMetadataFacts(recording: DvrEntry, configurations: List<DvrConfiguration>,
    context: android.content.Context, locale: Locale, nowSec: Long): List<Pair<String, String>> = buildList {
    val resources = context.resources
    fun fact(label: Int, value: String?) {
        value?.takeIf(String::isNotBlank)?.let { add(resources.getString(label) to it) }
    }
    recordingSizeBytes(recording)?.let {
        fact(R.string.details_size, formatFileSize(it, context))
    }
    if (configurations.size > 1) fact(R.string.details_recording_profile,
        configurations.firstOrNull { it.id == recording.configId }?.name?.trim())
    fact(R.string.details_picture, recordingPicture(recording))
    val streams = recording.files.orEmpty().flatMap { it.streams.orEmpty() }
    fun languages(types: List<SubscriptionStreamType>) = streams.filter { it.type in types }.mapNotNull {
        humanLanguageName(it.language?.let(androidx.media3.common.util.Util::normalizeLanguageCode), resources.getString(R.string.track_unknown_language),
            resources.getString(R.string.track_multiple_languages), locale)
    }.distinct().joinToString(", ")
    fact(R.string.details_audio, languages(listOf(SubscriptionStreamType.AAC, SubscriptionStreamType.AC3,
        SubscriptionStreamType.EAC3, SubscriptionStreamType.MPEG2_AUDIO)))
    fact(R.string.subtitles, languages(listOf(SubscriptionStreamType.DVB_SUBTITLE,
        SubscriptionStreamType.TEXT_SUBTITLE, SubscriptionStreamType.TELETEXT)))
    recordingErrorCount(recording)?.let { errors ->
        fact(R.string.details_errors, if (errors == 0L) resources.getString(R.string.details_no_errors)
            else resources.getQuantityString(R.plurals.details_error_count, errors.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), errors))
    }
    // A missed or invalid entry recorded nothing; its slot, padding included, is what explains why.
    val neverRecorded = recording.state == DvrEntryState.MISSED || recording.state == DvrEntryState.INVALID
    (recordingRecordedSpan(recording, nowSec)?.takeUnless { neverRecorded } ?: recordingPaddedStart(recording)?.let { paddedStart ->
        recordingPaddedStop(recording)?.let { paddedStart to it }
    })?.let { (start, stop) ->
        fact(if (neverRecorded) R.string.recording_state_scheduled else R.string.details_recorded,
            formatTimeInterval(start.epochSeconds, stop.epochSeconds, withDate = true, locale = locale, nowSec = nowSec))
    }
    if (recording.removal == DvrRemoval.UntilSpaceNeeded)
        fact(R.string.details_retention, resources.getString(R.string.details_kept_until_space_needed))
}

@OptIn(ExperimentalTvheadendApi::class)
fun programmeCreditFacts(event: EpgEvent?, resources: Resources): List<Pair<String, List<String>>> = buildList {
    val credits = event?.credits.orEmpty()
    listOf(EpgCreditRole.ACTOR to R.plurals.details_cast, EpgCreditRole.GUEST to R.plurals.details_guests,
        EpgCreditRole.PRESENTER to R.plurals.details_presenters, EpgCreditRole.DIRECTOR to R.plurals.details_directors,
        EpgCreditRole.WRITER to R.plurals.details_writers).forEach { (role, label) ->
        // One entry per name and role; a person can hold several roles, and the wire may repeat a pair.
        val names = credits.filter { it.role == role }.map { it.name.trim() }.filter(String::isNotEmpty).distinct()
        if (names.isNotEmpty()) add(resources.getQuantityString(label, names.size) to names)
    }
}

/** Only the EPG facts present, as label to value, in reading order. */
fun programmeAboutFacts(event: EpgEvent?, recording: DvrEntry?, resources: Resources, locale: Locale): List<Pair<String, String>> =
    buildList {
        fun fact(label: Int, value: String?) {
            value?.trim()?.takeIf(String::isNotEmpty)?.let { add(resources.getString(label) to it) }
        }
        fun count(label: Int, number: Long?, total: Long?) = fact(label, number?.let { n ->
            total?.takeIf { it >= n }?.let { resources.getString(R.string.details_count_of, n.toInt(), it.toInt()) } ?: formatMetadataNumber(n, locale)
        })
        fact(R.string.details_genre, event?.genre?.takeIf(String::isNotBlank)
            ?: programmeCategory(event?.contentGenre ?: recording?.contentGenre)?.let { resources.getString(programmeCategoryLabelRes(it)) })
        (event?.episode ?: recording?.episode)?.let {
            count(R.string.details_season, it.seasonNumber, it.seasonCount)
            if (it.episodeNumber != null) count(R.string.details_episode, it.episodeNumber, it.episodeCount)
            else fact(R.string.details_episode, it.onscreen)
            count(R.string.details_part, it.partNumber, it.partCount)
        }
        fact(R.string.details_year, (event?.copyrightYear ?: recording?.copyrightYear)?.let { formatMetadataNumber(it, locale) })
        (event?.rating ?: recording?.rating)?.let { rating ->
            fact(R.string.details_age_rating, programmeAgeRating(rating.label, rating.age))
            fact(R.string.details_stars, rating.stars?.let { formatMetadataNumber(it, locale) })
        }
        event?.firstAired?.let {
            fact(R.string.details_first_aired, formatFirstAired(it.toEpochMilliseconds(), locale))
        }
        event?.isNew?.let {
            fact(R.string.details_broadcast, resources.getString(if (it) R.string.details_broadcast_new else R.string.details_broadcast_repeat))
        }
        fact(R.string.details_categories, event?.categories?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()?.joinToString(" • "))
        fact(R.string.details_keywords, event?.keywords?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()?.joinToString(" • "))
    }
