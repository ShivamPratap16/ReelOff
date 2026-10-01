package app.reeloff.service

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import app.reeloff.core.BlockAction
import app.reeloff.core.BlockEngine
import app.reeloff.core.Decision
import app.reeloff.core.EngineConfig
import app.reeloff.data.Settings

/**
 * Watches only the apps the user chose, recognises short-form video and endless feeds via
 * [BlockEngine], and presses Back (or Home) for the user.
 */
class ReelOffAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: Settings
    private val engine = BlockEngine()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var config: EngineConfig = EngineConfig()
    private var watched: Set<String> = emptySet()

    private var lastSnapshotAt = 0L
    private var pendingPackage: String? = null
    private var lastToastAt = 0L
    private var lastAllowanceSave = 0L

    private val deferredCheck = Runnable { pendingPackage?.let { checkScreen(it) } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = Settings.get(this)
        settings.prefs.registerOnSharedPreferenceChangeListener(this)
        val (day, used) = settings.loadAllowance()
        if (day == Settings.today()) engine.restoreAllowance(day, used)
        reloadConfig()
        instance = this
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        // Counters change on every block; only settings changes need a reload.
        if (key != null && (key.startsWith("blocks_") || key.startsWith("allowance_used") || key == "allowance_day")) return
        reloadConfig()
    }

    private fun reloadConfig() {
        config = settings.engineConfig()
        val packages = engine.watchedPackages(config)
        if (packages != watched) {
            watched = packages
            // Narrow the system's event delivery to the chosen apps: nothing else reaches us.
            serviceInfo = serviceInfo?.apply {
                packageNames = if (packages.isEmpty()) arrayOf(packageName) else packages.toTypedArray()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in watched) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (config.scrollLimit.enabled && pkg in config.scrollLimit.packages) {
                    val fraction = event.source?.let { source ->
                        val rect = Rect()
                        source.getBoundsInScreen(rect)
                        val (w, h) = screenSize()
                        (rect.width().toLong() * rect.height()).toFloat() / (w.toLong() * h).coerceAtLeast(1)
                    } ?: 1f
                    handle(engine.onScroll(pkg, fraction, config, System.currentTimeMillis()))
                }
                // A scroll in the Shorts/Reels pager is a new video - re-check the screen too.
                scheduleCheck(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> checkScreen(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scheduleCheck(pkg)
        }
    }

    /** Content-change events arrive in bursts; snapshot at most every [THROTTLE_MS]. */
    private fun scheduleCheck(pkg: String) {
        pendingPackage = pkg
        val sinceLast = SystemClock.uptimeMillis() - lastSnapshotAt
        handler.removeCallbacks(deferredCheck)
        if (sinceLast >= THROTTLE_MS) checkScreen(pkg) else handler.postDelayed(deferredCheck, THROTTLE_MS - sinceLast)
    }

    private fun checkScreen(pkg: String) {
        lastSnapshotAt = SystemClock.uptimeMillis()
        pendingPackage = null
        val root = rootInActiveWindow ?: return
        // The active window may already belong to another app (e.g. the launcher after Home).
        val rootPkg = root.packageName?.toString() ?: return
        if (rootPkg != pkg && rootPkg !in watched) return
        val (w, h) = screenSize()
        val snapshot = SnapshotBuilder.build(root, rootPkg, w, h)
        val now = System.currentTimeMillis()
        val decision = engine.onScreen(snapshot, config, now, Settings.today())
        handle(decision)
        if (decision is Decision.Allowed && now - lastAllowanceSave > 15_000) saveAllowance(now)
    }

    private fun handle(decision: Decision) {
        if (decision !is Decision.Block) return
        val action = when (decision.action) {
            BlockAction.BACK -> GLOBAL_ACTION_BACK
            BlockAction.HOME -> GLOBAL_ACTION_HOME
        }
        performGlobalAction(action)
        settings.recordBlock(decision.ruleId)
        saveAllowance(System.currentTimeMillis())
        showToast(messageFor(decision))
    }

    private fun messageFor(d: Decision.Block): String = when (d.reason) {
        Decision.Reason.SHORT_FORM -> "ReelOff closed ${d.title}. You've got better things to do."
        Decision.Reason.ALLOWANCE_USED_UP -> "Today's ${d.title} time is used up. See you tomorrow."
        Decision.Reason.SCROLL_LIMIT -> {
            val until = engine.scrollLockedUntil(d.ruleId, System.currentTimeMillis())
            val minutes = until?.let { ((it - System.currentTimeMillis()) / 60_000L + 1) } ?: config.scrollLimit.cooldownMillis / 60_000L
            "Scroll limit reached in ${d.title}. Take a $minutes-minute break."
        }
    }

    private fun showToast(text: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastToastAt < 3_000) return
        lastToastAt = now
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun saveAllowance(now: Long) {
        lastAllowanceSave = now
        val (day, used) = engine.exportAllowance()
        if (used.isNotEmpty()) settings.saveAllowance(day, used)
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::settings.isInitialized) {
            saveAllowance(System.currentTimeMillis())
            settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val THROTTLE_MS = 250L

        /** Non-null while the service is connected; lets the UI show live status. */
        @Volatile
        var instance: ReelOffAccessibilityService? = null
            private set
    }
}
