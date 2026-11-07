package com.gamerz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private val favoritesSurface = Color(0xFF241B35)
private val favoritesInk = Color(0xFFFEF7FF)
private val seriesTone = Color(0xFFCEA3FF)
private val gameTone = Color(0xFFFFD48C)

@Composable
private fun GamerzFavoriteTile(
    title: String,
    value: String,
    imageUrl: String,
    isSeries: Boolean,
    showEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isSeries) seriesTone else gameTone
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = favoritesSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier.size(62.dp)
                    .border(2.dp, Brush.sweepGradient(listOf(accent, Color.White, accent)), CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl.replace(
                            "https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/"
                        ),
                        contentDescription = "صورة " + title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(
                        if (isSeries) Icons.Default.AutoAwesome else Icons.Default.SportsEsports,
                        contentDescription = null, tint = accent,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text(title, color = accent, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value.ifBlank { if (showEmpty) "أضف اختيارك" else "—" },
                color = if (value.isBlank()) Color(0xFFB8A9C9) else favoritesInk,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Two original, optional circular favorite badges, deliberately smaller than the avatar. */
@Composable
fun GamerzFavoriteBadges(
    seriesName: String,
    seriesImageUrl: String,
    gameName: String,
    gameImageUrl: String,
    showEmpty: Boolean = false,
    modifier: Modifier = Modifier
) {
    val showSeries = showEmpty || seriesName.isNotBlank() || seriesImageUrl.isNotBlank()
    val showGame = showEmpty || gameName.isNotBlank() || gameImageUrl.isNotBlank()
    if (!showSeries && !showGame) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("✦ اختياراتي", color = Color(0xFFF0E2FF),
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showSeries) GamerzFavoriteTile(
                "السلسلة المفضلة", seriesName, seriesImageUrl,
                isSeries = true, showEmpty = showEmpty, modifier = Modifier.weight(1f)
            )
            if (showGame) GamerzFavoriteTile(
                "اللعبة المفضلة", gameName, gameImageUrl,
                isSeries = false, showEmpty = showEmpty, modifier = Modifier.weight(1f)
            )
        }
    }
}
