package com.franco.uniboperks

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franco.uniboperks.data.repository.DiscountsRepository
import com.franco.uniboperks.ui.screens.CatalogScreen
import com.franco.uniboperks.ui.screens.FavoritesScreen
import com.franco.uniboperks.ui.screens.PlatformsScreen
import com.franco.uniboperks.ui.screens.SettingsScreen
import com.franco.uniboperks.ui.theme.*
import com.franco.uniboperks.update.AppUpdateManager
import com.franco.uniboperks.update.UpdateInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ottimizzazione Display 120Hz per Xiaomi POCO F7 (HyperOS 2 / Android 15)
        try {
            val wm = windowManager
            val curDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay
            }
            val modes = curDisplay?.supportedModes ?: emptyArray()
            val mode120 = modes.firstOrNull { it.refreshRate >= 119.0f }
            if (mode120 != null) {
                val lp = window.attributes
                lp.preferredDisplayModeId = mode120.modeId
                window.attributes = lp
            }
        } catch (_: Exception) {}

        val repo = DiscountsRepository.getInstance(applicationContext)

        setContent {
            UniBoPerksTheme {
                MainAppScreen(repo = repo, activity = this)
            }
        }
    }
}

private data class NavItem(
    val title: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScreen(
    repo: DiscountsRepository,
    activity: ComponentActivity
) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var startupUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val favoriteIds by repo.favoriteIds.collectAsState()

    // Verifica silenziosa aggiornamenti all'avvio
    LaunchedEffect(Unit) {
        scope.launch {
            val info = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
            if (info.hasUpdate) {
                startupUpdateInfo = info
            }
        }
    }

    val navItems = listOf(
        NavItem("Catalogo", Icons.Default.LocalOffer),
        NavItem("Preferiti", Icons.Default.Bookmark),
        NavItem("Portali", Icons.Default.School),
        NavItem("Info", Icons.Default.Info)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 4.dp
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            if (index == 1 && favoriteIds.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = UniBoGold,
                                            contentColor = DarkBackground
                                        ) {
                                            Text(
                                                text = "${favoriteIds.size}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            }
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = UniBoRedLight,
                            selectedTextColor = UniBoRedLight,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = DarkCard
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                0 -> CatalogScreen(repo = repo)
                1 -> FavoritesScreen(repo = repo)
                2 -> PlatformsScreen(repo = repo)
                3 -> SettingsScreen()
            }
        }

        // Dialog notifica aggiornamento
        startupUpdateInfo?.let { update ->
            AlertDialog(
                onDismissRequest = { startupUpdateInfo = null },
                containerColor = DarkCard,
                title = {
                    Text(
                        text = "Aggiornamento Disponibile (${update.latestVersion})",
                        color = UniBoGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "${update.releaseNotes}\n\nPuoi scaricare l'APK aggiornato dal browser Chrome. L'installazione manterrà intatti tutti i tuoi preferiti.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            AppUpdateManager.openBrowserReleasePage(activity, update.htmlUrl)
                            startupUpdateInfo = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UniBoRedLight,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(text = "Scarica da Chrome", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { startupUpdateInfo = null }) {
                        Text(text = "Più Tardi", color = TextMuted)
                    }
                }
            )
        }
    }
}
