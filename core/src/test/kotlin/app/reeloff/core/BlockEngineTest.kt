package app.reeloff.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockEngineTest {

    private val shorts = ScreenSnapshot(
        "com.google.android.youtube",
        listOf(NodeInfo("reel_player_page_container", screenFraction = 1f)),
    )
    private val ytHome = ScreenSnapshot("com.google.android.youtube", selectedLabels = setOf("Home"))
    private val day = 20_000L

    @Test
    fun shortFormIsBlockedWithBack() {
        val d = BlockEngine().onScreen(shorts, EngineConfig(), now = 10_000, today = day)
        assertIs<Decision.Block>(d)
        assertEquals(BlockAction.BACK, d.action)
        assertEquals(Decision.Reason.SHORT_FORM, d.reason)
    }

    @Test
    fun disabledRuleDoesNothing() {
        val config = EngineConfig(enabledRuleIds = setOf("instagram_reels"))
        assertEquals(Decision.None, BlockEngine().onScreen(shorts, config, 10_000, day))
    }

    @Test
    fun normalScreenDoesNothing() {
        assertEquals(Decision.None, BlockEngine().onScreen(ytHome, EngineConfig(), 10_000, day))
    }

    @Test
    fun wholeAppRuleGoesHome() {
        val d = BlockEngine().onScreen(ScreenSnapshot("com.zhiliaoapp.musically"), EngineConfig(), 10_000, day)
        assertIs<Decision.Block>(d)
        assertEquals(BlockAction.HOME, d.action)
    }

    @Test
    fun pausedEngineDoesNothing() {
        val config = EngineConfig(pausedUntil = 20_000)
        assertEquals(Decision.None, BlockEngine().onScreen(shorts, config, 10_000, day))
        assertIs<Decision.Block>(BlockEngine().onScreen(shorts, config, 20_000, day))
    }

    @Test
    fun repeatedEventsAreDebounced() {
        val engine = BlockEngine()
        assertIs<Decision.Block>(engine.onScreen(shorts, EngineConfig(), 10_000, day))
        assertEquals(Decision.None, engine.onScreen(shorts, EngineConfig(), 10_200, day))
    }

    @Test
    fun backThatKeepsFailingEscalatesToHome() {
        val engine = BlockEngine()
        val actions = listOf(10_000L, 11_000L, 12_000L).map {
            (engine.onScreen(shorts, EngineConfig(), it, day) as Decision.Block).action
        }
        assertEquals(listOf(BlockAction.BACK, BlockAction.BACK, BlockAction.HOME), actions)
        // After a quiet spell Back is tried again.
        val later = engine.onScreen(shorts, EngineConfig(), 30_000, day) as Decision.Block
        assertEquals(BlockAction.BACK, later.action)
    }

    @Test
    fun dailyAllowanceLetsYouWatchThenBlocks() {
        val engine = BlockEngine()
        val config = EngineConfig(allowanceMillis = mapOf("youtube_shorts" to 10_000L))
        var now = 0L
        var decision: Decision = Decision.None
        repeat(10) {
            now += 1_000
            decision = engine.onScreen(shorts, config, now, day)
            assertIs<Decision.Allowed>(decision)
        }
        now += 1_000
        decision = engine.onScreen(shorts, config, now, day)
        assertIs<Decision.Block>(decision)
        assertEquals(Decision.Reason.ALLOWANCE_USED_UP, decision.reason)
    }

    @Test
    fun allowanceDoesNotChargeTimeAwayAndResetsDaily() {
        val engine = BlockEngine()
        val config = EngineConfig(allowanceMillis = mapOf("youtube_shorts" to 60_000L))
        engine.onScreen(shorts, config, 1_000, day)
        engine.onScreen(shorts, config, 3_000, day)
        // An hour away is not counted.
        engine.onScreen(shorts, config, 3_600_000, day)
        assertEquals(2_000, engine.allowanceUsedMillis("youtube_shorts", day))
        assertEquals(0, engine.allowanceUsedMillis("youtube_shorts", day + 1))
    }

    @Test
    fun allowanceSurvivesExportRestore() {
        val engine = BlockEngine()
        val config = EngineConfig(allowanceMillis = mapOf("youtube_shorts" to 60_000L))
        engine.onScreen(shorts, config, 1_000, day)
        engine.onScreen(shorts, config, 4_000, day)
        val (savedDay, saved) = engine.exportAllowance()
        val restored = BlockEngine().apply { restoreAllowance(savedDay, saved) }
        assertEquals(3_000, restored.allowanceUsedMillis("youtube_shorts", day))
    }

    @Test
    fun watchedPackagesFollowConfig() {
        val engine = BlockEngine()
        val pkgs = engine.watchedPackages(EngineConfig(enabledRuleIds = setOf("tiktok")))
        assertTrue("com.zhiliaoapp.musically" in pkgs)
        assertFalse("com.google.android.youtube" in pkgs)
        val withScroll = engine.watchedPackages(
            EngineConfig(enabledRuleIds = emptySet(), scrollLimit = ScrollLimitConfig(true, setOf("com.reddit.frontpage"))),
        )
        assertEquals(setOf("com.reddit.frontpage"), withScroll)
    }
}

class ScrollLimiterTest {
    private val reddit = "com.reddit.frontpage"
    private val config = ScrollLimitConfig(
        enabled = true,
        packages = setOf(reddit),
        swipesPerSession = 3,
        cooldownMillis = 60_000,
        sessionResetMillis = 30_000,
    )

    @Test
    fun budgetThenLock() {
        val limiter = ScrollLimiter(swipeDebounceMillis = 500)
        assertEquals(ScrollLimiter.Result.Counted(1, 3), limiter.onScroll(reddit, 0.9f, 1_000, config))
        // Same fling: not a new swipe.
        assertEquals(ScrollLimiter.Result.Counted(1, 3), limiter.onScroll(reddit, 0.9f, 1_100, config))
        limiter.onScroll(reddit, 0.9f, 2_000, config)
        limiter.onScroll(reddit, 0.9f, 3_000, config)
        val locked = limiter.onScroll(reddit, 0.9f, 4_000, config)
        assertEquals(ScrollLimiter.Result.Locked(64_000), locked)
        assertEquals(ScrollLimiter.Result.Locked(64_000), limiter.onScroll(reddit, 0.9f, 10_000, config))
        // After the cool-down a fresh budget.
        assertEquals(ScrollLimiter.Result.Counted(1, 3), limiter.onScroll(reddit, 0.9f, 64_001, config))
    }

    @Test
    fun smallContainersAndOtherAppsAreIgnored() {
        val limiter = ScrollLimiter()
        assertEquals(ScrollLimiter.Result.Ignored, limiter.onScroll(reddit, 0.2f, 1_000, config))
        assertEquals(ScrollLimiter.Result.Ignored, limiter.onScroll("com.whatsapp", 1f, 1_000, config))
        assertEquals(ScrollLimiter.Result.Ignored, limiter.onScroll(reddit, 1f, 1_000, config.copy(enabled = false)))
    }

    @Test
    fun breakResetsBudget() {
        val limiter = ScrollLimiter(swipeDebounceMillis = 500)
        limiter.onScroll(reddit, 1f, 1_000, config)
        limiter.onScroll(reddit, 1f, 2_000, config)
        limiter.onScroll(reddit, 1f, 3_000, config)
        assertEquals(ScrollLimiter.Result.Counted(1, 3), limiter.onScroll(reddit, 1f, 40_000, config))
    }

    @Test
    fun lockedFeedIsClosedOnReopen() {
        val engine = BlockEngine()
        val engineConfig = EngineConfig(enabledRuleIds = emptySet(), scrollLimit = config)
        listOf(1_000L, 2_000L, 3_000L).forEach { engine.onScroll(reddit, 1f, engineConfig, it) }
        assertIs<Decision.Block>(engine.onScroll(reddit, 1f, engineConfig, 4_000))
        val reopen = engine.onScreen(ScreenSnapshot(reddit), engineConfig, 20_000, 1)
        assertIs<Decision.Block>(reopen)
        assertEquals(Decision.Reason.SCROLL_LIMIT, reopen.reason)
        assertEquals(BlockAction.HOME, reopen.action)
    }
}

class PauseStateTest {
    @Test
    fun pauseRequiresWaiting() {
        val wait = 30_000L
        val requested = PauseState().request(now = 0)
        assertEquals(wait, requested.waitRemaining(0, wait))
        // Confirming early is refused.
        val early = requested.confirm(now = 10_000, waitMillis = wait, pauseMillis = 60_000)
        assertFalse(early.isPaused(10_000))
        val granted = requested.confirm(now = 30_000, waitMillis = wait, pauseMillis = 60_000)
        assertTrue(granted.isPaused(30_000))
        assertFalse(granted.isPaused(90_000))
    }

    @Test
    fun confirmWithoutRequestDoesNothing() {
        assertFalse(PauseState().confirm(1_000_000, 0, 60_000).isPaused(1_000_000))
    }

    @Test
    fun resumeEndsPause() {
        val paused = PauseState().request(0).confirm(0, 0, 60_000)
        assertTrue(paused.isPaused(1))
        assertFalse(paused.resume().isPaused(1))
    }
}
