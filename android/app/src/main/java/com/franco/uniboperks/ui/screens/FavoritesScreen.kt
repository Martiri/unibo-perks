package com.franco.uniboperks.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franco.uniboperks.data.model.Discount
import com.franco.uniboperks.data.repository.DiscountsRepository
import com.franco.uniboperks.ui.components.DiscountCard
import com.franco.uniboperks.ui.components.DiscountDetailModal
import com.franco.uniboperks.ui.theme.*

@Composable
fun FavoritesScreen(
    repo: DiscountsRepository,
    modifier: Modifier = Modifier
) {
    val discounts by repo.discounts.collectAsState()
    val favoriteIds by repo.favoriteIds.collectAsState()

    var activeModalDiscount by remember { mutableStateOf<Discount?>(null) }

    val savedDiscounts = remember(discounts, favoriteIds) {
        discounts.filter { favoriteIds.contains(it.id) }
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Preferiti",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "${savedDiscounts.size} convenzioni salvate",
                        color = TextMuted,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
            }
        },
        containerColor = BgBase,
        modifier = modifier
    ) { innerPadding ->
        if (savedDiscounts.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = BgSurface,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = TextSubtle,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nessun preferito salvato",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tocca il segnalibro su qualsiasi sconto per tenerlo a portata di mano.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
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
                items(
                    items = savedDiscounts,
                    key = { it.id },
                    contentType = { "favorite_card" }
                ) { discount ->
                    DiscountCard(
                        discount = discount,
                        isFavorite = true,
                        onToggleFavorite = { repo.toggleFavorite(discount.id) },
                        onCardClick = { activeModalDiscount = discount }
                    )
                }
            }
        }

        activeModalDiscount?.let { discount ->
            DiscountDetailModal(
                discount = discount,
                isFavorite = true,
                onToggleFavorite = { repo.toggleFavorite(discount.id) },
                onDismiss = { activeModalDiscount = null }
            )
        }
    }
}
