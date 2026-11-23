package app.focuslite.core.model

/**
 * What the accessibility service saw on screen at one moment, reduced to plain data.
 * Contains no Android types so the detection engine can be tested on the JVM.
 *
 * [viewIds] holds short ids with the package prefix stripped, e.g. `clips_viewer_view_pager`
 * (use [normalizeViewId] to build them from full resource names).
 */
data class ScreenSnapshot(
    val packageName: String,
    val viewIds: Set<String> = emptySet(),
    /** Text of the browser's address bar, when the foreground app is a browser. */
    val urlBarText: String? = null,
    /** Short id of the currently selected bottom-bar tab, when known. */
    val selectedTab: String? = null,
)

/** `com.instagram.android:id/clips_tab` -> `clips_tab`. Names without a prefix are returned as is. */
fun normalizeViewId(resourceName: String): String =
    resourceName.substringAfter(":id/", missingDelimiterValue = resourceName).removePrefix("id/")
