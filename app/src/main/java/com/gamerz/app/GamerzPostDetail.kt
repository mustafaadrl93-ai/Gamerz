package com.gamerz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * An original in-app game review view. Navigation is driven only by trusted
 * Firestore post documents; it never opens a user-controlled external URL.
 */
@Composable
fun GamerzPostDetail(post: Map<String, Any>?, onBack: () -> Unit) {
    val imageUrl = post?.get("imageUrl")?.toString().orEmpty()
        .replace("https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/")
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0C0B16))
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("العودة")
        }
        if (post == null) {
            Text("لم تعد هذه المراجعة متاحة", color = Color.White)
            return@Column
        }
        Text("ملف اللعبة", style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFD2A1FC), fontWeight = FontWeight.Bold)
        Text(post["gameName"]?.toString().orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold, color = Color.White)
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF21172F),
            border = BorderStroke(1.dp, Color(0xFF4B356A))) {
            Column {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "صورة اللعبة",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                        .background(Color(0xFF100D1A))
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(post["title"]?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    GamerzPlatformBadge(post["platform"]?.toString().orEmpty())
                    GamerzRatingStars((post["rating"] as? Number)?.toInt() ?: 0)
                    GamerzCompletionBar((post["completion"] as? Number)?.toInt() ?: 0)
                    if (post["platinum"] == true) Text("🏆 بلاتينيوم", color = Color(0xFFFFD587))
                }
            }
        }
        listOf(
            Triple("الإيجابيات", post["pros"]?.toString().orEmpty(), Color(0xFF87E9C0)),
            Triple("السلبيات", post["cons"]?.toString().orEmpty(), Color(0xFFFFB992))
        ).forEach { (heading, description, tone) ->
            Surface(shape = RoundedCornerShape(19.dp),
                color = Color(0xFF20182E),
                border = BorderStroke(1.dp, tone.copy(alpha = 0.4f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(heading, color = tone, fontWeight = FontWeight.ExtraBold)
                    Text(description.ifBlank { "لم يضف صاحب المراجعة تفاصيل." },
                        color = Color(0xFFF1E7F8))
                }
            }
        }
    }
}
