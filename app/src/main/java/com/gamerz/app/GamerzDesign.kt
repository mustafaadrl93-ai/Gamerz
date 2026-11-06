package com.gamerz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val violet = Color(0xFFB84CFF)
private val lavender = Color(0xFFE5C9FF)
private val muted = Color(0xFFBAA8CF)

@Composable
fun GamerzPageHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = muted)
    }
}

@Composable
fun GamerzRatingStars(
    rating: Int,
    modifier: Modifier = Modifier,
    onRatingChange: ((Int) -> Unit)? = null
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { number ->
            val star = if (number <= rating) Icons.Default.Star else Icons.Default.StarBorder
            if (onRatingChange != null) {
                IconButton(onClick = { onRatingChange(number) }, modifier = Modifier.size(41.dp)) {
                    Icon(
                        imageVector = star,
                        contentDescription = "تقييم $number من 5",
                        tint = if (number <= rating) Color(0xFFFFC943) else Color(0xFF7E718B),
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = star,
                    contentDescription = null,
                    tint = if (number <= rating) Color(0xFFFFC943) else Color(0xFF776A85),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(if (rating in 1..5) "$rating/5" else "بدون تقييم",
            color = lavender,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GamerzCompletionBar(value: Int, modifier: Modifier = Modifier) {
    val progress = value.coerceIn(0, 100)
    val gradient = when {
        progress >= 80 -> listOf(Color(0xFF8C34FF), Color(0xFF32E58A))
        progress >= 40 -> listOf(Color(0xFF7C3BFF), Color(0xFFCB73FF))
        else -> listOf(Color(0xFF8F39F5), Color(0xFFFF8C6B))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("نسبة إكمال اللعبة", style = MaterialTheme.typography.labelMedium, color = muted)
            Text("$progress%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = lavender)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFF3A2B49))) {
            if (progress > 0) {
                Box(Modifier.fillMaxWidth(progress / 100f).fillMaxHeight()
                    .clip(CircleShape).background(Brush.horizontalGradient(gradient)))
            }
        }
    }
}

@Composable
fun GamerzPlatformBadge(platform: String) {
    Surface(shape = RoundedCornerShape(50), color = Color(0xFF312046)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(16.dp), tint = violet)
            Text(platform.ifBlank { "Gamerz" }, style = MaterialTheme.typography.labelSmall, color = lavender)
        }
    }
}

@Composable
fun GamerzAccentButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF9131D6),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF3D3250)
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}
