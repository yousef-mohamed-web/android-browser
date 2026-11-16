package com.example.geckobrowser.privacy

import android.content.Context

/** Stores normalized host/URL tokens. This is not a full EasyList parser or cosmetic-filter engine. */
class FilterRepository(private val context: Context) {
    private val native = NativeFilter()
    @Volatile private var rules: Array<String> = emptyArray()
    fun loadBundledRules() { rules = context.assets.open("filters/basic-hosts.txt").bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith("!") && !it.startsWith("[") }.map { it.trim().removePrefix("||").removeSuffix("^") }.toList().toTypedArray() } }
    fun shouldBlock(url: String): Boolean = rules.isNotEmpty() && native.nativeIsBlocked(url, rules)
}
