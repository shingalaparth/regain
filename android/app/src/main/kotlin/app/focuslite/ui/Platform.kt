package app.focuslite.ui

import androidx.compose.ui.graphics.Color
import app.focuslite.core.model.Surface
import app.focuslite.engine.detection.RulesFile

/** One row in a platform's settings sheet: the setting of one [Surface]. */
data class SurfaceToggle(val surface: Surface, val title: String, val hint: String? = null)

/**
 * The apps Regain covers. Tiles show each app's real launcher icon from the phone; the colour and
 * monogram are only the fallback if the icon cannot be loaded.
 */
enum class Platform(
    val label: String,
    /** A package that is certainly this platform; the full package list comes from the rules file. */
    val anchorPackage: String,
    val glyph: String,
    val tile: Color,
    val onTile: Color,
    val toggles: List<SurfaceToggle>,
) {
    Instagram(
        "Instagram", "com.instagram.android", "Ig", Color(0xFFC13584), Color.White,
        listOf(
            SurfaceToggle(Surface.REELS_FEED, "Reels", "The Reels tab and feed"),
            SurfaceToggle(Surface.REEL_FROM_DM, "Reels friends send", "Allow lets a reel a friend sent play. Whether swiping on to the next reel is caught still needs testing"),
        ),
    ),
    YouTube(
        "YouTube", "com.google.android.youtube", "Yt", Color(0xFFE53935), Color.White,
        listOf(SurfaceToggle(Surface.SHORTS, "Shorts", "The Shorts shelf and tab")),
    ),
    Snapchat(
        "Snapchat", "com.snapchat.android", "Sc", Color(0xFFFFD500), Color(0xFF111114),
        listOf(SurfaceToggle(Surface.SPOTLIGHT, "Spotlight", "Chats and stories keep working")),
    ),
    TikTok(
        "TikTok", "com.zhiliaoapp.musically", "Tt", Color(0xFF111114), Color.White,
        listOf(SurfaceToggle(Surface.WHOLE_APP, "The whole app", "Its screens can't be told apart safely")),
    ),
    Browser(
        "Browser", "com.android.chrome", "Br", Color(0xFF3B6FB6), Color.White,
        listOf(SurfaceToggle(Surface.BROWSER_SHORT_URL, "Short-video links", "instagram.com/reels, youtube.com/shorts, ...")),
    ),
    ;

    val surfaces: Set<Surface> get() = toggles.map { it.surface }.toSet()

    /** Every package of this platform (clones, ReVanced builds, other browsers), straight from the rules. */
    fun packages(rules: RulesFile): Set<String> =
        rules.apps.firstOrNull { anchorPackage in it.packages }?.packages?.toSet() ?: setOf(anchorPackage)
}
