package app.focuslite.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenSnapshotTest {
    @Test
    fun `full resource names lose their package prefix`() {
        assertEquals("clips_tab", normalizeViewId("com.instagram.android:id/clips_tab"))
        assertEquals("reel_recycler", normalizeViewId("com.google.android.youtube:id/reel_recycler"))
    }

    @Test
    fun `short and prefixed ids pass through`() {
        assertEquals("clips_tab", normalizeViewId("clips_tab"))
        assertEquals("clips_tab", normalizeViewId("id/clips_tab"))
    }
}
