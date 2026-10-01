package app.reeloff.core

/**
 * Every distraction ReelOff ships with. When an app redesign breaks detection, this is the one
 * file to update: dump the screen (`adb shell uiautomator dump`) and add the new view id here.
 */
object RuleCatalog {

    val YOUTUBE_SHORTS = BlockRule(
        id = "youtube_shorts",
        title = "YouTube Shorts",
        description = "Closes the Shorts player and Shorts tab. Regular videos, search and subscriptions keep working.",
        packages = setOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.rvx.android.youtube",
        ),
        playerViewIds = listOf(
            "reel_player_page_container",
            "reel_watch_player",
            "reel_recycler",
            "reel_progress_bar",
            "reel_time_bar",
            "shorts_player",
            "shorts_container",
        ),
        selectedTabLabels = listOf("Shorts"),
    )

    val INSTAGRAM_REELS = BlockRule(
        id = "instagram_reels",
        title = "Instagram Reels",
        description = "Closes the Reels tab and the full-screen reels viewer. Feed, stories and DMs keep working.",
        packages = setOf("com.instagram.android", "com.instagram.lite"),
        // `clips_video_container` is deliberately absent: it is also the inline reel in the home
        // feed, which at 4:5 can cover more than half the screen.
        playerViewIds = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_root",
            "clips_viewer_fragment",
            "clips_swipe_refresh_container",
            "reels_viewer",
        ),
        selectedTabLabels = listOf("Reels"),
    )

    val FACEBOOK_REELS = BlockRule(
        id = "facebook_reels",
        title = "Facebook Reels",
        description = "Closes the Reels tab and full-screen reels. The feed, Marketplace and groups keep working.",
        packages = setOf("com.facebook.katana", "com.facebook.lite"),
        // Facebook strips its resource names, so its player is recognised by labels instead.
        playerDescriptions = listOf("Reel details", "Reels tab details", "Navigate to your Reels profile"),
        playerViewIds = listOf("fb_shorts_viewer_page", "fb_shorts_viewer_fragment"),
        selectedTabLabels = listOf("Reels"),
    )

    val SNAPCHAT_SPOTLIGHT = BlockRule(
        id = "snapchat_spotlight",
        title = "Snapchat Spotlight",
        description = "Closes Spotlight. Chat, camera and stories keep working.",
        packages = setOf("com.snapchat.android"),
        playerViewIds = listOf("spotlight_page", "spotlight_feed", "spotlight_container", "spotlight_player"),
        selectedTabLabels = listOf("Spotlight"),
    )

    val TIKTOK = BlockRule(
        id = "tiktok",
        title = "TikTok",
        description = "The whole app is an endless short-video feed, so ReelOff closes it entirely.",
        packages = setOf(
            "com.zhiliaoapp.musically",
            "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme",
        ),
        mode = BlockMode.WHOLE_APP,
    )

    val MOJ_JOSH = BlockRule(
        id = "indian_short_video",
        title = "Moj, Josh, MX TakaTak, Chingari",
        description = "Short-video apps popular in India. All of each app is short-form, so they are closed entirely.",
        packages = setOf(
            "in.mohalla.video",            // Moj
            "com.eterno.shortvideos",      // Josh
            "com.next.innovation.takatak", // MX TakaTak
            "io.chingari.app",
        ),
        mode = BlockMode.WHOLE_APP,
    )

    val BROWSER_SHORT_FORM = BlockRule(
        id = "browser_short_form",
        title = "Shorts & Reels in browsers",
        description = "Blocks youtube.com/shorts, instagram.com/reels, facebook.com/reel and tiktok.com in Chrome, Firefox, Edge, Brave, Samsung Internet and more.",
        packages = BrowserPackages.ALL,
        urlFragments = listOf(
            "youtube.com/shorts",
            "instagram.com/reel",
            "instagram.com/reels",
            "facebook.com/reel",
            "facebook.com/reels",
            "snapchat.com/spotlight",
        ),
        urlHosts = listOf("tiktok.com"),
    )

    /** Section rules first: on a screen matching several rules, the most specific explanation wins. */
    val ALL: List<BlockRule> = listOf(
        YOUTUBE_SHORTS,
        INSTAGRAM_REELS,
        FACEBOOK_REELS,
        SNAPCHAT_SPOTLIGHT,
        TIKTOK,
        MOJ_JOSH,
        BROWSER_SHORT_FORM,
    )

    /** The only packages the accessibility service ever inspects for short-form video. */
    val ALL_PACKAGES: Set<String> = ALL.flatMap { it.packages }.toSet()

    fun byId(id: String): BlockRule? = ALL.firstOrNull { it.id == id }

    val DEFAULT_ENABLED_IDS: Set<String> = ALL.filter { it.enabledByDefault }.map { it.id }.toSet()
}

object BrowserPackages {
    val ALL: Set<String> = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "org.mozilla.firefox",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.sec.android.app.sbrowser",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.mi.globalbrowser",
        "com.UCMobile.intl",
    )

    /** Address-bar view id names across the browsers above. */
    val URL_BAR_ID_NAMES: Set<String> = setOf(
        "url_bar",
        "url_field",
        "omnibox_text_input",
        "mozac_browser_toolbar_url_view",
        "location_bar_edit_text",
        "toolbar_text",
        "search_bar_text",
        "url",
        "address_bar_edit_text",
    )
}
