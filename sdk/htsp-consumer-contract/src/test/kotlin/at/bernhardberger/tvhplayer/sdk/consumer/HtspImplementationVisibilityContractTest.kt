package at.bernhardberger.tvhplayer.sdk.consumer

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HtspImplementationVisibilityContractTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun externalKotlinConsumerCanCompileAgainstTheFrontendFacade() {
        val result = compileExternalConsumer(
            """
                package external.consumer

                import at.bernhardberger.tvhplayer.htsp.ChannelEpgRuntime
                import at.bernhardberger.tvhplayer.htsp.DvrRuntime
                import at.bernhardberger.tvhplayer.htsp.TvheadendClient

                class AllowedApi(
                    val client: TvheadendClient,
                    val channels: ChannelEpgRuntime,
                    val dvr: DvrRuntime,
                )
            """.trimIndent()
        )

        assertEquals(result.diagnostics, ExitCode.OK, result.exitCode)
    }

    @Test
    fun externalKotlinConsumerCannotCompileAgainstImplementationTypes() {
        val forbiddenTypes = listOf(
            "at.bernhardberger.tvhplayer.htsp.HtspService",
            "at.bernhardberger.tvhplayer.htsp.HtspConnectionProbe",
            "at.bernhardberger.tvhplayer.htsp.HtspCodec",
            "at.bernhardberger.tvhplayer.htsp.HtspMessage",
            "at.bernhardberger.tvhplayer.htsp.HtspEvent",
            "at.bernhardberger.tvhplayer.htsp.HtspMuxEvent",
            "at.bernhardberger.tvhplayer.htsp.PlaybackHtspTransport",
            "at.bernhardberger.tvhplayer.repositories.TvhRepository",
            "at.bernhardberger.tvhplayer.repositories.DvrRepository",
            "at.bernhardberger.tvhplayer.repositories.EpgRuntimeTimings",
        )

        forbiddenTypes.forEachIndexed { index, qualifiedName ->
            val simpleName = qualifiedName.substringAfterLast('.')
            val result = compileExternalConsumer(
                """
                    package external.consumer

                    import $qualifiedName

                    class ForbiddenApi$index(val implementation: $simpleName)
                """.trimIndent(),
                sourceName = "ForbiddenApi$index.kt",
            )

            assertEquals(result.diagnostics, ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.diagnostics, result.diagnostics.contains(simpleName))
            assertTrue(
                result.diagnostics,
                result.diagnostics.contains("internal", ignoreCase = true) ||
                    result.diagnostics.contains("cannot access", ignoreCase = true) ||
                    result.diagnostics.contains("opt-in", ignoreCase = true) ||
                    result.diagnostics.contains("playback integration", ignoreCase = true),
            )
        }
    }

    @Test
    fun playbackIntegrationTypesRequireAnExplicitErrorLevelOptIn() {
        val result = compileExternalConsumer(
            """
                @file:OptIn(
                    at.bernhardberger.tvhplayer.htsp.PlaybackIntegrationApi::class,
                )

                package external.consumer

                import at.bernhardberger.tvhplayer.htsp.HtspMessage
                import at.bernhardberger.tvhplayer.htsp.PlaybackHtspTransport

                class IntegratedPlaybackApi(
                    client: at.bernhardberger.tvhplayer.htsp.TvheadendClient,
                    val transport: PlaybackHtspTransport,
                    val message: HtspMessage,
                ) {
                    val clientTransport: PlaybackHtspTransport = client.playbackTransport
                }
            """.trimIndent(),
            sourceName = "IntegratedPlaybackApi.kt",
        )

        assertEquals(result.diagnostics, ExitCode.OK, result.exitCode)
    }

    @Test
    fun frontendCannotReachTheClientPlaybackTransportWithoutOptIn() {
        val result = compileExternalConsumer(
            """
                package external.consumer

                import at.bernhardberger.tvhplayer.htsp.TvheadendClient

                class ForbiddenClientTransport(client: TvheadendClient) {
                    val transport = client.playbackTransport
                }
            """.trimIndent(),
            sourceName = "ForbiddenClientTransport.kt",
        )

        assertEquals(result.diagnostics, ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            result.diagnostics,
            result.diagnostics.contains("playback integration", ignoreCase = true),
        )
    }

    private fun compileExternalConsumer(
        sourceText: String,
        sourceName: String = "AllowedApi.kt",
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
                "external-htsp-consumer",
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
