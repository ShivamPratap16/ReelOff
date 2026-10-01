package app.reeloff.ui

import app.reeloff.core.RuleCatalog
import app.reeloff.data.Settings

/** An immutable copy of everything the screen shows, rebuilt whenever a preference changes. */
data class UiState(
    val disclosureAccepted: Boolean,
    val serviceEnabled: Boolean,
    val pausedUntil: Long,
    val enabledRules: Set<String>,
    val allowanceMinutes: Map<String, Int>,
    val allowanceUsedMillis: Map<String, Long>,
    val scrollEnabled: Boolean,
    val scrollPackages: Set<String>,
    val swipesPerSession: Int,
    val scrollCooldownMinutes: Int,
    val pauseWaitSeconds: Int,
    val blocksToday: Int,
    val blocksWeek: List<Int>,
    val blocksTotal: Int,
    val blocksByRule: Map<String, Int>,
) {
    val minutesSaved: Int get() = blocksTotal * Settings.MINUTES_SAVED_PER_BLOCK

    companion object {
        fun from(settings: Settings, serviceEnabled: Boolean): UiState {
            val today = Settings.today()
            val (allowanceDay, used) = settings.loadAllowance()
            val week = settings.blocksLastDays(7, today)
            return UiState(
                disclosureAccepted = settings.disclosureAccepted,
                serviceEnabled = serviceEnabled,
                pausedUntil = settings.pauseState.pausedUntil,
                enabledRules = settings.enabledRuleIds,
                allowanceMinutes = RuleCatalog.ALL.associate { it.id to settings.allowanceMinutes(it.id) },
                allowanceUsedMillis = if (allowanceDay == today) used else emptyMap(),
                scrollEnabled = settings.scrollLimitEnabled,
                scrollPackages = settings.scrollPackages,
                swipesPerSession = settings.swipesPerSession,
                scrollCooldownMinutes = settings.scrollCooldownMinutes,
                pauseWaitSeconds = settings.pauseWaitSeconds,
                blocksToday = week.last(),
                blocksWeek = week,
                blocksTotal = settings.totalBlocks,
                blocksByRule = RuleCatalog.ALL.associate { it.id to settings.blocksForRule(it.id) },
            )
        }
    }
}
