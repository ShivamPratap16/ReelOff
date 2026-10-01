package app.reeloff.core

enum class BlockMode {
    /** Block one part of an app (the Shorts player) and leave the rest usable. */
    SECTION,

    /** The whole app is short-form video, so any screen of it is blocked. */
    WHOLE_APP,
}

/**
 * One distraction ReelOff knows how to recognise. A rule matches when the foreground package is
 * one of [packages] and *any* of its signals fire.
 *
 * Design rule learned from existing blockers: the single biggest complaint is false positives -
 * being bounced out of a normal home feed because a Shorts shelf or an inline reel scrolled past.
 * So every view-id signal also demands that the view fill most of the screen ([minPlayerFraction]).
 * A shelf or inline card never does; the full-screen vertical player always does.
 */
data class BlockRule(
    val id: String,
    val title: String,
    val description: String,
    val packages: Set<String>,
    val mode: BlockMode = BlockMode.SECTION,
    /** View id names that only exist on the full-screen short-video player. */
    val playerViewIds: List<String> = emptyList(),
    val minPlayerFraction: Float = DEFAULT_MIN_PLAYER_FRACTION,
    /** Bottom-nav tab labels that mean "you are on the short-video tab" when selected. */
    val selectedTabLabels: List<String> = emptyList(),
    /** Exact content descriptions only present on the short-video player (for apps with stripped ids). */
    val playerDescriptions: List<String> = emptyList(),
    /** URL fragments matched against the browser address bar. */
    val urlFragments: List<String> = emptyList(),
    /** Hosts blocked entirely in browsers (matched on host, so `x.com` never matches `netflix.com`). */
    val urlHosts: List<String> = emptyList(),
    val enabledByDefault: Boolean = true,
) {
    fun matches(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.packageName !in packages) return false
        if (mode == BlockMode.WHOLE_APP) return true
        if (playerViewIds.isNotEmpty() && snapshot.hasVisibleViewId(playerViewIds, minPlayerFraction)) return true
        if (selectedTabLabels.isNotEmpty() && snapshot.hasSelectedLabel(selectedTabLabels)) return true
        if (playerDescriptions.isNotEmpty() && snapshot.hasVisibleDescription(playerDescriptions)) return true
        val url = snapshot.urlBarText
        if (url != null) {
            if (UrlMatcher.containsAnyFragment(url, urlFragments)) return true
            val host = UrlMatcher.hostOf(url)
            if (urlHosts.any { UrlMatcher.hostMatches(host, it) }) return true
        }
        return false
    }

    companion object {
        /**
         * The vertical player covers (nearly) the whole screen; a 4:5 feed post covers ~55% and a
         * Shorts shelf far less, so 0.7 separates them with room to spare for system bars.
         */
        const val DEFAULT_MIN_PLAYER_FRACTION = 0.7f
    }
}
