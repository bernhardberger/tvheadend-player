package at.bernhardberger.tvhplayer.htsp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMetadataRepositoryTest {
    @Test
    fun initialSyncPublishesCompleteChannelAndTagSnapshotsInStableOrder() = runTest {
        val repository = ChannelMetadataRepository()

        repository.accept(message("tagAdd", "tagId" to 20, "tagName" to "Sport", "tagIndex" to 2))
        repository.accept(message("tagAdd", "tagId" to 10, "tagName" to "News", "tagIndex" to 1))
        repository.accept(channel(id = 30, name = "Thirty", number = 30))
        repository.accept(channel(id = 11, name = "Zulu", number = 10))
        repository.accept(channel(id = 10, name = "Alpha", number = 10))
        repository.accept(channel(id = 40, name = "Unnumbered", number = null))

        assertEquals(emptyList<ChannelUi>(), repository.channelsUi.value)
        assertEquals(emptyList<ChannelTagUi>(), repository.tagsUi.value)
        assertFalse(repository.metadataReady.value)

        val effect = repository.accept(message("initialSyncCompleted"))

        assertEquals(
            ChannelMetadataEffect.InitialSyncCompleted(setOf(10, 11, 30, 40)),
            effect,
        )
        assertEquals(listOf(10, 11, 30, 40), repository.channelsUi.value.map(ChannelUi::id))
        assertEquals(listOf(10, 20), repository.tagsUi.value.map(ChannelTagUi::id))
        assertTrue(repository.metadataReady.value)
        repository.awaitChannelsReady()
    }

    @Test
    fun channelMessagesPreservePartialFieldsInterpretAliasesAndPublishAfterSync() {
        val repository = ChannelMetadataRepository()
        assertEquals(
            ChannelMetadataEffect.ChannelUpserted(channelId = 42, isNew = true),
            repository.accept(
                message(
                    "channelAdd",
                    "channelId" to 42,
                    "channelName" to "World",
                    "channelNumber" to 8,
                    "channelIcon" to "old-icon",
                    "tagIds" to listOf(7),
                )
            ),
        )
        repository.accept(message("initialSyncCompleted"))

        assertEquals(
            ChannelMetadataEffect.ChannelUpserted(channelId = 42, isNew = false),
            repository.accept(
                message(
                    "channelUpdate",
                    "channelId" to 42,
                    "lcn" to 3L,
                    "channelTags" to listOf(9L, "ignored"),
                )
            ),
        )

        assertEquals(
            ChannelUi(
                id = 42,
                name = "World",
                number = 3,
                icon = "old-icon",
                tagIds = setOf(9),
            ),
            repository.channelsUi.value.single(),
        )
        assertNull(repository.accept(message("channelUpdate", "channelId" to 99, "lcn" to 99)))
        assertEquals(listOf(42), repository.channelsUi.value.map(ChannelUi::id))

        assertEquals(
            ChannelMetadataEffect.ChannelDeleted(channelId = 42),
            repository.accept(message("channelDelete", "channelId" to 42)),
        )
        assertEquals(emptyList<ChannelUi>(), repository.channelsUi.value)
    }

    @Test
    fun tagMessagesPreservePartialFieldsInterpretAliasesAndPublishAfterSync() {
        val repository = ChannelMetadataRepository()
        repository.accept(message("initialSyncCompleted"))

        repository.accept(message("tagAdd", "id" to 7, "name" to "News", "index" to 2))
        repository.accept(message("tagUpdate", "tagId" to 7, "tagName" to "Current affairs"))
        repository.accept(message("tagAdd", "tagId" to 8, "tagName" to "Sport", "tagIndex" to 1))

        assertEquals(listOf(8, 7), repository.tagsUi.value.map(ChannelTagUi::id))
        assertEquals("Current affairs", repository.tagsUi.value.last().name)
        assertEquals(2, repository.tagsUi.value.last().index)

        repository.accept(message("tagDelete", "id" to 7))
        assertEquals(listOf(8), repository.tagsUi.value.map(ChannelTagUi::id))
    }

    @Test
    fun reconnectKeepsPublishedMetadataUntilReplacementInitialSyncCompletes() = runTest {
        val repository = ChannelMetadataRepository()
        repository.accept(channel(id = 10, name = "Old", number = 10))
        repository.accept(message("tagAdd", "tagId" to 1, "tagName" to "Old tag", "tagIndex" to 1))
        repository.accept(message("initialSyncCompleted"))

        repository.reset(preservePublished = true)
        repository.accept(channel(id = 20, name = "New", number = 20))
        repository.accept(message("tagAdd", "tagId" to 2, "tagName" to "New tag", "tagIndex" to 1))
        val replacementReady = async { repository.awaitChannelsReady() }
        yield()

        assertEquals(listOf(10), repository.channelsUi.value.map(ChannelUi::id))
        assertEquals(listOf(1), repository.tagsUi.value.map(ChannelTagUi::id))
        assertFalse(repository.metadataReady.value)
        assertFalse(replacementReady.isCompleted)

        repository.accept(message("initialSyncCompleted"))
        replacementReady.await()

        assertEquals(listOf(20), repository.channelsUi.value.map(ChannelUi::id))
        assertEquals(listOf(2), repository.tagsUi.value.map(ChannelTagUi::id))
        assertTrue(repository.metadataReady.value)
    }

    @Test
    fun changedServerResetClearsPublishedMetadataImmediately() {
        val repository = ChannelMetadataRepository()
        repository.accept(channel(id = 10, name = "Old", number = 10))
        repository.accept(message("tagAdd", "tagId" to 1, "tagName" to "Old tag", "tagIndex" to 1))
        repository.accept(message("initialSyncCompleted"))

        repository.reset(preservePublished = false)

        assertEquals(emptyList<ChannelUi>(), repository.channelsUi.value)
        assertEquals(emptyList<ChannelTagUi>(), repository.tagsUi.value)
        assertFalse(repository.metadataReady.value)
    }

    @Test
    fun cancellingAReadinessWaiterDoesNotCancelRepositoryReadiness() = runTest {
        val repository = ChannelMetadataRepository()
        val cancelledWaiter = async { repository.awaitChannelsReady(timeoutMs = 30_000) }
        yield()

        cancelledWaiter.cancelAndJoin()
        assertTrue(cancelledWaiter.isCancelled)

        repository.accept(message("initialSyncCompleted"))
        repository.awaitChannelsReady()
        assertTrue(repository.metadataReady.value)
    }

    @Test
    fun resetInstallsReplacementReadinessBeforePublishingTheUnreadyPhase() = runTest {
        val repository = ChannelMetadataRepository()
        repository.accept(message("initialSyncCompleted"))
        val replacementReadinessObserved = CompletableDeferred<Unit>()
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            repository.metadataReady.drop(1).first { ready -> !ready }
            repository.awaitChannelsReady()
            replacementReadinessObserved.complete(Unit)
        }

        repository.reset()

        assertFalse(replacementReadinessObserved.isCompleted)
        repository.accept(message("initialSyncCompleted"))
        observer.join()
        assertTrue(replacementReadinessObserved.isCompleted)
    }

    private fun channel(id: Int, name: String, number: Int?) = message(
        method = "channelAdd",
        "channelId" to id,
        "channelName" to name,
        "channelNumber" to number,
    )

    private fun message(method: String, vararg fields: Pair<String, Any?>) =
        HtspMessage(method = method, seq = null, fields = mapOf(*fields))
}
