package com.example.geckobrowser

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserSmokeTest {

    @Test
    fun appLaunchesAndShowsNOVAHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val views = content.descendants().toList()

                assertTrue("NOVA home screen should be present", views.filterIsInstance<TextView>().any { it.text.toString() == "NOVA" })
                assertTrue("Address/search field should be visible", views.filterIsInstance<EditText>().any { it.hint.toString().contains("Search or enter website") })
                assertTrue("Settings entry should be visible", views.filterIsInstance<TextView>().any { it.contentDescription == "Settings" })
            }
        }
    }

    @Test
    fun settingsScreenShowsAppearanceAndSearchOptions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val settings = content.descendants().filterIsInstance<TextView>().firstOrNull { it.contentDescription == "Settings" }
                assertTrue("Settings entry should exist", settings != null)
                settings!!.performClick()
                val labels = content.descendants().filterIsInstance<TextView>().map { it.text.toString() }.toList()
                assertTrue("Appearance section should be available", labels.contains("APPEARANCE"))
                assertTrue("Theme controls should be available", labels.contains("System") && labels.contains("Light") && labels.contains("Dark"))
                assertTrue("Home layout presets should be available", labels.contains("Comfortable") && labels.contains("Compact") && labels.contains("Minimal"))
                assertTrue("Search settings should be available", labels.contains("SEARCH") && labels.contains("Google"))
            }
        }
    }


    @Test
    fun insecureHttpExceptionIsSingleUse() {
        val gate = InsecureNavigationGate()
        val url = "http://example.com/private"
        assertFalse("HTTP must be blocked unless explicitly approved", gate.isAllowed(url))
        gate.allowOnce(url)
        assertTrue("Explicit approval should allow exactly one navigation", gate.isAllowed(url))
        assertFalse("The same approval must not be reusable", gate.isAllowed(url))
    }

    private fun ViewGroup.descendants(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            yield(child)
            if (child is ViewGroup) yieldAll(child.descendants())
        }
    }
}
