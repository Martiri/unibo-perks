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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateInfo?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Info & Aggiornamenti",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dettagli applicazione, tuning POCO F7 e verifica versione",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        containerColor = DarkBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                )
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Identity Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "UniBo",
                                    color = UniBoRedLight,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Perks Native",
                                    color = UniBoGold,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Versione installata: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VerifiedGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, VerifiedGreen)
                        ) {
                            Text(
                                text = "Release",
                                color = VerifiedGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pulsante Verifica Aggiornamenti
                    Button(
                        onClick = {
                            if (isCheckingUpdates) return@Button
                            isCheckingUpdates = true
                            statusMessage = null
                            scope.launch {
                                val info = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
                                updateResult = info
                                isCheckingUpdates = false
                                if (!info.hasUpdate && info.errorMessage == null) {
                                    statusMessage = "Sei già all'ultima versione disponibile!"
                                }
                            }
                        },
                        enabled = !isCheckingUpdates,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UniBoRedLight,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
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
                            Text(text = "Controlla Aggiornamenti GitHub", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = VerifiedGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Se è disponibile un aggiornamento
                    updateResult?.let { update ->
                        if (update.hasUpdate) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = UniBoGold.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, UniBoGold),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Nuova versione disponibile: ${update.latestVersion}",
                                        color = UniBoGold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = update.releaseNotes,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            AppUpdateManager.openBrowserReleasePage(context, update.htmlUrl)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = UniBoGold,
                                            contentColor = DarkBackground
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Scarica Aggiornamento su Chrome",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        } else if (update.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = update.errorMessage,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // POCO F7 Hardware Tuning Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = UniBoGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tuning Xiaomi POCO F7",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Display AMOLED 120Hz forzato in Kotlin per scroll ultra-fluido\n" +
                                "• Supporto Aspect Ratio 19.5:9 con layout Edge-to-Edge per il punch-hole\n" +
                                "• Snapdragon 8s Gen 4: filtraggio reattivo dei 78+ sconti su coroutine multi-core\n" +
                                "• Catalogo offline pre-caricato a zero consumo dati",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Google Play Protect Safety Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = VerifiedGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sicurezza Google Play Protect",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Zero permessi pericolosi: escluso REQUEST_INSTALL_PACKAGES\n" +
                                "• Solo connessione HTTPS per verificare nuove convenzioni\n" +
                                "• Firma permanente RSA-2048 con schema V1+V2 per aggiornamenti in-place senza perdita di dati\n" +
                                "• Scansione Play Protect: 100% pulita senza avvisi malware",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Open Source GitHub Card
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Martiri/unibo-perks")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vedi Codice Sorgente su GitHub",
                    fontSize = 13.sp
                )
            }
        }
    }
}
