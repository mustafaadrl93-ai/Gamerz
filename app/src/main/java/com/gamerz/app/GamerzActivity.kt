package com.gamerz.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

private data class ActivityEvent(
    val label: String, val details: String, val timestamp: Long,
    val liked: Boolean, val followed: Boolean = false
)

@Composable
fun GamerzActivityScreen(posts: List<Map<String, Any>>, uid: String, db: FirebaseFirestore) {
    var events by remember { mutableStateOf(listOf<ActivityEvent>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember(uid) { mutableIntStateOf(0) }

    LaunchedEffect(uid, posts, refresh) {
        loading = true
        error = ""
        try {
            val result = mutableListOf<ActivityEvent>()
            for (post in posts.filter { it["uid"] == uid }.take(15)) {
                val postId = post["_id"]?.toString() ?: continue
                val game = post["gameName"]?.toString().orEmpty()
                val postRef = db.collection("posts").document(postId)
                val likes = postRef.collection("likes").orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(5).get().await().documents
                for (like in likes) {
                    if (like.getString("uid") != uid) {
                        result.add(ActivityEvent("إعجاب بمنشورك", "أعجب لاعب بمنشورك عن " + game,
                            like.getTimestamp("createdAt")?.seconds ?: 0L, true))
                    }
                }
                val comments = postRef.collection("comments").orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(5).get().await().documents
                for (comment in comments) {
                    if (comment.getString("uid") != uid) {
                        val name = comment.getString("username").orEmpty().ifBlank { "لاعب" }
                        result.add(ActivityEvent("تعليق جديد من " + name,
                            "على " + game + ": " + comment.getString("text").orEmpty().take(85),
                            comment.getTimestamp("createdAt")?.seconds ?: 0L, false))
                    }
                }
            }
            // Follow activity is stored as Firestore documents, so no push-service
            // credentials or paid functions are needed for this in-app inbox.
            val followers = db.collection("follows").whereEqualTo("targetUid", uid)
                .limit(50).get().await().documents
            for (follow in followers) {
                val followerUid = follow.getString("followerUid").orEmpty()
                if (followerUid.isNotBlank() && followerUid != uid) {
                    result.add(ActivityEvent(
                        "متابع جديد 🎮", "لاعب جديد يتابع ملفك في Gamerz",
                        follow.getTimestamp("createdAt")?.seconds ?: 0L,
                        liked = false, followed = true
                    ))
                }
            }
            events = result.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "تعذّر تحميل آخر النشاط"
        } finally {
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item { GamerzPageHeading("النشاط", "الإعجابات والتعليقات والمتابعات الجديدة") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { refresh++ }, enabled = !loading) {
                    Text("تحديث النشاط")
                }
            }
        }
        if (loading) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color(0xFFC271FF))
            } }
        } else if (error.isNotBlank()) {
            item { Text(error, color = Color(0xFFFFA3B3)) }
        } else if (events.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF251B34))) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null,
                            tint = Color(0xFFC98BFF), modifier = Modifier.size(45.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("لا توجد تفاعلات جديدة", fontWeight = FontWeight.Bold)
                        Text("عندما يتفاعل اللاعبون مع منشوراتك ستظهر هنا.",
                            color = Color(0xFFBCAACE), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            items(events) { event ->
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF261D35))) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (event.followed) Icons.Default.PersonAdd else if (event.liked) Icons.Default.Favorite else Icons.Default.ChatBubble,
                            contentDescription = null,
                            tint = if (event.liked) Color(0xFFFF608C) else if (event.followed) Color(0xFFFFD378) else Color(0xFFC789FF))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.label, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(event.details, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8C6E9))
                        }
                    }
                }
            }
        }
    }
}
