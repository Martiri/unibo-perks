package com.franco.uniboperks.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franco.uniboperks.BuildConfig
import com.franco.uniboperks.ui.theme.*
import com.franco.uniboperks.update.AppUpdateManager
import com.franco.uniboperks.update.UpdateInfo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateInfo?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgGlass)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Info & Aggiornamenti",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Versione applicazione, tuning POCO F7 e sicurezza",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            HorizontalDivider(thickness = 1.dp, color = BorderGlass)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Identità & Aggiornamenti
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BgSurface,
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "UniBo Perks",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                color = TextMuted,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = SuccessSubtle,
                            border = BorderStroke(1.dp, Success.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "Release",
                                color = Success,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (isCheckingUpdates) return@Button
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isCheckingUpdates = true
                            statusMessage = null
                            scope.launch {
                                val info = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
                                updateResult = info
                                isCheckingUpdates = false
                                if (!info.hasUpdate && info.errorMessage == null) {
                                    statusMessage = "Sei già all'ultima versione disponibile."
                                }
                            }
                        },
                        enabled = !isCheckingUpdates,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Verifica in corso...", fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Controlla aggiornamenti",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = msg,
                            color = Success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    updateResult?.let { update ->
                        if (update.hasUpdate) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = BgElevated,
                                border = BorderStroke(1.dp, AccentBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Nuova versione disponibile: ${update.latestVersion}",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = update.releaseNotes,
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            AppUpdateManager.openBrowserReleasePage(context, update.htmlUrl)
                                        },
                                        border = BorderStroke(1.dp, AccentBorder),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = AccentSubtle,
                                            contentColor = TextPrimary
                                        ),
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Text(
                                            text = "Scarica aggiornamento su Chrome",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        } else if (update.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = update.errorMessage,
                                color = TextSubtle,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Inset Grouped Card: Tuning POCO F7
            InsetInfoCard(
                icon = Icons.Default.Speed,
                iconTint = TextMuted,
                title = "Tuning Xiaomi POCO F7",
                items = listOf(
                    "Display AMOLED 120Hz forzato in Kotlin per scroll fluido",
                    "Layout Edge-to-Edge 19.5:9 con gestione punch-hole",
                    "Filtraggio reattivo su coroutine multi-core",
                    "Catalogo offline pre-caricato in background"
                )
            )

            // Inset Grouped Card: Sicurezza Play Protect
            InsetInfoCard(
                icon = Icons.Default.Security,
                iconTint = Success,
                title = "Sicurezza Google Play Protect",
                items = listOf(
                    "Zero permessi invasivi (escluso REQUEST_INSTALL_PACKAGES)",
                    "Solo connessione HTTPS per sincronizzare nuove convenzioni",
                    "Firma permanente RSA-2048 V1+V2 per aggiornamenti in-place",
                    "Scansione Play Protect pulita"
                )
            )

            // Link Sorgente GitHub
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Martiri/unibo-perks")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, BorderGlass),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = BgSurface,
                    contentColor = TextMuted
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Codice sorgente su GitHub",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun InsetInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    items: List<String>
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgSurface,
        border = BorderStroke(1.dp, BorderGlass),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    items.forEachIndexed { idx, item ->
                        Text(
                            text = item,
                            color = TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                        if (idx < items.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = BorderSubtle,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
