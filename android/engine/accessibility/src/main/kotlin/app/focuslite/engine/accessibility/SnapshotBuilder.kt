package app.focuslite.engine.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import app.focuslite.core.model.ScreenSnapshot
import app.focuslite.core.model.normalizeViewId

/** Reads the visible view IDs (and the browser address bar) from a window into a [ScreenSnapshot]. */
object SnapshotBuilder {
    /** Safety valve so a pathological window can never stall the service. */
    private const val MAX_NODES = 3_000

    fun build(root: AccessibilityNodeInfo, packageName: String, urlBarId: String?): ScreenSnapshot {
        val ids = HashSet<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            // Instagram keeps the Reels pager alive off screen; only what the user can see counts.
            if (!node.isVisibleToUser) continue
            node.viewIdResourceName?.let { ids.add(normalizeViewId(it)) }
            for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
        }
        return ScreenSnapshot(
            packageName = packageName,
            viewIds = ids,
            urlBarText = urlBarId?.let { readUrlBar(root, packageName, it) },
        )
    }

    private fun readUrlBar(root: AccessibilityNodeInfo, pkg: String, urlBarId: String): String? =
        root.findAccessibilityNodeInfosByViewId("$pkg:id/$urlBarId")
            .firstNotNullOfOrNull { it.text?.toString()?.takeIf(String::isNotBlank) }
}
