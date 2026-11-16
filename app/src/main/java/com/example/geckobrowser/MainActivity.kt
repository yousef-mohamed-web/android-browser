package com.example.geckobrowser

import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {
    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession
    private lateinit var geckoView: GeckoView
    private lateinit var address: EditText
    private lateinit var content: FrameLayout
    private lateinit var bottomBar: LinearLayout
    private val prefs by lazy { getSharedPreferences("browser_preferences", MODE_PRIVATE) }
    private val searchEngines = linkedMapOf("Google" to "https://www.google.com/search?q=", "Bing" to "https://www.bing.com/search?q=", "DuckDuckGo" to "https://duckduckgo.com/?q=")
    private var currentPage = "Home"
    private var canGoBack = false
    private var canGoForward = false

    private val dark: Boolean get() = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> (resources.configuration.uiMode and 0x30) == 0x20
    }
    private val bg get() = if (dark) Color.rgb(14, 19, 30) else Color.rgb(247, 249, 253)
    private val surface get() = if (dark) Color.rgb(27, 35, 49) else Color.WHITE
    private val textMain get() = if (dark) Color.rgb(241, 245, 251) else Color.rgb(25, 35, 52)
    private val textMuted get() = if (dark) Color.rgb(164, 177, 197) else Color.rgb(105, 119, 140)
    private val accent = Color.rgb(77, 119, 255)
    private val border get() = if (dark) Color.rgb(54, 65, 83) else Color.rgb(226, 232, 242)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .allowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
            .remoteDebuggingEnabled(false)
            .aboutConfigEnabled(false)
            .globalPrivacyControlEnabled(true)
            .debugLogging(false)
            .consoleOutput(false)
            .configFilePath("")
            .build()
        runtime = GeckoRuntime.create(this, runtimeSettings)
        session = GeckoSession().apply {
            settings.useTrackingProtection = true
            open(runtime)
        }
        geckoView = GeckoView(this).apply { setSession(this@MainActivity.session); isFocusable = true }
        buildShell()
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                val target = request.uri
                val scheme = android.net.Uri.parse(target).scheme?.lowercase()
                if (scheme == "https" || target == "about:blank") return null
                return requestRiskyNavigation(target, scheme ?: "unknown")
            }

            override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                canGoBack = value
            }

            override fun onCanGoForward(session: GeckoSession, value: Boolean) {
                canGoForward = value
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) { runOnUiThread { address.setText(url) } }
            override fun onPageStop(session: GeckoSession, success: Boolean) { runOnUiThread { } }
        }
        showHome()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        content = FrameLayout(this)
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(surface)
            setPadding(dp(5), dp(7), dp(5), dp(7))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomBar, LinearLayout.LayoutParams(-1, dp(64)))
        setContentView(root)
        updateBottomBar()
    }

    private fun showHome() {
        currentPage = "Home"
        val layoutMode = prefs.getString("home_layout", "Comfortable") ?: "Comfortable"
        val compact = layoutMode == "Compact"
        val minimal = layoutMode == "Minimal"
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(if (compact) 14 else 20), dp(if (compact) 14 else 20), dp(if (compact) 14 else 20), dp(22)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "N"; textSize = 25f; typeface = Typeface.create("sans-serif-black", Typeface.BOLD); gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = rounded(accent, 18); 
        }
        brand.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        brandText.addView(label("NOVA", 19, textMain, true))
        brandText.addView(label("BROWSER", 9, textMuted, true))
        brand.addView(brandText)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionChip("⚙", "Settings") { showSettings() })
        column.addView(header)
        column.addView(space(if (compact) 14 else 26))
        column.addView(label("Good day.", 28, textMain, true))
        column.addView(label("Where would you like to go?", 14, textMuted))
        column.addView(space(if (compact) 12 else 20))

        val searchCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(7), dp(8), dp(7))
            background = rounded(surface, 20, border, 1)
        }
        searchCard.addView(label("⌕", 25, textMuted, true), LinearLayout.LayoutParams(dp(34), dp(48)))
        address = EditText(this).apply {
            hint = "Search or enter website"; textSize = 14f; isSingleLine = true; setTextColor(textMain); setHintTextColor(textMuted)
            setPadding(0, 0, 0, 0); background = null; imeOptions = 2
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        searchCard.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchCard.addView(actionChip("GO", "Go") { navigate(address.text.toString()) })
        column.addView(searchCard)
        column.addView(space(if (compact) 12 else 22))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(19), dp(20), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(47, 79, 177), Color.rgb(91, 111, 239))).apply { cornerRadius = dp(22).toFloat() }
        }
        hero.addView(label("YOUR WEB, YOUR WAY", 10, Color.rgb(221, 229, 255), true))
        hero.addView(space(8))
        hero.addView(label("Browse freely.", 24, Color.WHITE, true))
        hero.addView(label("A faster, calmer place for the things you love.", 13, Color.rgb(231, 236, 255)))
        hero.addView(space(15))
        hero.addView(actionChip("Explore the web  →", "Explore") { navigate("https://www.google.com") })
        if (!minimal) column.addView(hero)
        column.addView(space(if (compact || minimal) 12 else 24))

        val shortcutsHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        shortcutsHeader.addView(label("Quick access", 17, textMain, true), LinearLayout.LayoutParams(0, -2, 1f))
        shortcutsHeader.addView(label("EDIT", 10, accent, true))
        column.addView(shortcutsHeader)
        column.addView(space(12))
        val sites = listOf(
            Triple("G", "Google", "https://www.google.com"), Triple("▶", "YouTube", "https://www.youtube.com"),
            Triple("f", "Facebook", "https://www.facebook.com"), Triple("X", "X", "https://x.com"),
            Triple("W", "Wikipedia", "https://www.wikipedia.org"), Triple("in", "LinkedIn", "https://www.linkedin.com"),
            Triple("B", "Bing", "https://www.bing.com"), Triple("+", "Add", "")
        )
        for (rowIndex in 0..1) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (site in sites.subList(rowIndex * 4, rowIndex * 4 + 4)) {
                val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(3), dp(8), dp(3), dp(8)); isClickable = true; setOnClickListener { if (site.third.isNotEmpty()) navigate(site.third) } }
                val glyph = TextView(this).apply { text = site.first; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(if (site.first == "+") accent else textMain); background = rounded(surface, 16, border, 1) }
                item.addView(glyph, LinearLayout.LayoutParams(dp(54), dp(54)))
                item.addView(space(6))
                item.addView(label(site.second, 10, textMuted))
                row.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
            }
            column.addView(row)
        }
        column.addView(space(18))
        val privacy = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); background = rounded(surface, 18, border, 1) }
        privacy.addView(label("◈", 24, accent, true), LinearLayout.LayoutParams(dp(38), -2))
        val privacyText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        privacyText.addView(label("Privacy comes first", 14, textMain, true))
        privacyText.addView(label("Your browsing. Your choices.", 11, textMuted))
        privacy.addView(privacyText)
        if (!minimal) column.addView(privacy)
        scroll.addView(column)
        page.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        content.removeAllViews()
        content.addView(page)
        updateBottomBar()
    }

    private fun showBrowser() {
        currentPage = "Browser"
        content.removeAllViews()
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val back = smallButton("‹") { if (canGoBack) session.goBack() }
        val forward = smallButton("›") { if (canGoForward) session.goForward() }
        val refresh = smallButton("↻") { session.reload() }
        bar.addView(back); bar.addView(forward); bar.addView(refresh)
        val url = EditText(this).apply { setTextColor(textMain); setHintTextColor(textMuted); hint = "Search or enter address"; singleLine = true; textSize = 13f; background = rounded(surface, 14, border, 1); setPadding(dp(10), dp(8), dp(10), dp(8)); imeOptions = 2; setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true } }
        bar.addView(url, LinearLayout.LayoutParams(0, dp(44), 1f))
        bar.addView(smallButton("⋮") { showBrowserMenu(it) })
        wrapper.addView(bar)
        wrapper.addView(geckoView, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(wrapper)
        address = url
        updateBottomBar()
    }

    private fun showSettings() {
        currentPage = "Settings"
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(24)); setBackgroundColor(bg) }
        col.addView(label("Settings", 27, textMain, true))
        col.addView(label("Make NOVA feel like yours.", 13, textMuted))
        col.addView(space(24))
        col.addView(sectionTitle("APPEARANCE"))
        col.addView(space(9))
        col.addView(settingCard("Theme", "Choose light, dark, or follow your device"))
        val themeOptions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(16)) }
        val selected = AppCompatDelegate.getDefaultNightMode()
        listOf("System" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, "Light" to AppCompatDelegate.MODE_NIGHT_NO, "Dark" to AppCompatDelegate.MODE_NIGHT_YES).forEach { (name, mode) ->
            val b = optionButton(name, selected == mode) { AppCompatDelegate.setDefaultNightMode(mode); recreate() }
            themeOptions.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }
        col.addView(themeOptions)
        col.addView(settingCard("Home screen layout", "Select the spacing and visual density"))
        val layoutOptions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(16)) }
        val layouts = listOf("Comfortable" to "More space between cards", "Compact" to "Tighter layout, more content", "Minimal" to "Simple and distraction-free")
        layouts.forEach { (name, desc) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(surface, 14, border, 1); isClickable = true
                setOnClickListener { prefs.edit().putString("home_layout", name).apply(); showSettings() } }
            row.addView(label(name + if (prefs.getString("home_layout", "Comfortable") == name) "  ✓" else "", 14, textMain, true))
            row.addView(label(desc, 11, textMuted))
            layoutOptions.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        col.addView(layoutOptions)
        col.addView(sectionTitle("SEARCH"))
        col.addView(space(9))
        col.addView(settingCard("Default search engine", "Used when you type a search instead of a web address"))
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(18)) }
        searchRow.addView(label(selectedSearchEngine(), 14, textMain, true), LinearLayout.LayoutParams(0, -2, 1f))
        searchRow.addView(actionChip("Change", "Search engine") { showSearchEngineMenu() })
        col.addView(searchRow)
        col.addView(sectionTitle("ABOUT"))
        col.addView(space(10))
        col.addView(settingCard("NOVA Browser", "Powered by GeckoView • Android 9 and above"))
        scroll.addView(col)
        content.removeAllViews()
        content.addView(scroll)
        updateBottomBar()
    }

    private fun settingCard(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); background = rounded(surface, 15, border, 1)
        addView(label(title, 14, textMain, true)); addView(space(3)); addView(label(subtitle, 11, textMuted))
    }

    private fun sectionTitle(value: String) = label(value, 10, accent, true)
    private fun optionButton(name: String, active: Boolean, click: () -> Unit) = TextView(this).apply {
        text = name; gravity = Gravity.CENTER; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (active) Color.WHITE else textMain); background = rounded(if (active) accent else surface, 13, if (active) accent else border, 1); setOnClickListener { click() }
    }

    private fun updateBottomBar() {
        if (!::bottomBar.isInitialized) return
        bottomBar.removeAllViews()
        listOf("Home" to "⌂", "Bookmarks" to "☆", "History" to "◷", "Downloads" to "↓", "Settings" to "⚙").forEach { (name, glyph) ->
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true; setPadding(0, dp(2), 0, dp(2)); setOnClickListener {
                when (name) { "Home" -> showHome(); "Settings" -> showSettings(); "Bookmarks", "History", "Downloads" -> showSimplePage(name) }
            } }
            item.addView(label(glyph, 20, if (currentPage == name) accent else textMuted, true))
            item.addView(label(name, 9, if (currentPage == name) accent else textMuted, currentPage == name))
            bottomBar.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun showSimplePage(name: String) {
        currentPage = name
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)); setBackgroundColor(bg) }
        col.addView(label(name, 25, textMain, true))
        col.addView(space(8))
        col.addView(label("Nothing here yet. This section is ready for the next update.", 13, textMuted))
        content.removeAllViews(); content.addView(col); updateBottomBar()
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val hasExplicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value)
        val uri = when {
            hasExplicitScheme -> value
            value.contains(" ") || !value.contains('.') -> searchUrl(value)
            else -> "https://$value"
        }
        showBrowser()
        address.setText(uri)
        session.loadUri(uri)
    }

    private fun requestRiskyNavigation(
        target: String,
        scheme: String
    ): GeckoResult<org.mozilla.geckoview.AllowOrDeny> {
        val decision = GeckoResult<org.mozilla.geckoview.AllowOrDeny>()
        val host = android.net.Uri.parse(target).host
        val destination = host?.let { "$scheme://$it" } ?: "$scheme: link"
        val warning = when (scheme) {
            "http" -> "This website uses HTTP, not encrypted HTTPS. Data exchanged with it may be visible to others."
            "file" -> "This is a local file address. Continue only if you recognize and trust this file."
            "content" -> "This link requests content provided by an Android app. Continue only if you trust its source."
            "javascript" -> "This link can execute JavaScript in the current page. It may read or change page data."
            "data" -> "This link contains embedded data or code. Opening it may display untrusted content."
            else -> "This link uses the '$scheme' protocol, which may open another app or access external content."
        }
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Security warning")
                .setMessage("$warning\n\nDestination: $destination\n\nDo you want to continue?")
                .setPositiveButton("Continue") { _, _ ->
                    decision.complete(org.mozilla.geckoview.AllowOrDeny.ALLOW)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    decision.complete(org.mozilla.geckoview.AllowOrDeny.DENY)
                }
                .setOnCancelListener {
                    decision.complete(org.mozilla.geckoview.AllowOrDeny.DENY)
                }
                .show()
        }
        return decision
    }

    private fun selectedSearchEngine(): String = prefs.getString("search_engine", "Google") ?: "Google"
    private fun searchUrl(query: String): String {
        val base = searchEngines[selectedSearchEngine()] ?: searchEngines.getValue("Google")
        return base + java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
    }
    private fun showSearchEngineMenu() {
        PopupMenu(this, content).apply {
            searchEngines.keys.forEach { menu.add(it) }
            setOnMenuItemClickListener { item -> prefs.edit().putString("search_engine", item.title.toString()).apply(); if (currentPage == "Settings") showSettings(); true }
            show()
        }
    }
    private fun showBrowserMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Home"); menu.add("Settings"); menu.add("Desktop site"); menu.add("Mobile site"); menu.add("Search engine")
            setOnMenuItemClickListener { item -> when (item.title.toString()) {
                "Home" -> showHome(); "Settings" -> showSettings(); "Desktop site" -> BrowserEngine.setDesktopMode(session, true)
                "Mobile site" -> BrowserEngine.setDesktopMode(session, false); "Search engine" -> showSearchEngineMenu()
            }; true }
            show()
        }
    }
    private fun actionChip(text: String, description: String, click: () -> Unit) = TextView(this).apply {
        this.text = text; contentDescription = description; gravity = Gravity.CENTER; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); background = rounded(accent, 14); setPadding(dp(14), dp(11), dp(14), dp(11)); isClickable = true; setOnClickListener { click() }
    }
    private fun smallButton(text: String, click: (View) -> Unit) = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 22f; setTextColor(textMain); background = rounded(surface, 12, border, 1)
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(42)).apply { marginEnd = dp(5) }; setOnClickListener { click(this) }
    }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun actionChipNoop() = Unit
    private fun rounded(color: Int, radius: Int, stroke: Int? = null, strokeWidth: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(strokeWidth), stroke)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun enterBrowserPip() {
        if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }
    override fun onBackPressed() {
        if (currentPage == "Browser") { if (canGoBack) session.goBack() else showHome() }
        else if (currentPage != "Home") showHome() else super.onBackPressed()
    }
    override fun onDestroy() { session.close(); super.onDestroy() }
}
