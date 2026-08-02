package at.bernhardberger.tvhplayer.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvhplayer.htsp.TvheadendClient
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackRuntimeContractTest {
    @Test
    fun playerBoundaryUsesTheStandardMedia3Contract() {
        val playerGetter = PlaybackRuntime::class.java.getMethod("getPlayer")

        assertEquals(Player::class.java, playerGetter.returnType)
        assertNotEquals(ExoPlayer::class.java, playerGetter.returnType)
    }

    @Test
    fun supportedPlaybackApiSignaturesDoNotExposeConcreteExoPlayer() {
        val supportedApiTypes = listOf(
            PlaybackRuntime::class.java,
        )
        val exposedTypes = buildList {
            supportedApiTypes.forEach { apiType ->
                apiType.declaredMethods
                    .filter { method -> Modifier.isPublic(method.modifiers) }
                    .forEach { method ->
                        add(method.returnType)
                        addAll(method.parameterTypes)
                    }
                apiType.declaredConstructors
                    .filter { constructor -> Modifier.isPublic(constructor.modifiers) }
                    .forEach { constructor -> addAll(constructor.parameterTypes) }
                apiType.declaredFields
                    .filter { field -> Modifier.isPublic(field.modifiers) }
                    .forEach { field -> add(field.type) }
            }
            val factory = Class.forName(
                "at.bernhardberger.tvhplayer.player.PlaybackRuntimeKt"
            ).getMethod(
                "createMedia3PlaybackRuntime",
                Context::class.java,
                TvheadendClient::class.java,
                PlaybackPreferencesProvider::class.java,
            )
            add(factory.returnType)
            addAll(factory.parameterTypes)
        }

        assertFalse(exposedTypes.contains(ExoPlayer::class.java))
    }
}
