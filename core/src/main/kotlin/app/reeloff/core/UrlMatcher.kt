package app.reeloff.core

/** Reads browser address bars, which show anything from a full URL to a bare host to a search query. */
object UrlMatcher {

    /** The host out of whatever the address bar is showing, or null when it is not a URL. */
    fun hostOf(urlBarText: String?): String? {
        var candidate = urlBarText?.trim()?.lowercase() ?: return null
        if (candidate.isEmpty() || candidate.contains(' ')) return null
        val scheme = candidate.indexOf("://")
        if (scheme >= 0) candidate = candidate.substring(scheme + 3)
        candidate = candidate
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .removePrefix("www.")
            .removePrefix("m.")
        if (!candidate.contains('.')) return null
        return candidate.takeIf { it.isNotEmpty() }
    }

    /** True for [domain] itself and its subdomains, never for lookalikes such as `nottiktok.com`. */
    fun hostMatches(host: String?, domain: String): Boolean {
        if (host == null) return false
        return host == domain || host.endsWith(".$domain")
    }

    /** The path of the URL (`/shorts/abc`), or "" when there is none. */
    fun pathOf(urlBarText: String?): String {
        val text = urlBarText?.trim()?.lowercase() ?: return ""
        val afterScheme = text.substringAfter("://", text)
        val slash = afterScheme.indexOf('/')
        return if (slash < 0) "" else afterScheme.substring(slash)
    }

    /**
     * True when the URL is on the host of one of [fragments] (`youtube.com/shorts`) and its path
     * starts with the fragment's path. Matching host and path separately means
     * `evil.com/?q=youtube.com/shorts` does not count.
     */
    fun containsAnyFragment(urlBarText: String?, fragments: List<String>): Boolean {
        if (fragments.isEmpty()) return false
        val host = hostOf(urlBarText) ?: return false
        val path = pathOf(urlBarText)
        return fragments.any { fragment ->
            val fragHost = fragment.substringBefore('/')
            val fragPath = fragment.substring(fragHost.length)
            hostMatches(host, fragHost) && path.startsWith(fragPath)
        }
    }
}
