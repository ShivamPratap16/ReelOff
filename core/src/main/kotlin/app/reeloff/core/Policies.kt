package app.reeloff.core

/**
 * Optional daily allowance per rule ("5 minutes of Shorts a day"). Going cold turkey is not for
 * everyone; a small, conscious budget is what most users of one sec / ScreenZen ask for.
 *
 * Time is counted from consecutive detections of the same rule. A gap longer than [maxGapMillis]
 * means the user left, so the gap is not charged.
 */
class AllowanceTracker(private val maxGapMillis: Long = 5_000) {
    private var day: Long = Long.MIN_VALUE
    private val usedMillis = HashMap<String, Long>()
    private val lastSeen = HashMap<String, Long>()

    /** Records that [ruleId] is on screen at [now] and returns the total used today in millis. */
    fun onSeen(ruleId: String, now: Long, today: Long): Long {
        rollDay(today)
        val previous = lastSeen[ruleId]
        if (previous != null) {
            val gap = now - previous
            if (gap in 1..maxGapMillis) usedMillis[ruleId] = (usedMillis[ruleId] ?: 0L) + gap
        }
        lastSeen[ruleId] = now
        return usedMillis[ruleId] ?: 0L
    }

    fun usedMillis(ruleId: String, today: Long): Long {
        rollDay(today)
        return usedMillis[ruleId] ?: 0L
    }

    /** Forget the open viewing session (call after blocking, so the next visit starts fresh). */
    fun endSession(ruleId: String) {
        lastSeen.remove(ruleId)
    }

    fun export(): Map<String, Long> = HashMap(usedMillis)

    fun restore(today: Long, used: Map<String, Long>) {
        day = today
        usedMillis.clear()
        usedMillis.putAll(used)
        lastSeen.clear()
    }

    val currentDay: Long get() = day

    private fun rollDay(today: Long) {
        if (today != day) {
            day = today
            usedMillis.clear()
            lastSeen.clear()
        }
    }
}

data class ScrollLimitConfig(
    val enabled: Boolean = false,
    /** Packages whose feeds are limited (Instagram home, X, Reddit, Facebook...). */
    val packages: Set<String> = emptySet(),
    /** Swipes allowed per sitting. One swipe is roughly one screen of feed. */
    val swipesPerSession: Int = 40,
    /** How long the feed stays closed once the budget is spent. */
    val cooldownMillis: Long = 15 * 60_000L,
    /** Leaving the feed alone this long starts a fresh sitting with a full budget. */
    val sessionResetMillis: Long = 10 * 60_000L,
)

/**
 * Endless-scroll limiter. Infinite feeds have no stopping cue; this adds one. Scroll events arrive
 * dozens of times per fling, so events closer than [swipeDebounceMillis] count as one swipe, and
 * only scrolls of large containers (the feed itself, not a carousel or a comment sheet) count.
 */
class ScrollLimiter(
    private val swipeDebounceMillis: Long = 600,
    private val minContainerFraction: Float = 0.5f,
) {
    private class State(var swipes: Int = 0, var lastScroll: Long = Long.MIN_VALUE / 2, var lockedUntil: Long = 0)

    private val states = HashMap<String, State>()

    sealed interface Result {
        data object Ignored : Result
        data class Counted(val used: Int, val budget: Int) : Result
        data class Locked(val until: Long) : Result
    }

    fun onScroll(pkg: String, containerFraction: Float, now: Long, config: ScrollLimitConfig): Result {
        if (!config.enabled || pkg !in config.packages) return Result.Ignored
        val state = states.getOrPut(pkg) { State() }
        if (now < state.lockedUntil) return Result.Locked(state.lockedUntil)
        if (containerFraction < minContainerFraction) return Result.Ignored

        if (now - state.lastScroll > config.sessionResetMillis) state.swipes = 0
        if (now - state.lastScroll >= swipeDebounceMillis) state.swipes++
        state.lastScroll = now

        if (state.swipes > config.swipesPerSession) {
            state.lockedUntil = now + config.cooldownMillis
            state.swipes = 0
            return Result.Locked(state.lockedUntil)
        }
        return Result.Counted(state.swipes, config.swipesPerSession)
    }

    /** True while [pkg]'s feed is in its cool-down. */
    fun lockedUntil(pkg: String, now: Long): Long? =
        states[pkg]?.lockedUntil?.takeIf { it > now }

    fun reset() = states.clear()
}

/**
 * Pressing Back is the gentlest exit: it returns to where you were. But some screens (a Shorts
 * link opened from another app, a deep link into Reels) just reload on Back. If Back has already
 * failed [maxBacks] times within [windowMillis], escalate to the home screen.
 */
class EscalationGuard(
    private val maxBacks: Int = 2,
    private val windowMillis: Long = 4_000,
    private val debounceMillis: Long = 700,
) {
    private val recent = ArrayDeque<Long>()
    private var lastActionAt = Long.MIN_VALUE / 2

    /** Returns the action to take now, or null if an action was just taken and we should wait. */
    fun nextAction(now: Long, preferHome: Boolean): BlockAction? {
        if (now - lastActionAt < debounceMillis) return null
        lastActionAt = now
        while (recent.isNotEmpty() && now - recent.first() > windowMillis) recent.removeFirst()
        if (preferHome) {
            recent.clear()
            return BlockAction.HOME
        }
        recent.addLast(now)
        return if (recent.size > maxBacks) {
            recent.clear()
            BlockAction.HOME
        } else {
            BlockAction.BACK
        }
    }
}

/**
 * Turning protection off on impulse is the classic way blockers fail. Pausing therefore takes a
 * deliberate wait (the "one sec" idea, but longer), and a pause always ends on its own.
 */
data class PauseState(
    /** When a pause request was started; the pause is granted once the wait has elapsed. */
    val requestedAt: Long? = null,
    val pausedUntil: Long = 0,
) {
    fun isPaused(now: Long): Boolean = now < pausedUntil

    fun waitRemaining(now: Long, waitMillis: Long): Long =
        requestedAt?.let { (it + waitMillis - now).coerceAtLeast(0) } ?: waitMillis

    fun request(now: Long): PauseState = copy(requestedAt = now)

    fun cancel(): PauseState = copy(requestedAt = null)

    /**
     * Grants the pause if the wait is over. Returns this state unchanged when it is not, so the UI
     * cannot skip the countdown by calling confirm early.
     */
    fun confirm(now: Long, waitMillis: Long, pauseMillis: Long): PauseState {
        val started = requestedAt ?: return this
        if (now - started < waitMillis) return this
        return PauseState(requestedAt = null, pausedUntil = now + pauseMillis)
    }

    fun resume(): PauseState = PauseState()
}
