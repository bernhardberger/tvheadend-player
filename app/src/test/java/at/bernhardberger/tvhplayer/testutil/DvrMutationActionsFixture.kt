package at.bernhardberger.tvhplayer.testutil

import at.bernhardberger.tvheadend.sdk.core.AutorecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleUpdate
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryUpdate
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleId
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleUpdate
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions

@OptIn(ExperimentalTvheadendApi::class)
internal fun testDvrMutationActions(
    scheduleEntry: suspend (CurrentSessionObservation, DvrScheduleRequest) -> DvrMutationResult<DvrEntryId>,
    stopEntry: suspend (CurrentSessionObservation, DvrEntryId) -> DvrMutationResult<Unit>,
    cancelEntry: suspend (CurrentSessionObservation, DvrEntryId) -> DvrMutationResult<Unit>,
    deleteEntry: suspend (CurrentSessionObservation, DvrEntryId) -> DvrMutationResult<Unit>,
    updateWatchStateCommand: suspend (CurrentSessionObservation, DvrEntryId, Boolean) -> DvrProgressResult,
    createAutorecRule: suspend (CurrentSessionObservation, AutorecRuleCreate) -> DvrMutationResult<AutorecRuleId> =
        { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    updateAutorecRule: suspend (
        CurrentSessionObservation, AutorecRuleId, AutorecRuleUpdate,
    ) -> DvrMutationResult<Unit> =
        { _, _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    deleteAutorecRule: suspend (CurrentSessionObservation, AutorecRuleId) -> DvrMutationResult<Unit> =
        { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    createTimerecRule: suspend (CurrentSessionObservation, TimerecRuleCreate) -> DvrMutationResult<TimerecRuleId> =
        { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    updateTimerecRule: suspend (
        CurrentSessionObservation, TimerecRuleId, TimerecRuleUpdate,
    ) -> DvrMutationResult<Unit> =
        { _, _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    deleteTimerecRule: suspend (CurrentSessionObservation, TimerecRuleId) -> DvrMutationResult<Unit> =
        { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    updateEntry: suspend (CurrentSessionObservation, DvrEntryId, DvrEntryUpdate) -> DvrMutationResult<Unit> =
        { _, _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
): DvrMutationActions = DvrMutationActions(
    scheduleEntry, stopEntry, cancelEntry, deleteEntry, updateWatchStateCommand,
    createAutorecRule, updateAutorecRule, deleteAutorecRule,
    createTimerecRule, updateTimerecRule, deleteTimerecRule, updateEntry,
)
