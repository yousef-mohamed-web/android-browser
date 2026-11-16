package com.example.geckobrowser

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
                val labels = content.descendants().filterIsInstance<TextView>().map { it.text.toString().replace("✓", "").trim() }.toList()
                assertTrue("Appearance section should be available", labels.contains("APPEARANCE"))
                assertTrue("Theme controls should be available", labels.contains("System") && labels.contains("Light") && labels.contains("Dark"))
                assertTrue("Home layout presets should be available", labels.contains("Comfortable") && labels.contains("Compact") && labels.contains("Minimal"))
                assertTrue("Search settings should be available", labels.contains("SEARCH") && labels.contains("Google"))
            }
        }
    }


    @Test
    fun geckoRuntimeUsesConfirmedInsecureNavigationMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("runtime")
                field.isAccessible = true
                val runtime = field.get(activity) as GeckoRuntime
                assertEquals(GeckoRuntimeSettings.ALLOW_ALL, runtime.settings.allowInsecureConnections)
                assertFalse("Remote debugging must remain disabled", runtime.settings.remoteDebuggingEnabled)
                assertFalse("about:config must remain disabled", runtime.settings.aboutConfigEnabled)
            }
        }
    }

    @Test
    fun appDoesNotRequestLegacySharedStoragePermission() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions?.toList() ?: emptyList()
        assertFalse("The app must not request broad external storage access", permissions.contains(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    private fun ViewGroup.descendants(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            yield(child)
            if (child is ViewGroup) yieldAll(child.descendants())
        }
    }
}
