package com.franco.uniboperks.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franco.uniboperks.data.model.Discount
import com.franco.uniboperks.ui.theme.*

@Composable
fun DiscountCard(
    discount: Discount,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(16.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, if (discount.isUniboOfficial) UniBoRed.copy(alpha = 0.6f) else DarkBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Category, Badges and Favorite Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Category chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DarkSurface,
                        border = BorderStroke(0.5.dp, DarkBorder)
                    ) {
                        Text(
                            text = discount.category,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (discount.isUniboOfficial) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = UniBoRed.copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, UniBoRed)
                        ) {
                            Text(
                                text = "UniBo Ufficiale",
                                color = UniBoRedLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Salva",
                        tint = if (isFavorite) UniBoGold else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Provider
            Text(
                text = discount.provider,
                color = UniBoGoldBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Title
            Text(
                text = discount.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Summary
            Text(
                text = discount.summary,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom bar: Discount Badge & Action button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Discount Badge (Golden pill)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = UniBoGold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, UniBoGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalOffer,
                            contentDescription = null,
                            tint = UniBoGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = discount.discountBadge,
                            color = UniBoGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Riscatta button
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(discount.url)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UniBoRedLight,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Riscatta",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
