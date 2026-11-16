package com.example.geckobrowser.privacy

class NativeFilter {
    init { System.loadLibrary("gecko_filter") }
    external fun nativeIsBlocked(url: String, rules: Array<String>): Boolean
}
