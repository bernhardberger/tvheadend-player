package at.bernhardberger.tvhplayer.sdk.playback.consumer

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackImplementationVisibilityContractTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun externalAndroidFrontendCanCompileAgainstSupportedSdkTypes() {
        val result = compileExternalConsumer(
            """
                package external.consumer

                import android.content.Context
                import androidx.media3.common.Player
                import at.bernhardberger.tvhplayer.htsp.ChannelEpgRuntime
                import at.bernhardberger.tvhplayer.htsp.DvrRuntime
                import at.bernhardberger.tvhplayer.htsp.TvheadendClient
                import at.bernhardberger.tvhplayer.player.PlaybackPreferencesProvider
                import at.bernhardberger.tvhplayer.player.PlaybackRuntime
                import at.bernhardberger.tvhplayer.player.createMedia3PlaybackRuntime

                class SupportedPlaybackApi(
                    val client: TvheadendClient,
                    val channels: ChannelEpgRuntime,
                    val dvr: DvrRuntime,
                    val playback: PlaybackRuntime,
                ) {
                    val player: Player = playback.player

                    fun create(
                        context: Context,
                        preferences: PlaybackPreferencesProvider,
                    ): PlaybackRuntime = createMedia3PlaybackRuntime(context, client, preferences)
                }
            """.trimIndent(),
        )

        assertEquals(result.diagnostics, ExitCode.OK, result.exitCode)
    }

    @Test
    fun externalAndroidFrontendCannotCompileAgainstPlaybackImplementations() {
        val forbiddenTypes = listOf(
            "at.bernhardberger.tvhplayer.player.Media3PlaybackRuntime",
            "at.bernhardberger.tvhplayer.player.htsp.HtspSubscriptionDataSource",
            "at.bernhardberger.tvhplayer.player.htsp.HtspRecordingDataSource",
            "at.bernhardberger.tvhplayer.player.htsp.HtspSubscriptionExtractor",
            "at.bernhardberger.tvhplayer.player.htsp.HtspDataSourceInterface",
            "at.bernhardberger.tvhplayer.player.htsp.HtspFramedCodec",
            "at.bernhardberger.tvhplayer.player.htsp.LegacyRenderer",
            "at.bernhardberger.tvhplayer.player.htsp.TvheadendExtractorsFactory",
            "at.bernhardberger.tvhplayer.player.htsp.reader.StreamReader",
            "at.bernhardberger.tvhplayer.player.htsp.reader.StreamReadersFactory",
        )

        forbiddenTypes.forEachIndexed { index, qualifiedName ->
            val simpleName = qualifiedName.substringAfterLast('.')
            val result = compileExternalConsumer(
                """
                    package external.consumer

                    import $qualifiedName

                    class ForbiddenPlaybackApi$index(val implementation: $simpleName)
                """.trimIndent(),
                sourceName = "ForbiddenPlaybackApi$index.kt",
            )

            assertEquals(result.diagnostics, ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.diagnostics, result.diagnostics.contains(simpleName))
            assertTrue(
                result.diagnostics,
                result.diagnostics.contains("internal", ignoreCase = true) ||
                    result.diagnostics.contains("cannot access", ignoreCase = true),
            )
        }
    }

    private fun compileExternalConsumer(
        sourceText: String,
        sourceName: String = "SupportedPlaybackApi.kt",
    ): CompilationResult {
        val source = temporaryFolder.newFile(sourceName).apply { writeText(sourceText) }
        val outputDirectory = temporaryFolder.newFolder("classes-${sourceName.substringBefore('.')}")
        val diagnostics = ByteArrayOutputStream()
        val exitCode = PrintStream(diagnostics).use { output ->
            K2JVMCompiler().exec(
                output,
                "-classpath",
                System.getProperty("java.class.path"),
                "-module-name",
                "external-playback-consumer",
                "-d",
                outputDirectory.absolutePath,
                source.absolutePath,
            )
        }
        return CompilationResult(
            exitCode = exitCode,
            diagnostics = diagnostics.toString(Charsets.UTF_8),
        )
    }

    private data class CompilationResult(
        val exitCode: ExitCode,
        val diagnostics: String,
    )
}
