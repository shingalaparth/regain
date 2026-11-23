package app.focuslite.engine.detection

import app.focuslite.core.model.Surface
import kotlinx.serialization.Serializable

/** The whole rules file (`rules/platform-rules.json`). Data, not code, so a fix is a JSON edit. */
@Serializable
data class RulesFile(
    val version: Int,
    val updated: String,
    val apps: List<AppRules>,
)

@Serializable
data class AppRules(
    /** One entry can cover several packages (clones, regional builds, ReVanced). */
    val packages: List<String>,
    /** Checked in order; the first rule that matches wins, so list specific rules first. */
    val surfaces: List<SurfaceRule>,
    /** View id of the tab to jump to after blocking (for example Instagram's DM tab). */
    val escapeTab: String? = null,
    /** Short id of the browser's address bar, for browser entries. */
    val urlBarId: String? = null,
)

/**
 * A rule matches a screen when ALL of these hold:
 * every id in [allOf] is present, at least one id in [anyOf] is present (if the list is not empty),
 * none of [noneOf] is present, and the URL contains one of [urlContains] (if the list is not empty).
 * A rule with no conditions at all matches every screen of the app (used for [Surface.WHOLE_APP]).
 */
@Serializable
data class SurfaceRule(
    val surface: Surface,
    val allOf: List<String> = emptyList(),
    val anyOf: List<String> = emptyList(),
    val noneOf: List<String> = emptyList(),
    val urlContains: List<String> = emptyList(),
    /**
     * Ids of the scrolling view that pages through the videos (for example Instagram's Reels pager). One scroll
     * of such a view is one swipe to the next video, which is what the reel counter counts.
     */
    val counterIds: List<String> = emptyList(),
    /**
     * Whether this rule was confirmed on the real phone with the current app version.
     * IDs copied from other projects start as false and are flipped after the Phase 1 recording.
     */
    val verified: Boolean = false,
)
