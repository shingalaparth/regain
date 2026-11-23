package app.focuslite.core.model

/** A kind of screen inside a social app that the blocker cares about. */
enum class Surface {
    /** Instagram Reels feed (the swipeable full-screen video pager). */
    REELS_FEED,

    /** A reel opened from a direct message. Played, but not swiped onward. */
    REEL_FROM_DM,

    /** YouTube Shorts. */
    SHORTS,

    /** Snapchat Spotlight. */
    SPOTLIGHT,

    /** Instagram Explore. Detection rule not written yet (needs verifying on device). */
    EXPLORE,

    /** Direct messages or chat. Always allowed. */
    DM,

    /** A browser showing a short-video URL (instagram.com/reels, youtube.com/shorts, ...). */
    BROWSER_SHORT_URL,

    /** The whole app is the problem (for example TikTok, whose view IDs are scrambled). */
    WHOLE_APP,

    /** Anything else. Always allowed. */
    OTHER,
}
