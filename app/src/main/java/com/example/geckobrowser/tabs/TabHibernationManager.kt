package com.example.geckobrowser.tabs

import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.GeckoSession
import java.util.concurrent.ConcurrentHashMap

/** Releases background GeckoSession resources after inactivity; restores by reopening the session. */
class TabHibernationManager(private val idleMs: Long = 120_000L) {
    data class Tab(val id: String, var session: GeckoSession?, var lastActive: Long, var url: String, var hibernated: Boolean = false)
    private val tabs = ConcurrentHashMap<String, Tab>()
    private val handler = Handler(Looper.getMainLooper())
    fun register(id: String, session: GeckoSession, url: String = "about:blank") { tabs[id] = Tab(id, session, System.currentTimeMillis(), url) }
    fun activate(id: String, reopen: (String) -> GeckoSession) {
        tabs[id]?.let { tab ->
            if (tab.hibernated) { tab.session = reopen(tab.url); tab.hibernated = false }
            tab.lastActive = System.currentTimeMillis()
        }
    }
    fun updateUrl(id: String, url: String) { tabs[id]?.let { it.url = url; it.lastActive = System.currentTimeMillis() } }
    fun hibernateIdle() {
        val now = System.currentTimeMillis()
        tabs.values.filter { !it.hibernated && now - it.lastActive >= idleMs }.forEach { tab ->
            tab.session?.close(); tab.session = null; tab.hibernated = true
        }
    }
    fun closeAll() { tabs.values.forEach { it.session?.close() }; tabs.clear() }
}
