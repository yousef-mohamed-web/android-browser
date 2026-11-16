package com.example.geckobrowser

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserSmokeTest {

    @Test
    fun appLaunchesAndShowsBrowserControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val views = content.descendants().toList()

                assertTrue("Browser content view should be present", content.childCount > 0)
                assertTrue("Address/search field should be visible", views.any { it is EditText })
                assertTrue("Go button should be visible", views.filterIsInstance<Button>().any { it.text.toString() == "Go" })
                assertTrue(
                    "Google should be the default search engine",
                    views.filterIsInstance<Button>().any { it.text.toString() == "Search: Google" }
                )

                val addressField = views.filterIsInstance<EditText>().first()
                assertEquals("https://www.google.com", addressField.text.toString())
            }
        }
    }

    private fun ViewGroup.descendants(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            yield(child)
            if (child is ViewGroup) yieldAll(child.descendants())
        }
    }
}
