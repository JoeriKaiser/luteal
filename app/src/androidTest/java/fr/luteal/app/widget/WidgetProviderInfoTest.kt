package fr.luteal.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Process
import android.util.TypedValue
import kotlin.math.roundToInt
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.luteal.app.widget.duo.DuoCycleWidgetReceiver
import fr.luteal.app.widget.personal.PersonalCycleWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetProviderInfoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun bothProvidersExposeResponsiveMetadata() {
        val providers = AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(context.packageName, Process.myUserHandle())
            .associateBy { it.provider.className }

        listOf(
            PersonalCycleWidgetReceiver::class.java.name,
            DuoCycleWidgetReceiver::class.java.name
        ).forEach { className ->
            val info = requireNotNull(providers[className]) { "Missing provider $className" }
            assertEquals(dp(180), info.minWidth)
            assertEquals(dp(110), info.minHeight)
            assertTrue(info.minResizeWidth <= info.minWidth)
            assertTrue(info.minResizeHeight <= info.minHeight)
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).roundToInt()
}
