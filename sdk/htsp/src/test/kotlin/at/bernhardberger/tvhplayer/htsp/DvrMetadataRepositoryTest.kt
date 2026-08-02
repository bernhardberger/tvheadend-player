package at.bernhardberger.tvhplayer.htsp

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrMetadataRepositoryTest {
    @Test
    fun initialSyncPublishesWorkingEntriesAtomicallyInStartAndIdOrder() {
        val repository = DvrMetadataRepository()

        repository.accept(entry("dvrEntryAdd", id = 9, start = 100L))
        repository.accept(entry("dvrEntryAdd", id = 7, start = 50L))
        repository.accept(entry("dvrEntryAdd", id = 3, start = 100L))

        assertFalse(repository.entriesReady.value)
        assertEquals(emptyList<DvrEntry>(), repository.entries.value)

        repository.accept(message("initialSyncCompleted"))

        assertTrue(repository.entriesReady.value)
        assertEquals(listOf(7, 3, 9), repository.entries.value.map(DvrEntry::id))
    }

    @Test
    fun changesPublishImmediatelyAfterReadinessAndRepeatedInitialSyncRemainsReady() {
        val repository = DvrMetadataRepository()
        repository.accept(message("initialSyncCompleted"))

        repository.accept(entry("dvrEntryAdd", id = 4, start = 100L))
        assertEquals(listOf(4), repository.entries.value.map(DvrEntry::id))

        repository.accept(message("dvrEntryUpdate", "id" to 4, "title" to "Updated"))
        assertEquals("Updated", repository.entries.value.single().title)

        repository.accept(message("dvrEntryDelete", "id" to 999))
        assertEquals(listOf(4), repository.entries.value.map(DvrEntry::id))

        repository.accept(message("initialSyncCompleted"))
        assertTrue(repository.entriesReady.value)
        assertEquals(listOf(4), repository.entries.value.map(DvrEntry::id))

        repository.accept(message("dvrEntryDelete", "dvrId" to 4))
        assertEquals(emptyList<DvrEntry>(), repository.entries.value)
    }

    @Test
    fun partialUpdatesPreserveFieldsAndSupportEveryEntryAlias() {
        val repository = DvrMetadataRepository()
        repository.accept(
            message(
                "dvrEntryAdd",
                "dvrId" to 21,
                "eventId" to 210,
                "channel" to 6,
                "start" to 100L,
                "stop" to 200L,
                "title" to "Programme",
                "subtitle" to "Subtitle",
                "summary" to "Summary",
                "description" to "Description",
                "status" to "scheduled",
                "configId" to "config",
                "owner" to "owner",
                "creator" to "creator",
                "path" to "folder/file.ts",
                "channelName" to "Channel",
                "image" to "image",
                "fanartImage" to "fanart",
                "playPosition" to 10L,
                "playCount" to 2,
                "seasonNumber" to 1,
                "episodeNumber" to 2,
                "episodeCount" to 3,
                "partNumber" to 4,
                "partCount" to 5,
                "autorecId" to "auto",
                "timerecId" to "time",
            ),
        )
        repository.accept(message("initialSyncCompleted"))

        repository.accept(
            message(
                "dvrEntryUpdate",
                "id" to 21,
                "channelId" to 7,
                "state" to "recording",
                "playposition" to 20L,
                "playcount" to 3,
                "subtitle" to 99,
                "seasonNumber" to "not-a-number",
            ),
        )

        val entry = repository.entries.value.single()
        assertEquals(21, entry.id)
        assertEquals(210, entry.eventId)
        assertEquals(7, entry.channelId)
        assertEquals(100L, entry.start)
        assertEquals(200L, entry.stop)
        assertEquals("Programme", entry.title)
        assertEquals("Subtitle", entry.subtitle)
        assertEquals("Summary", entry.summary)
        assertEquals("Description", entry.description)
        assertEquals(DvrState.RECORDING, entry.state)
        assertEquals("config", entry.configId)
        assertEquals("owner", entry.owner)
        assertEquals("creator", entry.creator)
        assertEquals("folder/file.ts", entry.path)
        assertEquals("Channel", entry.channelName)
        assertEquals("image", entry.image)
        assertEquals("fanart", entry.fanartImage)
        assertEquals(20L, entry.playPosition)
        assertEquals(3, entry.playCount)
        assertEquals(1, entry.seasonNumber)
        assertEquals(2, entry.episodeNumber)
        assertEquals(3, entry.episodeCount)
        assertEquals(4, entry.partNumber)
        assertEquals(5, entry.partCount)
        assertEquals("auto", entry.autorecId)
        assertEquals("time", entry.timerecId)
    }

    @Test
    fun errorPrecedenceUsesExistingStateMapping() {
        val repository = DvrMetadataRepository()
        repository.accept(
            message(
                "dvrEntryAdd",
                "id" to 1,
                "state" to "completed",
                "statusError" to "disk full",
            ),
        )
        repository.accept(message("initialSyncCompleted"))

        assertEquals(DvrState.FAILED, repository.entries.value.single().state)
        assertEquals("disk full", repository.entries.value.single().failureReason)

        repository.accept(
            message(
                "dvrEntryUpdate",
                "id" to 1,
                "error" to "user cancelled",
                "statusError" to "ignored",
            ),
        )

        assertEquals(DvrState.CANCELLED, repository.entries.value.single().state)
        assertEquals("user cancelled", repository.entries.value.single().failureReason)
    }

    @Test
    fun filesReplaceClearSkipMalformedMembersAndRetainOnNonListInput() {
        val repository = DvrMetadataRepository()
        repository.accept(
            message(
                "dvrEntryAdd",
                "id" to 1,
                "files" to listOf(
                    mapOf("id" to 4L, "filename" to "one.ts", "size" to 12.0),
                    "malformed",
                    mapOf("id" to 5, "path" to "two.ts", "size" to 13L),
                ),
            ),
        )
        repository.accept(message("initialSyncCompleted"))

        assertEquals(
            listOf(
                DvrFile(id = 4, path = "one.ts", size = 12L),
                DvrFile(id = 5, path = "two.ts", size = 13L),
            ),
            repository.entries.value.single().files,
        )

        repository.accept(message("dvrEntryUpdate", "id" to 1))
        repository.accept(message("dvrEntryUpdate", "id" to 1, "files" to "not-a-list"))
        assertEquals(2, repository.entries.value.single().files.size)

        repository.accept(message("dvrEntryUpdate", "id" to 1, "files" to emptyList<Any?>()))
        assertEquals(emptyList<DvrFile>(), repository.entries.value.single().files)
    }

    @Test
    fun resetPreservesOrClearsPublishedReadinessAndConfigsExactly() {
        val repository = DvrMetadataRepository()
        repository.accept(entry("dvrEntryAdd", id = 1, start = 100L))
        repository.accept(message("initialSyncCompleted"))
        repository.ingestDvrConfigsReply(
            reply("dvrconfigs" to listOf(mapOf("uuid" to "default"))),
        )

        repository.reset(preservePublished = true)

        assertTrue(repository.entriesReady.value)
        assertEquals(listOf(1), repository.entries.value.map(DvrEntry::id))
        assertEquals(listOf("default"), repository.configs.value.map(DvrConfig::id))

        repository.accept(entry("dvrEntryAdd", id = 2, start = 200L))
        assertEquals(listOf(1), repository.entries.value.map(DvrEntry::id))
        repository.accept(message("initialSyncCompleted"))
        assertEquals(listOf(2), repository.entries.value.map(DvrEntry::id))

        repository.reset(preservePublished = false)

        assertFalse(repository.entriesReady.value)
        assertEquals(emptyList<DvrEntry>(), repository.entries.value)
        assertEquals(emptyList<DvrConfig>(), repository.configs.value)
    }

    @Test
    fun configAliasesOrderFilteringCoercionsReplacementAndClearingArePreserved() {
        val repository = DvrMetadataRepository()
        repository.ingestDvrConfigsReply(
            reply(
                "dvrconfigs" to listOf(
                    mapOf("uuid" to "first", "profileName" to "First", "enabled" to false),
                    mapOf("configId" to 22, "name" to "", "enabled" to 0),
                    mapOf("id" to "third", "name" to "Third", "enabled" to "FALSE"),
                    mapOf(
                        "uuid" to "",
                        "configId" to "fallback",
                        "profileName" to "Fallback",
                        "comment" to " ",
                        "enabled" to "0",
                    ),
                    mapOf("id" to "string-default", "enabled" to "yes"),
                    mapOf("id" to "fraction", "enabled" to 0.5),
                    mapOf("name" to "missing id"),
                    "malformed",
                ),
            ),
        )

        assertEquals(
            listOf("first", "22", "third", "fallback", "string-default", "fraction"),
            repository.configs.value.map(DvrConfig::id),
        )
        assertEquals(listOf(false, false, false, false, true, true), repository.configs.value.map(DvrConfig::enabled))
        assertEquals("22", repository.configs.value[1].name)
        assertNull(repository.configs.value[3].comment)

        repository.ingestDvrConfigsReply(
            reply("configs" to listOf(mapOf("id" to "replacement", "name" to "Replacement"))),
        )
        assertEquals(listOf("replacement"), repository.configs.value.map(DvrConfig::id))

        repository.ingestDvrConfigsReply(reply())
        assertEquals(emptyList<DvrConfig>(), repository.configs.value)

        repository.ingestDvrConfigsReply(
            reply("configs" to listOf(mapOf("id" to "clear-me"))),
        )
        repository.clearConfigs()
        assertEquals(emptyList<DvrConfig>(), repository.configs.value)
    }

    @Test
    fun unknownAndMalformedMessagesHaveNoEffect() {
        val repository = DvrMetadataRepository()
        repository.accept(entry("dvrEntryAdd", id = 1, start = 100L))
        repository.accept(message("initialSyncCompleted"))

        repository.accept(message("unknown", "id" to 2))
        repository.accept(message("dvrEntryAdd", "title" to "No ID"))
        repository.accept(message("dvrEntryUpdate", "dvrId" to "not-an-id"))
        repository.accept(message("dvrEntryDelete"))

        assertEquals(listOf(1), repository.entries.value.map(DvrEntry::id))
        assertEquals(repository.entries.value.single(), repository.entryForEvent(10))
        assertNull(repository.entryForEvent(999))
    }

    @Test
    fun concurrentDistinctAcceptsCannotLoseWorkingEntries() {
        val repository = DvrMetadataRepository()
        val start = CountDownLatch(1)
        val done = CountDownLatch(50)

        repeat(50) { index ->
            thread(name = "dvr-metadata-$index") {
                start.await()
                repository.accept(entry("dvrEntryAdd", id = index + 1, start = index.toLong()))
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        repository.accept(message("initialSyncCompleted"))

        assertEquals((1..50).toSet(), repository.entries.value.map(DvrEntry::id).toSet())
    }

    @Test
    fun reentrantResetCannotBeOverwrittenByAnOlderPublication() = runBlocking {
        val repository = DvrMetadataRepository()
        repository.ingestDvrConfigsReply(
            reply("configs" to listOf(mapOf("id" to "default"))),
        )
        repository.accept(entry("dvrEntryAdd", id = 1, start = 100L))
        val reset = launch(
            context = Dispatchers.Unconfined,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            repository.entries.drop(1).first()
            repository.reset(preservePublished = false)
        }

        repository.accept(message("initialSyncCompleted"))
        reset.join()

        assertEquals(emptyList<DvrEntry>(), repository.entries.value)
        assertFalse(repository.entriesReady.value)
        assertEquals(emptyList<DvrConfig>(), repository.configs.value)

        repository.accept(entry("dvrEntryAdd", id = 2, start = 200L))
        assertEquals(emptyList<DvrEntry>(), repository.entries.value)
        assertFalse(repository.entriesReady.value)

        repository.accept(message("initialSyncCompleted"))
        assertEquals(listOf(2), repository.entries.value.map(DvrEntry::id))
        assertTrue(repository.entriesReady.value)
    }

    private fun entry(method: String, id: Int, start: Long): HtspMessage = message(
        method,
        "id" to id,
        "eventId" to id * 10,
        "channelId" to 1,
        "start" to start,
        "stop" to start + 10L,
        "title" to "Entry $id",
        "state" to "scheduled",
    )

    private fun message(method: String, vararg fields: Pair<String, Any?>): HtspMessage =
        HtspMessage(method = method, seq = null, fields = mapOf(*fields))

    private fun reply(vararg fields: Pair<String, Any?>): HtspMessage =
        HtspMessage(method = null, seq = 1, fields = mapOf(*fields))
}
