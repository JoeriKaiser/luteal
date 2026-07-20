package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.component.CyclePhaseBadge
import fr.luteal.core.designsystem.component.FloatyBackground
import fr.luteal.core.designsystem.component.FloatyCard
import fr.luteal.core.designsystem.component.WhimsicalButton
import fr.luteal.core.model.CyclePhase

@Composable
fun LutealMainScaffold() {
    var selectedTab by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        NavigationTabItem("Cycle", Icons.Rounded.CalendarMonth),
        NavigationTabItem("Symptômes", Icons.Rounded.Favorite),
        NavigationTabItem("Duo Sync", Icons.Rounded.Sync),
        NavigationTabItem("Paramètres", Icons.Rounded.Settings)
    )

    FloatyBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 8.dp
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (selectedTab) {
                    0 -> CycleTabContent()
                    1 -> SymptomsTabContent()
                    2 -> DuoSyncTabContent()
                    3 -> SettingsTabContent()
                }
            }
        }
    }
}

private data class NavigationTabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun CycleTabContent() {
    Text(
        text = "Suivi du Cycle",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(16.dp))

    CyclePhaseBadge(phase = CyclePhase.LUTEAL)

    Spacer(modifier = Modifier.height(24.dp))

    FloatyCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Cycle En Cours",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Jour 21 • Phase Lutéale activée",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Prochaines règles estimées dans 7 jours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            WhimsicalButton(
                onClick = {},
                text = "Ajouter une note",
                icon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SymptomsTabContent() {
    Text(
        text = "Journal des Symptômes",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(16.dp))

    CyclePhaseBadge(phase = CyclePhase.FOLLICULAR)

    Spacer(modifier = Modifier.height(24.dp))

    FloatyCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Symptômes d'Aujourd'hui",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aucun symptôme sévère enregistré",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Suivez vos émotions, douleurs et énergie au quotidien.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            WhimsicalButton(
                onClick = {},
                text = "Consigner un symptôme",
                icon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DuoSyncTabContent() {
    Text(
        text = "Synchronisation Duo",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(16.dp))

    CyclePhaseBadge(phase = CyclePhase.OVULATORY)

    Spacer(modifier = Modifier.height(24.dp))

    FloatyCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Statut Duo Sync",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mode Partenaire: Connecté en local (P2P)",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Vos données de phase sont partagées en toute confidentialité.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            WhimsicalButton(
                onClick = {},
                text = "Synchroniser maintenant",
                icon = Icons.Rounded.Refresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsTabContent() {
    Text(
        text = "Paramètres de l'Application",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(16.dp))

    CyclePhaseBadge(phase = CyclePhase.MENSTRUAL)

    Spacer(modifier = Modifier.height(24.dp))

    FloatyCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Préférences & Confidencialité",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Stockage local chiffré activé",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Gérez le suivi des troubles (SPM, TDPM) et le mode de synchronisation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            WhimsicalButton(
                onClick = {},
                text = "Enregistrer la configuration",
                icon = Icons.Rounded.Settings,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
