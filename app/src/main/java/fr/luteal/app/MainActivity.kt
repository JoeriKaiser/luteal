package fr.luteal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import fr.luteal.app.navigation.LutealMainScaffold
import fr.luteal.core.designsystem.theme.LutealTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LutealTheme {
                LutealMainScaffold()
            }
        }
    }
}
