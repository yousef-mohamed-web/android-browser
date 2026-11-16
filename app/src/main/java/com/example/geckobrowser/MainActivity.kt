package com.example.geckobrowser

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {
    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession
    private lateinit var geckoView: GeckoView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private val insecure = InsecureNavigationGate()
    private var pendingHttp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = GeckoRuntime.getDefault(this)
        session = GeckoSession().apply { open(runtime) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10) }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        address = EditText(this).apply { hint = "Search or enter address"; setSingleLine(true); imeOptions = 2; setText("https://www.google.com") }
        val go = Button(this).apply { text = "Go"; setOnClickListener { navigate(address.text.toString()) } }
        toolbar.addView(address, LinearLayout.LayoutParams(0, -2, 1f)); toolbar.addView(go)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text = "Desktop"; setOnClickListener { BrowserEngine.setDesktopMode(session, true) } })
        actions.addView(Button(this).apply { text = "Mobile"; setOnClickListener { BrowserEngine.setDesktopMode(session, false) } })
        actions.addView(Button(this).apply { text = "Dark"; setOnClickListener { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) } })
        actions.addView(Button(this).apply { text = "Light"; setOnClickListener { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) } })
        actions.addView(Button(this).apply { text = "PiP"; setOnClickListener { enterBrowserPip() } })
        status = TextView(this).apply { text = "Ready"; setPadding(8) }
        geckoView = GeckoView(this).apply { setSession(this@MainActivity.session); isFocusable = true }
        root.addView(toolbar); root.addView(actions); root.addView(status)
        root.addView(geckoView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                val uri = request.uri
                if (uri.startsWith("http://") && !insecure.isAllowed(uri)) {
                    pendingHttp = uri
                    runOnUiThread { showHttpWarning(uri) }
                    return GeckoResult.deny()
                }
                return null
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) { runOnUiThread { status.text = "Loading…" } }
            override fun onPageStop(session: GeckoSession, success: Boolean) { runOnUiThread { status.text = if (success) "Loaded" else "Load failed" } }
        }
        navigate("https://www.google.com")
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val uri = if (value.contains(" ") || (!value.contains("://") && !value.contains('.'))) BrowserEngine.googleSearchUrl(value) else if (!value.contains("://")) "https://$value" else value
        if (uri.startsWith("http://") && !insecure.isAllowed(uri)) { pendingHttp = uri; showHttpWarning(uri); return }
        session.loadUri(uri)
    }

    private fun showHttpWarning(uri: String) {
        android.app.AlertDialog.Builder(this).setTitle("Insecure HTTP site")
            .setMessage("This site does not use HTTPS. Your connection may be intercepted. Continue only if you trust this site.\n\n$uri")
            .setNegativeButton("Cancel", null).setPositiveButton("Continue once") { _, _ ->
                insecure.allowOnce(uri); session.loadUri(uri)
            }.show()
    }

    private fun enterBrowserPip() {
        if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onDestroy() { session.close(); super.onDestroy() }
}
