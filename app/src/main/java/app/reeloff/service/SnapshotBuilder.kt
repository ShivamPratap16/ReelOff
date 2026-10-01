package app.reeloff.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.reeloff.core.BrowserPackages
import app.reeloff.core.NodeInfo
import app.reeloff.core.ScreenSnapshot

/**
 * Copies the bits of the accessibility tree that detection needs into a [ScreenSnapshot].
 *
 * Privacy: node text is never copied, except the browser address bar. View ids, content
 * descriptions and the selected tab are all the rules look at.
 */
object SnapshotBuilder {

    private const val MAX_NODES = 2_000
    private const val MAX_DEPTH = 64

    fun build(root: AccessibilityNodeInfo, packageName: String, screenWidth: Int, screenHeight: Int): ScreenSnapshot {
        val screenArea = (screenWidth.toLong() * screenHeight).coerceAtLeast(1)
        val isBrowser = packageName in BrowserPackages.ALL
        val nodes = ArrayList<NodeInfo>(256)
        val selected = HashSet<String>()
        var urlBar: String? = null
        val rect = Rect()

        // Iterative DFS: deep view trees would overflow recursion on some apps.
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && nodes.size < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            val visible = node.isVisibleToUser
            val idName = NodeInfo.idName(node.viewIdResourceName)
            val description = node.contentDescription?.toString()

            node.getBoundsInScreen(rect)
            val width = (minOf(rect.right, screenWidth) - maxOf(rect.left, 0)).coerceAtLeast(0)
            val height = (minOf(rect.bottom, screenHeight) - maxOf(rect.top, 0)).coerceAtLeast(0)
            val fraction = (width.toLong() * height).toFloat() / screenArea

            nodes += NodeInfo(
                viewIdName = idName,
                contentDescription = description,
                visible = visible,
                screenFraction = fraction,
            )

            if (visible && node.isSelected) {
                val label = ScreenSnapshot.normalizeLabel(description)
                    ?: ScreenSnapshot.normalizeLabel(node.text?.toString())
                    ?: labelFromRelatives(node)
                if (label != null) selected += label
            }

            if (isBrowser && urlBar == null && idName != null && idName in BrowserPackages.URL_BAR_ID_NAMES) {
                urlBar = node.text?.toString()
            }

            if (depth < MAX_DEPTH) {
                for (i in node.childCount - 1 downTo 0) {
                    node.getChild(i)?.let { stack.addLast(it to depth + 1) }
                }
            }
        }

        return ScreenSnapshot(packageName, nodes, selected, urlBar)
    }

    /**
     * A selected tab icon often has no label of its own: the name sits on a child text view or on
     * the parent. Look one level down, then up to two levels up.
     */
    private fun labelFromRelatives(node: AccessibilityNodeInfo): String? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val label = ScreenSnapshot.normalizeLabel(child.contentDescription?.toString())
                ?: ScreenSnapshot.normalizeLabel(child.text?.toString())
            if (label != null) return label
        }
        var parent = node.parent
        repeat(2) {
            val p = parent ?: return null
            val label = ScreenSnapshot.normalizeLabel(p.contentDescription?.toString())
            if (label != null) return label
            parent = p.parent
        }
        return null
    }
}
