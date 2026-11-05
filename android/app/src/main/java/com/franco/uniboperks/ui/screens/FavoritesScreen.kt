package com.franco.uniboperks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                    .background(DarkBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "I Tuoi Sconti Salvati",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${savedDiscounts.size} convenzioni conservate nei preferiti",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        containerColor = DarkBackground,
        modifier = modifier
    ) { innerPadding ->
        if (savedDiscounts.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Nessun preferito salvato",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tocca l'icona segnalibro su qualsiasi sconto\nper salvarlo in questa schermata.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
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
