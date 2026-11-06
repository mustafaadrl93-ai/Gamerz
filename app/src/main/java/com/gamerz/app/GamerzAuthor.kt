package com.gamerz.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@Composable
fun GamerzPostAuthor(
    uid: String,
    fallbackPlatform: String,
    db: FirebaseFirestore,
    modifier: Modifier = Modifier
) {
    var name by remember(uid) { mutableStateOf("لاعب Gamerz") }
    var platform by remember(uid, fallbackPlatform) { mutableStateOf(fallbackPlatform) }
    var avatarUrl by remember(uid) { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            try {
                val profile = db.collection("users").document(uid).get().await()
                name = profile.getString("username").orEmpty().ifBlank { "لاعب Gamerz" }
                platform = profile.getString("platform").orEmpty().ifBlank { fallbackPlatform }
                avatarUrl = profile.getString("avatarUrl").orEmpty()
            } catch (_: Exception) {
                // Keep a useful fallback if a profile is missing or offline.
            }
        }
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(model = avatarUrl, contentDescription = "صورة " + name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(43.dp).border(2.dp, Color(0xFFC36CFF), CircleShape)
                    .padding(3.dp).clip(CircleShape))
        } else {
            Icon(Icons.Default.AccountCircle, contentDescription = null,
                tint = Color(0xFFB78AFF), modifier = Modifier.size(43.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(name, fontWeight = FontWeight.Bold, maxLines = 1,
                style = MaterialTheme.typography.titleSmall, color = Color.White)
            GamerzPlatformBadge(platform)
        }
    }
}
