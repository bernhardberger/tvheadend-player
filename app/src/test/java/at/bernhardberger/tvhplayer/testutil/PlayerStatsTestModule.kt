package at.bernhardberger.tvhplayer.testutil

import at.bernhardberger.tvhplayer.di.PlayerStatsStore
import org.koin.dsl.module

/** A fresh process-scoped switch for each isolated app-root test graph. */
fun playerStatsTestModule() = module {
    single { PlayerStatsStore() }
}
