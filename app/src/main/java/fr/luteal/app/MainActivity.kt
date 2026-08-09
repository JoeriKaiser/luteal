package fr.luteal.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import fr.luteal.app.navigation.LutealMainScaffold
import fr.luteal.core.designsystem.theme.LutealTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var widgetDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetDestination = intent.widgetDestination()
        enableEdgeToEdge()
        setContent {
            LutealTheme {
                LutealMainScaffold(
                    widgetDestination = widgetDestination,
                    onWidgetDestinationConsumed = { widgetDestination = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetDestination = intent.widgetDestination()
    }

    private fun Intent.widgetDestination(): String? =
        takeIf { action == ACTION_OPEN_WIDGET_DESTINATION }
            ?.getStringExtra(EXTRA_WIDGET_DESTINATION)

    companion object {
        const val ACTION_OPEN_WIDGET_DESTINATION = "fr.luteal.app.action.OPEN_WIDGET_DESTINATION"
        const val EXTRA_WIDGET_DESTINATION = "fr.luteal.app.extra.WIDGET_DESTINATION"
        const val WIDGET_DESTINATION_TODAY = "today"
        const val WIDGET_DESTINATION_TODAY_EDITOR = "today_editor"
        const val WIDGET_DESTINATION_DUO = "duo"
    }
}
