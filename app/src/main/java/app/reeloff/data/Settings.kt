package app.reeloff.data

import android.content.Context
import android.content.SharedPreferences
import app.reeloff.core.EngineConfig
import app.reeloff.core.PauseState
import app.reeloff.core.RuleCatalog
import app.reeloff.core.ScrollLimitConfig
import java.time.LocalDate

/**
 * All user settings and counters, in one SharedPreferences file. The accessibility service and
 * the UI run in the same process, so a preference listener is enough to keep them in sync.
 */
class Settings private constructor(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("reeloff", Context.MODE_PRIVATE)

    // --- Blocking rules -------------------------------------------------------------------------

    var enabledRuleIds: Set<String>
        get() = prefs.getStringSet(KEY_RULES, null)?.toSet() ?: RuleCatalog.DEFAULT_ENABLED_IDS
        set(value) = prefs.edit().putStringSet(KEY_RULES, value).apply()

    fun setRuleEnabled(id: String, enabled: Boolean) {
        enabledRuleIds = if (enabled) enabledRuleIds + id else enabledRuleIds - id
    }

    /** Daily allowance in minutes; 0 means block immediately. */
    fun allowanceMinutes(ruleId: String): Int = prefs.getInt(KEY_ALLOWANCE + ruleId, 0)

    fun setAllowanceMinutes(ruleId: String, minutes: Int) =
        prefs.edit().putInt(KEY_ALLOWANCE + ruleId, minutes).apply()

    // --- Endless scroll limiter -----------------------------------------------------------------

    var scrollLimitEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCROLL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SCROLL_ENABLED, value).apply()

    var scrollPackages: Set<String>
        get() = prefs.getStringSet(KEY_SCROLL_PKGS, null)?.toSet() ?: DEFAULT_SCROLL_PACKAGES
        set(value) = prefs.edit().putStringSet(KEY_SCROLL_PKGS, value).apply()

    var swipesPerSession: Int
        get() = prefs.getInt(KEY_SCROLL_SWIPES, 40)
        set(value) = prefs.edit().putInt(KEY_SCROLL_SWIPES, value).apply()

    var scrollCooldownMinutes: Int
        get() = prefs.getInt(KEY_SCROLL_COOLDOWN, 15)
        set(value) = prefs.edit().putInt(KEY_SCROLL_COOLDOWN, value).apply()

    // --- Pause with friction --------------------------------------------------------------------

    /** Seconds the user must wait before a pause is granted. */
    var pauseWaitSeconds: Int
        get() = prefs.getInt(KEY_PAUSE_WAIT, 30)
        set(value) = prefs.edit().putInt(KEY_PAUSE_WAIT, value).apply()

    var pauseState: PauseState
        get() = PauseState(
            requestedAt = prefs.getLong(KEY_PAUSE_REQUESTED, -1).takeIf { it >= 0 },
            pausedUntil = prefs.getLong(KEY_PAUSED_UNTIL, 0),
        )
        set(value) = prefs.edit()
            .putLong(KEY_PAUSE_REQUESTED, value.requestedAt ?: -1)
            .putLong(KEY_PAUSED_UNTIL, value.pausedUntil)
            .apply()

    // --- Onboarding -----------------------------------------------------------------------------

    var disclosureAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLOSURE, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLOSURE, value).apply()

    // --- Engine config --------------------------------------------------------------------------

    fun engineConfig(): EngineConfig = EngineConfig(
        enabledRuleIds = enabledRuleIds,
        allowanceMillis = RuleCatalog.ALL.associate { it.id to allowanceMinutes(it.id) * 60_000L },
        scrollLimit = ScrollLimitConfig(
            enabled = scrollLimitEnabled,
            packages = scrollPackages,
            swipesPerSession = swipesPerSession,
            cooldownMillis = scrollCooldownMinutes * 60_000L,
        ),
        pausedUntil = pauseState.pausedUntil,
    )

    // --- Allowance persistence (so a service restart does not hand out a fresh budget) ----------

    fun saveAllowance(day: Long, used: Map<String, Long>) {
        val encoded = used.entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs.edit().putLong(KEY_ALLOWANCE_DAY, day).putString(KEY_ALLOWANCE_USED, encoded).apply()
    }

    fun loadAllowance(): Pair<Long, Map<String, Long>> {
        val day = prefs.getLong(KEY_ALLOWANCE_DAY, Long.MIN_VALUE)
        val used = prefs.getString(KEY_ALLOWANCE_USED, "").orEmpty()
            .split(';')
            .mapNotNull { entry ->
                val (k, v) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                v.toLongOrNull()?.let { k to it }
            }
            .toMap()
        return day to used
    }

    // --- Stats ----------------------------------------------------------------------------------

    fun recordBlock(id: String, day: Long = today()) {
        prefs.edit()
            .putInt(KEY_DAY + day, prefs.getInt(KEY_DAY + day, 0) + 1)
            .putInt(KEY_BY_RULE + id, prefs.getInt(KEY_BY_RULE + id, 0) + 1)
            .putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            .apply()
        pruneOldDays(day)
    }

    fun blocksOn(day: Long): Int = prefs.getInt(KEY_DAY + day, 0)

    fun blocksLastDays(days: Int, today: Long = today()): List<Int> =
        (days - 1 downTo 0).map { blocksOn(today - it) }

    val totalBlocks: Int get() = prefs.getInt(KEY_TOTAL, 0)

    fun blocksForRule(id: String): Int = prefs.getInt(KEY_BY_RULE + id, 0)

    private fun pruneOldDays(today: Long) {
        val stale = prefs.all.keys.filter { key ->
            key.startsWith(KEY_DAY) && (key.removePrefix(KEY_DAY).toLongOrNull() ?: today) < today - 60
        }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach(::remove) }.apply()
    }

    companion object {
        private const val KEY_RULES = "enabled_rules"
        private const val KEY_ALLOWANCE = "allowance_min_"
        private const val KEY_SCROLL_ENABLED = "scroll_enabled"
        private const val KEY_SCROLL_PKGS = "scroll_packages"
        private const val KEY_SCROLL_SWIPES = "scroll_swipes"
        private const val KEY_SCROLL_COOLDOWN = "scroll_cooldown_min"
        private const val KEY_PAUSE_WAIT = "pause_wait_sec"
        private const val KEY_PAUSE_REQUESTED = "pause_requested_at"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val KEY_DISCLOSURE = "disclosure_accepted"
        private const val KEY_ALLOWANCE_DAY = "allowance_day"
        private const val KEY_ALLOWANCE_USED = "allowance_used"
        private const val KEY_DAY = "blocks_day_"
        private const val KEY_BY_RULE = "blocks_rule_"
        private const val KEY_TOTAL = "blocks_total"

        val DEFAULT_SCROLL_PACKAGES = setOf("com.instagram.android", "com.twitter.android", "com.reddit.frontpage")

        /**
         * Rough, deliberately conservative estimate of time saved per interception. Each Short or
         * Reel is under a minute, but people who open one typically watch several in a row.
         */
        const val MINUTES_SAVED_PER_BLOCK = 3

        fun today(): Long = LocalDate.now().toEpochDay()

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) { instance ?: Settings(context).also { instance = it } }
    }
}
