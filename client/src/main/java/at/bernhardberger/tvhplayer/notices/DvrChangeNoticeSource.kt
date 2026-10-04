package at.bernhardberger.tvhplayer.notices

import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.DvrChangeKind
import at.bernhardberger.tvheadend.sdk.core.DvrChangeOrigin
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Consumes SDK facts, never snapshots or command responses. Run once in the application scope. */
class DvrChangeNoticeSource(private val repository: DvrRepository, private val notices: NoticeCenter) {
    suspend fun run(): Unit = coroutineScope {
        val groups = mutableMapOf<Triple<NoticeContext, AutorecRuleId, DvrChangeKind>, MutableMap<DvrEntryId, Notice.Dvr>>()
        repository.changes.collect { change ->
            val context = notices.context()
            if (context.session !== change.generationIdentity) return@collect
            val entry = change.current ?: change.previous ?: return@collect
            if (change.kind != DvrChangeKind.SCHEDULED) {
                // Do not announce a buffered schedule after that entry has already started or gone away.
                groups.filterKeys { it.first == context && it.third == DvrChangeKind.SCHEDULED }
                    .values.forEach { it.remove(entry.id) }
            }
            if (change.kind == DvrChangeKind.REMOVED && change.origin is DvrChangeOrigin.External &&
                entry.state != DvrEntryState.SCHEDULED) return@collect
            val notice = Notice.Dvr(change.kind, entry, change.origin)
            val rule = entry.autorecRuleId
            val groupCancellation = change.kind == DvrChangeKind.REMOVED &&
                change.origin is DvrChangeOrigin.External && entry.state == DvrEntryState.SCHEDULED
            if ((change.kind != DvrChangeKind.SCHEDULED && !groupCancellation) || rule == null) {
                notices.post(notice, context)
                return@collect
            }
            val key = Triple(context, rule, change.kind)
            val existing = groups[key]
            if (existing != null) {
                existing[entry.id] = notice
            } else {
                val group = mutableMapOf(entry.id to notice)
                groups[key] = group
                launch {
                    delay(2_000)
                    groups.remove(key)
                    group.values.firstOrNull()?.let { notices.post(it.copy(count = group.size), context) }
                }
            }
        }
    }
}
