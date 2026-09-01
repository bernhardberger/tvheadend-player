package at.bernhardberger.tvhplayer

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ExternalTargetAcceptanceRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                Assume.assumeTrue(
                    "${description.displayName} requires an explicitly admitted external target",
                    InstrumentationRegistry.getArguments().getString(ARGUMENT) == ENABLED_VALUE,
                )
                base.evaluate()
            }
        }

    private companion object {
        const val ARGUMENT = "tvhplayer.externalTargetAcceptance"
        const val ENABLED_VALUE = "enabled"
    }
}
