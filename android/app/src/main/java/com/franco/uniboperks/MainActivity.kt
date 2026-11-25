package com.franco.uniboperks

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
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

@Composable
private fun MainAppScreen(
    repo: DiscountsRepository,
    activity: ComponentActivity
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val saveableStateHolder = rememberSaveableStateHolder()
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

    val navItems = remember {
        listOf(
            NavItem("Catalogo", Icons.Default.LocalOffer),
            NavItem("Preferiti", Icons.Default.Bookmark),
            NavItem("Portali", Icons.Default.School),
            NavItem("Info", Icons.Default.Info)
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = BorderGlass)
                NavigationBar(
                    containerColor = BgGlass,
                    tonalElevation = 0.dp
                ) {
                    navItems.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (selectedTab != index) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTab = index
                                }
                            },
                            icon = {
                                if (index == 1 && favoriteIds.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = Accent,
                                                contentColor = TextPrimary
                                            ) {
                                                Text(
                                                    text = "${favoriteIds.size}",
                                                    style = TextStyle(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFeatureSettings = "tnum"
                                                    )
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
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextPrimary,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextSubtle,
                                unselectedTextColor = TextSubtle,
                                indicatorColor = BgHover
                            )
                        )
                    }
                }
            }
        },
        containerColor = BgBase
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            saveableStateHolder.SaveableStateProvider(key = selectedTab) {
                when (selectedTab) {
                    0 -> CatalogScreen(repo = repo)
                    1 -> FavoritesScreen(repo = repo)
                    2 -> PlatformsScreen(repo = repo)
                    3 -> SettingsScreen()
                }
            }
        }

        // Dialog notifica aggiornamento
        startupUpdateInfo?.let { update ->
            AlertDialog(
                onDismissRequest = { startupUpdateInfo = null },
                containerColor = BgSurface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Aggiornamento Disponibile (${update.latestVersion})",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "${update.releaseNotes}\n\nPuoi scaricare l'APK aggiornato dal browser Chrome. L'installazione manterrà intatti tutti i tuoi preferiti.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            AppUpdateManager.openBrowserReleasePage(activity, update.htmlUrl)
                            startupUpdateInfo = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(text = "Scarica da Chrome", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { startupUpdateInfo = null },
                        border = BorderStroke(1.dp, BorderGlass),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(text = "Più Tardi", color = TextMuted)
                    }
                }
            )
        }
    }
}
