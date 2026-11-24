package com.franco.uniboperks.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franco.uniboperks.data.model.Discount
import com.franco.uniboperks.data.repository.DiscountsRepository
import com.franco.uniboperks.ui.components.DiscountCard
import com.franco.uniboperks.ui.components.DiscountCardSkeleton
import com.franco.uniboperks.ui.components.DiscountDetailModal
import com.franco.uniboperks.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CatalogScreen(
    repo: DiscountsRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val discounts by repo.discounts.collectAsState()
    val favoriteIds by repo.favoriteIds.collectAsState()
    val isSyncing by repo.isSyncing.collectAsState()
    val isLoading by repo.isLoading.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Tutti") }
    var activeModalDiscount by remember { mutableStateOf<Discount?>(null) }

    val categories = remember(discounts) {
        val list = mutableListOf("Tutti")
        val unique = discounts.map { it.category }.distinct().filter { it.isNotBlank() }
        list.addAll(unique)
        list
    }

    val filteredDiscounts = remember(discounts, searchQuery, selectedCategory) {
        discounts.filter { d ->
            val matchCategory = selectedCategory == "Tutti" || d.category.equals(selectedCategory, ignoreCase = true)
            val matchSearch = searchQuery.isBlank() ||
                    d.title.contains(searchQuery, ignoreCase = true) ||
                    d.provider.contains(searchQuery, ignoreCase = true) ||
                    d.summary.contains(searchQuery, ignoreCase = true) ||
                    d.category.contains(searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgBase)
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Header sobrio allineato a sinistra + pulsante Sync
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Accent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "UniBo Perks",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                            Text(
                                text = "${filteredDiscounts.size} convenzioni disponibili",
                                color = TextMuted,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    repo.syncDiscountsFromGitHub()
                                }
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Accent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Aggiorna Sconti",
                                    tint = TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Barra di ricerca
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Cerca sconti (Apple, TPER, Copilot, Musei)...",
                                color = TextSubtle,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextSubtle,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Pulisci",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgSurface,
                            unfocusedContainerColor = BgSurface,
                            focusedBorderColor = BorderStrong,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chip categorie
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                onClick = {
                                    if (selectedCategory != cat) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedCategory = cat
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Accent else BgSurface,
                                border = BorderStroke(1.dp, if (isSelected) Accent else BorderSubtle)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) TextPrimary else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
            }
        },
        containerColor = BgBase,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && discounts.isEmpty()) {
                items(count = 5) {
                    DiscountCardSkeleton()
                }
            } else {
                items(
                    items = filteredDiscounts,
                    key = { it.id },
                    contentType = { "discount_card" }
                ) { discount ->
                    DiscountCard(
                        discount = discount,
                        isFavorite = favoriteIds.contains(discount.id),
                        onToggleFavorite = { repo.toggleFavorite(discount.id) },
                        onCardClick = { activeModalDiscount = discount }
                    )
                }

                if (filteredDiscounts.isEmpty()) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = BgSurface,
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = TextSubtle,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Nessuno sconto trovato",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Prova a cambiare parola chiave o categoria.",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                                if (searchQuery.isNotBlank() || selectedCategory != "Tutti") {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedButton(
                                        onClick = {
                                            searchQuery = ""
                                            selectedCategory = "Tutti"
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, BorderStrong),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = TextPrimary
                                        )
                                    ) {
                                        Text(
                                            text = "Reimposta filtri",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        activeModalDiscount?.let { discount ->
            DiscountDetailModal(
                discount = discount,
                isFavorite = favoriteIds.contains(discount.id),
                onToggleFavorite = { repo.toggleFavorite(discount.id) },
                onDismiss = { activeModalDiscount = null }
            )
        }
    }
}
