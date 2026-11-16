package com.example.geckobrowser

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BrowserEngine {
    fun googleSearchUrl(query: String): String = "https://www.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())

    fun setDesktopMode(session: GeckoSession, enabled: Boolean) {
        val settings = session.settings
        settings.userAgentMode = if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        settings.viewportMode = if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }
}
