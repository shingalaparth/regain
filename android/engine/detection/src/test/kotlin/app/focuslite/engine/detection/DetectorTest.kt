package app.focuslite.engine.detection

import app.focuslite.core.model.ScreenSnapshot
import app.focuslite.core.model.Surface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * NOTE: the snapshots below are SYNTHETIC, built from view IDs reported by other open-source
 * projects. Phase 1 replaces them with trees recorded on the real phone.
 */
class DetectorTest {
    private val rules = RulesParser.parse(
        File(System.getProperty("rules.path")).readText(),
    )
    private val detector = Detector(rules)

    private fun ig(vararg ids: String) = ScreenSnapshot("com.instagram.android", ids.toSet())

    @Test
    fun `instagram reels feed is detected`() {
        val s = ig("clips_viewer_view_pager", "clips_ufi_component", "clips_author_username")
        assertEquals(Surface.REELS_FEED, detector.detect(s))
    }

    @Test
    fun `a reel opened from a DM is not treated as the feed`() {
        val s = ig("clips_viewer_view_pager", "clips_ufi_component", "reel_viewer_message_composer")
        assertEquals(Surface.REEL_FROM_DM, detector.detect(s))
    }

    @Test
    fun `reels pager kept alive in the background does not count without a reel control`() {
        assertEquals(Surface.OTHER, detector.detect(ig("clips_viewer_view_pager", "feed_tab")))
    }

    @Test
    fun `instagram DM inbox is a DM`() {
        assertEquals(Surface.DM, detector.detect(ig("direct_inbox_recycler_view")))
    }

    @Test
    fun `instagram home feed is other`() {
        assertEquals(Surface.OTHER, detector.detect(ig("feed_tab", "clips_tab", "direct_tab")))
    }

    @Test
    fun `youtube shorts is detected and normal video is not`() {
        val yt = "com.google.android.youtube"
        assertEquals(Surface.SHORTS, detector.detect(ScreenSnapshot(yt, setOf("reel_recycler"))))
        assertEquals(Surface.OTHER, detector.detect(ScreenSnapshot(yt, setOf("watch_while_time_bar"))))
    }

    @Test
    fun `revanced youtube package uses the same rule`() {
        val s = ScreenSnapshot("app.revanced.android.youtube", setOf("reel_player_page_container"))
        assertEquals(Surface.SHORTS, detector.detect(s))
    }

    @Test
    fun `snapchat spotlight is detected and chat is not`() {
        val snap = "com.snapchat.android"
        assertEquals(Surface.SPOTLIGHT, detector.detect(ScreenSnapshot(snap, setOf("spotlight_pager"))))
        assertEquals(Surface.OTHER, detector.detect(ScreenSnapshot(snap, setOf("chat_input"))))
    }

    @Test
    fun `tiktok is blocked as a whole app on any screen`() {
        assertEquals(Surface.WHOLE_APP, detector.detect(ScreenSnapshot("com.zhiliaoapp.musically")))
        assertEquals(Surface.WHOLE_APP, detector.detect(ScreenSnapshot("com.ss.android.ugc.trill", setOf("i98"))))
    }

    @Test
    fun `browser short video urls are detected, with or without scheme`() {
        val chrome = "com.android.chrome"
        listOf(
            "youtube.com/shorts/abc123",
            "https://www.youtube.com/shorts/abc123",
            "instagram.com/reels/xyz/",
            "https://www.instagram.com/reel/xyz",
            "WWW.YOUTUBE.COM/SHORTS/ABC",
        ).forEach {
            assertEquals(Surface.BROWSER_SHORT_URL, detector.detect(ScreenSnapshot(chrome, urlBarText = it)), it)
        }
    }

    @Test
    fun `normal browser pages and a missing url are other`() {
        val chrome = "com.android.chrome"
        assertEquals(Surface.OTHER, detector.detect(ScreenSnapshot(chrome, urlBarText = "youtube.com/watch?v=1")))
        assertEquals(Surface.OTHER, detector.detect(ScreenSnapshot(chrome, urlBarText = null)))
    }

    @Test
    fun `unwatched app is other and not watched`() {
        val s = ScreenSnapshot("com.whatsapp", setOf("clips_viewer_view_pager"))
        assertEquals(Surface.OTHER, detector.detect(s))
        assertFalse(detector.watches("com.whatsapp"))
        assertTrue(detector.watches("com.instagram.android"))
    }

    @Test
    fun `packages and url bar id come from the rules`() {
        assertTrue("com.instagram.android" in detector.packages())
        assertTrue("com.android.chrome" in detector.packages())
        assertEquals("url_bar", detector.urlBarId("com.android.chrome"))
        assertEquals(null, detector.urlBarId("com.instagram.android"))
    }

    @Test
    fun `the video pagers that count reels come from the rules`() {
        assertTrue("clips_viewer_view_pager" in detector.counterIds("com.instagram.android"))
        assertTrue("reel_recycler" in detector.counterIds("com.google.android.youtube"))
        assertEquals(emptySet(), detector.counterIds("com.android.chrome"))
        assertEquals(emptySet(), detector.counterIds("com.whatsapp"))
    }

    @Test
    fun `social apps are lockable but browsers and unknown apps are not`() {
        assertTrue(detector.lockable("com.instagram.android"))
        assertTrue(detector.lockable("com.snapchat.android"))
        assertFalse(detector.lockable("com.android.chrome"))
        assertFalse(detector.lockable("com.whatsapp"))
    }

    @Test
    fun `escape tab comes from the rules`() {
        assertEquals("direct_tab", detector.escapeTab("com.instagram.android"))
        assertEquals(null, detector.escapeTab("com.google.android.youtube"))
    }

    @Test
    fun `rule order matters, the first matching rule wins`() {
        val file = RulesFile(
            version = 1,
            updated = "t",
            apps = listOf(
                AppRules(
                    packages = listOf("x.app"),
                    surfaces = listOf(
                        SurfaceRule(Surface.DM, anyOf = listOf("a")),
                        SurfaceRule(Surface.SHORTS, anyOf = listOf("a")),
                    ),
                ),
            ),
        )
        assertEquals(Surface.DM, Detector(file).detect(ScreenSnapshot("x.app", setOf("a"))))
    }

    @Test
    fun `the shipped rules file parses and every app has rules`() {
        assertTrue(rules.apps.isNotEmpty())
        assertTrue(rules.apps.all { it.surfaces.isNotEmpty() })
    }

    @Test
    fun `parser rejects broken rules`() {
        assertFailsWith<InvalidRulesException> { RulesParser.parse("not json") }
        assertFailsWith<InvalidRulesException> {
            RulesParser.parse("""{"version":0,"updated":"t","apps":[]}""")
        }
        assertFailsWith<InvalidRulesException> {
            RulesParser.parse(
                """{"version":1,"updated":"t","apps":[
                  {"packages":["a"],"surfaces":[{"surface":"DM"}]},
                  {"packages":["a"],"surfaces":[{"surface":"DM"}]}]}""",
            )
        }
        assertFailsWith<InvalidRulesException> {
            RulesParser.parse("""{"version":1,"updated":"t","apps":[{"packages":["a"],"surfaces":[]}]}""")
        }
    }
}
