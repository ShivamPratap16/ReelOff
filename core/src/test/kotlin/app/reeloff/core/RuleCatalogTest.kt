package app.reeloff.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleCatalogTest {

    private val yt = "com.google.android.youtube"
    private val ig = "com.instagram.android"

    private fun match(snapshot: ScreenSnapshot): BlockRule? =
        RuleCatalog.ALL.firstOrNull { it.matches(snapshot) }

    @Test
    fun youtubeShortsPlayerFullScreenIsBlocked() {
        val s = ScreenSnapshot(yt, listOf(NodeInfo("reel_player_page_container", screenFraction = 0.95f)))
        assertEquals("youtube_shorts", match(s)?.id)
    }

    @Test
    fun youtubeShortsShelfOnHomeFeedIsNotBlocked() {
        // The Shorts shelf reuses reel_* ids but only covers a slice of the home feed.
        val s = ScreenSnapshot(
            yt,
            listOf(
                NodeInfo("results", screenFraction = 0.9f),
                NodeInfo("reel_recycler", screenFraction = 0.3f),
            ),
            selectedLabels = setOf("Home"),
        )
        assertNull(match(s))
    }

    @Test
    fun youtubeRegularVideoIsNotBlocked() {
        val s = ScreenSnapshot(yt, listOf(NodeInfo("watch_player", screenFraction = 0.35f), NodeInfo("watch_list", screenFraction = 0.6f)))
        assertNull(match(s))
    }

    @Test
    fun youtubeShortsTabSelectedIsBlocked() {
        val s = ScreenSnapshot(yt, selectedLabels = setOf("Shorts"))
        assertEquals("youtube_shorts", match(s)?.id)
    }

    @Test
    fun invisibleNodesNeverCount() {
        // Instagram keeps the Reels pager page attached behind the home feed.
        val s = ScreenSnapshot(ig, listOf(NodeInfo("clips_viewer_view_pager", visible = false, screenFraction = 1f)))
        assertNull(match(s))
    }

    @Test
    fun instagramReelsViewerIsBlocked() {
        val s = ScreenSnapshot(ig, listOf(NodeInfo("clips_viewer_view_pager", screenFraction = 0.92f)))
        assertEquals("instagram_reels", match(s)?.id)
    }

    @Test
    fun instagramInlineFeedReelIsNotBlocked() {
        val s = ScreenSnapshot(
            ig,
            listOf(NodeInfo("clips_video_container", screenFraction = 0.56f), NodeInfo("feed_tab")),
            selectedLabels = setOf("Home"),
        )
        assertNull(match(s))
    }

    @Test
    fun facebookReelsRecognisedByLabelBecauseIdsAreStripped() {
        val s = ScreenSnapshot(
            "com.facebook.katana",
            listOf(NodeInfo(viewIdName = "(name removed)", contentDescription = "Reel details", screenFraction = 1f)),
        )
        assertEquals("facebook_reels", match(s)?.id)
    }

    @Test
    fun facebookInlineReelLabelIsNotBlocked() {
        val s = ScreenSnapshot("com.facebook.katana", listOf(NodeInfo(contentDescription = "Reel", screenFraction = 0.5f)))
        assertNull(match(s))
    }

    @Test
    fun facebookReelsTabLabelIsNormalised() {
        val label = ScreenSnapshot.normalizeLabel("Reels, tab 2 of 6")!!
        val s = ScreenSnapshot("com.facebook.katana", selectedLabels = setOf(label))
        assertEquals("facebook_reels", match(s)?.id)
    }

    @Test
    fun tiktokIsBlockedWholeApp() {
        val rule = match(ScreenSnapshot("com.zhiliaoapp.musically"))
        assertEquals("tiktok", rule?.id)
        assertEquals(BlockMode.WHOLE_APP, rule?.mode)
    }

    @Test
    fun browserShortsUrlIsBlocked() {
        val s = ScreenSnapshot("com.android.chrome", urlBarText = "https://m.youtube.com/shorts/abc123")
        assertEquals("browser_short_form", match(s)?.id)
        assertEquals("browser_short_form", match(s.copy(urlBarText = "youtube.com/shorts/x"))?.id)
        assertEquals("browser_short_form", match(s.copy(urlBarText = "instagram.com/reels/"))?.id)
        assertEquals("browser_short_form", match(s.copy(urlBarText = "www.tiktok.com/@someone"))?.id)
    }

    @Test
    fun browserNormalPagesAreNotBlocked() {
        val s = ScreenSnapshot("com.android.chrome", urlBarText = "https://www.youtube.com/watch?v=abc")
        assertNull(match(s))
        assertNull(match(s.copy(urlBarText = "nottiktok.com")))
        assertNull(match(s.copy(urlBarText = "example.com/?q=youtube.com/shorts")))
        assertNull(match(s.copy(urlBarText = "youtube shorts funny")))
        assertNull(match(s.copy(urlBarText = null)))
    }

    @Test
    fun otherAppsAreNeverMatched() {
        val s = ScreenSnapshot("com.whatsapp", listOf(NodeInfo("reel_player_page_container", screenFraction = 1f)), setOf("Shorts"))
        assertNull(match(s))
    }

    @Test
    fun ruleIdsAreUniqueAndPackagesNonEmpty() {
        assertEquals(RuleCatalog.ALL.size, RuleCatalog.ALL.map { it.id }.toSet().size)
        assertTrue(RuleCatalog.ALL.all { it.packages.isNotEmpty() })
        assertFalse(RuleCatalog.ALL_PACKAGES.contains("com.whatsapp"))
    }

    @Test
    fun idNameParsing() {
        assertEquals("reel_recycler", NodeInfo.idName("com.google.android.youtube:id/reel_recycler"))
        assertNull(NodeInfo.idName(null))
        assertNull(NodeInfo.idName("garbage"))
    }

    @Test
    fun hostParsing() {
        assertEquals("tiktok.com", UrlMatcher.hostOf("https://www.tiktok.com/foryou"))
        assertEquals("youtube.com", UrlMatcher.hostOf("m.youtube.com"))
        assertNull(UrlMatcher.hostOf("search words here"))
        assertNull(UrlMatcher.hostOf("localhost"))
        assertTrue(UrlMatcher.hostMatches("vm.tiktok.com", "tiktok.com"))
        assertFalse(UrlMatcher.hostMatches("nottiktok.com", "tiktok.com"))
    }
}
