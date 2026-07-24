package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.HtspMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class DvrConfigMappingTest {
    @Test
    fun parsesUsableServerConfigurations() {
        val reply = HtspMessage(
            method = null,
            seq = 1,
            fields = mapOf(
                "dvrconfigs" to listOf(
                    mapOf(
                        "uuid" to "default",
                        "name" to "Default",
                        "comment" to "Household",
                        "enabled" to 1,
                    ),
                    mapOf("uuid" to "", "name" to "Invalid"),
                    mapOf("uuid" to "archive", "name" to "Archive", "enabled" to 0),
                )
            ),
        )

        val configs = dvrConfigsFromReply(reply)

        assertEquals(listOf("default", "archive"), configs.map { it.id })
        assertEquals(false, configs.last().enabled)
    }
}
