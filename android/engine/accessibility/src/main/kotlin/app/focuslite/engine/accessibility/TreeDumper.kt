package app.focuslite.engine.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises a window's view tree so detection rules can be written and tested against real screens.
 * Privacy: records structure only (ids, classes, flags, bounds). Never text or content descriptions,
 * because those contain messages and usernames.
 */
object TreeDumper {
    private const val MAX_NODES = 4_000

    fun dump(root: AccessibilityNodeInfo, packageName: String, capturedAt: Long): JSONObject {
        val nodes = JSONArray()
        val bounds = Rect()
        // (node, depth) pairs, depth-first
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && nodes.length() < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            node.getBoundsInScreen(bounds)
            nodes.put(
                JSONObject()
                    .put("depth", depth)
                    .put("id", node.viewIdResourceName ?: JSONObject.NULL)
                    .put("class", node.className?.toString() ?: JSONObject.NULL)
                    .put("visible", node.isVisibleToUser)
                    .put("selected", node.isSelected)
                    .put("clickable", node.isClickable)
                    .put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"),
            )
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it to depth + 1) }
        }
        return JSONObject()
            .put("package", packageName)
            .put("capturedAt", capturedAt)
            .put("nodeCount", nodes.length())
            .put("nodes", nodes)
    }
}
