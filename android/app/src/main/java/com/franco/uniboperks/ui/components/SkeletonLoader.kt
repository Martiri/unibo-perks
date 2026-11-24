package com.franco.uniboperks.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.franco.uniboperks.ui.theme.BgElevated
import com.franco.uniboperks.ui.theme.BgSurface
import com.franco.uniboperks.ui.theme.BorderSubtle

@Composable
fun DiscountCardSkeleton(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "skeleton_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(12.dp)
                    .graphicsLayer { alpha = pulseAlpha }
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgElevated)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(18.dp)
                    .graphicsLayer { alpha = pulseAlpha }
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgElevated)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(13.dp)
                    .graphicsLayer { alpha = pulseAlpha }
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgElevated)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(13.dp)
                    .graphicsLayer { alpha = pulseAlpha }
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgElevated)
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(30.dp)
                        .graphicsLayer { alpha = pulseAlpha }
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgElevated)
                )
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(36.dp)
                        .graphicsLayer { alpha = pulseAlpha }
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgElevated)
                )
            }
        }
    }
}
