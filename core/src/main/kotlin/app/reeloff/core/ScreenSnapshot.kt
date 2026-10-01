package app.reeloff.core

/**
 * A plain-data copy of what is on screen, built by the accessibility service from the live
 * window. Keeping detection a pure function over this type is what lets every rule be unit tested
 * without a device.
 */
data class ScreenSnapshot(
    val packageName: String,
    val nodes: List<NodeInfo> = emptyList(),
    /**
     * Labels of the visible, selected nodes (usually the open bottom-nav tab), already reduced to
     * their first comma-separated segment: Facebook reports `Reels, tab 2 of 6`, we keep `Reels`.
     */
    val selectedLabels: Set<String> = emptySet(),
    /** Text of the browser address bar, when the foreground app is a browser. */
    val urlBarText: String? = null,
) {
    private val visibleNodes: List<NodeInfo> by lazy { nodes.filter { it.visible } }

    /** True when a visible node's id name equals one of [names] and covers at least [minFraction] of the screen. */
    fun hasVisibleViewId(names: Collection<String>, minFraction: Float): Boolean =
        visibleNodes.any { node ->
            val name = node.viewIdName ?: return@any false
            node.screenFraction >= minFraction && names.any { it.equals(name, ignoreCase = true) }
        }

    fun hasSelectedLabel(labels: Collection<String>): Boolean =
        selectedLabels.any { selected -> labels.any { it.equals(selected, ignoreCase = true) } }

    /** True when a visible node's content description equals one of [labels] (ignoring case). */
    fun hasVisibleDescription(labels: Collection<String>): Boolean =
        visibleNodes.any { node ->
            val desc = node.contentDescription ?: return@any false
            labels.any { it.equals(desc.trim(), ignoreCase = true) }
        }

    companion object {
        /** `Reels, tab 2 of 6` -> `Reels`. */
        fun normalizeLabel(raw: String?): String? =
            raw?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
    }
}

data class NodeInfo(
    /** The part of the resource id after `id/`, e.g. `reel_player_page_container`. */
    val viewIdName: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    /** AccessibilityNodeInfo.isVisibleToUser - pagers keep off-screen pages attached. */
    val visible: Boolean = true,
    /** Visible area of this node divided by the screen area, 0..1. */
    val screenFraction: Float = 0f,
) {
    companion object {
        /** `com.google.android.youtube:id/reel_recycler` -> `reel_recycler`. */
        fun idName(resourceId: String?): String? =
            resourceId?.substringAfter("id/", missingDelimiterValue = "")?.takeIf { it.isNotEmpty() }
    }
}
