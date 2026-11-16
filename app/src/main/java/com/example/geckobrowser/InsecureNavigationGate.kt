package com.example.geckobrowser

import java.util.concurrent.ConcurrentHashMap

/** Explicit, one-navigation user bypass; HTTP is never globally enabled. */
class InsecureNavigationGate {
    private val allowed = ConcurrentHashMap.newKeySet<String>()
    fun allowOnce(url: String) { allowed.add(url) }
    fun isAllowed(url: String): Boolean = allowed.remove(url)
}
