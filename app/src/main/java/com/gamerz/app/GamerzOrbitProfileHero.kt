package com.gamerz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
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
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun GamerzOrbitProfileHero(
    username: String, email: String, avatarUrl: String,
    platform: String, uid: String, db: FirebaseFirestore
) {
    var membershipTier by remember(uid) { mutableStateOf("") }
    DisposableEffect(uid, db) {
        val registration = db.collection("entitlements").document(uid)
            .addSnapshotListener { snapshot, _ ->
                val expiresAt = snapshot?.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                membershipTier = if (snapshot?.getBoolean("active") == true &&
                    expiresAt > System.currentTimeMillis()) {
                    snapshot.getString("tier").orEmpty()
                } else ""
            }
        onDispose { registration.remove() }
    }
    val founderTone = when (membershipTier) {
        "gamerz_sadeem" -> Color(0xFFFFD58C)
        "gamerz_wamda" -> Color(0xFFCEA4FF)
        else -> Color(0xFF71538D)
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF21172E),
        border = BorderStroke(1.5.dp, founderTone)
    ) {
        Column(Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(
                Color(0xFF322143), Color(0xFF19132A), Color(0xFF191D31)
            )))
            .padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Text("✦ PLAYER IDENTITY", color = Color(0xFFE0B8FF),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                Text("GAMERZ / 02", color = Color(0xFFAA9ABA),
                    style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(86.dp)
                    .border(2.dp, Brush.sweepGradient(listOf(
                        Color(0xFFFFDB92), Color(0xFFBA86FF),
                        Color(0xFF7DCDEE), Color(0xFFFFDB92)
                    )), CircleShape).padding(4.dp)) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl
                                .replace("https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/"),
                            contentDescription = "الصورة الشخصية",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(Icons.Default.AccountCircle,
                            contentDescription = "الصورة الافتراضية",
                            modifier = Modifier.fillMaxSize(),
                            tint = Color(0xFFC9A2EE))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(username.ifBlank { "لاعب Gamerz" },
                        color = Color.White, fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    GamerzPlatformBadge(platform)
                    if (membershipTier.isNotBlank()) {
                        Surface(
                            color = if (membershipTier == "gamerz_sadeem")
                                Color(0xFF49371D) else Color(0xFF3D2558),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                if (membershipTier == "gamerz_sadeem")
                                    "✧ داعم Gamerz • سديم" else "✦ داعم Gamerz • ومضة",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                color = founderTone,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Text(email, color = Color(0xFFBFAFD1),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = Color(0xFF604A6E))
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GamerzOrbitRankPill(uid, db)
                GamerzPlatinumCounter(uid, db)
            }
        }
    }
}
