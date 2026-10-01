package app.reeloff.core

enum class BlockAction { BACK, HOME }

data class EngineConfig(
    val enabledRuleIds: Set<String> = RuleCatalog.DEFAULT_ENABLED_IDS,
    /** Daily allowance per rule in millis; missing or 0 means block immediately. */
    val allowanceMillis: Map<String, Long> = emptyMap(),
    val scrollLimit: ScrollLimitConfig = ScrollLimitConfig(),
    val pausedUntil: Long = 0,
)

sealed interface Decision {
    data object None : Decision

    /** Short-form content is on screen but still inside today's allowance. */
    data class Allowed(val ruleId: String, val usedMillis: Long, val allowanceMillis: Long) : Decision

    data class Block(
        val reason: Reason,
        val ruleId: String,
        val title: String,
        val action: BlockAction,
    ) : Decision

    enum class Reason { SHORT_FORM, ALLOWANCE_USED_UP, SCROLL_LIMIT }
}

/**
 * Turns screens and scroll events into decisions. Pure Kotlin with time passed in, so the whole
 * blocking behaviour is covered by JVM unit tests.
 */
class BlockEngine(
    private val rules: List<BlockRule> = RuleCatalog.ALL,
    private val allowance: AllowanceTracker = AllowanceTracker(),
    private val scrollLimiter: ScrollLimiter = ScrollLimiter(),
    private val escalation: EscalationGuard = EscalationGuard(),
) {
    /** Packages worth inspecting for [config] - everything else is ignored without reading it. */
    fun watchedPackages(config: EngineConfig): Set<String> =
        rules.filter { it.id in config.enabledRuleIds }.flatMap { it.packages }.toSet() +
            (if (config.scrollLimit.enabled) config.scrollLimit.packages else emptySet())

    fun matchingRule(snapshot: ScreenSnapshot, config: EngineConfig): BlockRule? =
        rules.firstOrNull { it.id in config.enabledRuleIds && it.matches(snapshot) }

    fun onScreen(snapshot: ScreenSnapshot, config: EngineConfig, now: Long, today: Long): Decision {
        if (now < config.pausedUntil) return Decision.None

        // A feed in cool-down stays closed even when no scroll happens (e.g. reopening the app).
        val lockedUntil = scrollLimiter.lockedUntil(snapshot.packageName, now)
        if (lockedUntil != null && config.scrollLimit.enabled && snapshot.packageName in config.scrollLimit.packages) {
            return block(Decision.Reason.SCROLL_LIMIT, snapshot.packageName, appTitle(snapshot.packageName), now, preferHome = true)
        }

        val rule = matchingRule(snapshot, config) ?: return Decision.None
        val budget = config.allowanceMillis[rule.id] ?: 0L
        if (budget > 0) {
            val used = allowance.onSeen(rule.id, now, today)
            if (used < budget) return Decision.Allowed(rule.id, used, budget)
            allowance.endSession(rule.id)
            return block(Decision.Reason.ALLOWANCE_USED_UP, rule.id, rule.title, now, rule.mode == BlockMode.WHOLE_APP)
        }
        return block(Decision.Reason.SHORT_FORM, rule.id, rule.title, now, rule.mode == BlockMode.WHOLE_APP)
    }

    fun onScroll(pkg: String, containerFraction: Float, config: EngineConfig, now: Long): Decision {
        if (now < config.pausedUntil) return Decision.None
        return when (scrollLimiter.onScroll(pkg, containerFraction, now, config.scrollLimit)) {
            is ScrollLimiter.Result.Locked -> block(Decision.Reason.SCROLL_LIMIT, pkg, appTitle(pkg), now, preferHome = true)
            else -> Decision.None
        }
    }

    fun allowanceUsedMillis(ruleId: String, today: Long): Long = allowance.usedMillis(ruleId, today)

    fun exportAllowance(): Pair<Long, Map<String, Long>> = allowance.currentDay to allowance.export()

    fun restoreAllowance(day: Long, used: Map<String, Long>) = allowance.restore(day, used)

    fun scrollLockedUntil(pkg: String, now: Long): Long? = scrollLimiter.lockedUntil(pkg, now)

    private fun block(reason: Decision.Reason, id: String, title: String, now: Long, preferHome: Boolean): Decision {
        val action = escalation.nextAction(now, preferHome) ?: return Decision.None
        return Decision.Block(reason, id, title, action)
    }

    companion object {
        val FEED_APPS: Map<String, String> = linkedMapOf(
            "com.instagram.android" to "Instagram",
            "com.facebook.katana" to "Facebook",
            "com.twitter.android" to "X (Twitter)",
            "com.reddit.frontpage" to "Reddit",
            "com.instagram.barcelona" to "Threads",
            "com.linkedin.android" to "LinkedIn",
            "com.pinterest" to "Pinterest",
            "com.google.android.youtube" to "YouTube",
            "com.snapchat.android" to "Snapchat",
            "com.sharechat.app" to "ShareChat",
        )

        fun appTitle(pkg: String): String = FEED_APPS[pkg] ?: pkg
    }
}
