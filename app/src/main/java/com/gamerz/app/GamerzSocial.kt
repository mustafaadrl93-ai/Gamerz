package com.gamerz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun GamerzLikeButton(postId: String, uid: String, db: FirebaseFirestore) {
    val scope = rememberCoroutineScope()
    val collection = remember(postId) { db.collection("posts").document(postId).collection("likes") }
    var count by remember(postId) { mutableIntStateOf(0) }
    var liked by remember(postId, uid) { mutableStateOf(false) }
    var loading by remember(postId) { mutableStateOf(true) }
    var saving by remember(postId) { mutableStateOf(false) }
    var error by remember(postId) { mutableStateOf("") }

    LaunchedEffect(postId, uid) {
        try {
            count = collection.count().get(AggregateSource.SERVER).await().count.toInt()
            liked = collection.document(uid).get().await().exists()
        } catch (e: Exception) {
            error = "تعذر تحميل الإعجابات"
        } finally { loading = false }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = !saving && !loading && uid.isNotBlank(), onClick = {
            scope.launch {
                try {
                    saving = true
                    if (liked) {
                        collection.document(uid).delete().await()
                        liked = false
                        count = (count - 1).coerceAtLeast(0)
                    } else {
                        collection.document(uid).set(mapOf(
                            "uid" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )).await()
                        liked = true
                        count += 1
                    }
                    error = ""
                } catch (e: Exception) {
                    error = e.localizedMessage ?: "تعذر تحديث الإعجاب"
                } finally { saving = false }
            }
        }) {
            Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "إعجاب",
                tint = if (liked) Color(0xFFFF648B) else Color(0xFFCAB1E5))
            Spacer(Modifier.width(5.dp))
            Text(if (loading) "..." else count.toString(), color = Color(0xFFE1C9FA))
        }
        if (error.isNotBlank()) Text("!", color = Color(0xFFFF8F9D))
    }
}

@Composable
fun GamerzCommentsDialog(
    postId: String,
    uid: String,
    isOwner: Boolean,
    db: FirebaseFirestore,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val commentsRef = remember(postId) { db.collection("posts").document(postId).collection("comments") }
    var comments by remember(postId) { mutableStateOf(listOf<Map<String, Any>>()) }
    var draft by remember(postId) { mutableStateOf("") }
    var working by remember(postId) { mutableStateOf(false) }
    var error by remember(postId) { mutableStateOf("") }

    suspend fun reload() {
        comments = commentsRef.orderBy("createdAt", Query.Direction.ASCENDING).limit(75)
            .get().await().documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
            }
    }

    LaunchedEffect(postId) {
        try { reload() }
        catch (e: Exception) { error = e.localizedMessage ?: "تعذر تحميل التعليقات" }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("تعليقات اللاعبين", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                if (comments.isEmpty()) Text("لا توجد تعليقات بعد. ابدأ المحادثة!",
                    color = Color(0xFFBBA9CB), style = MaterialTheme.typography.bodySmall)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(comments) { comment ->
                        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF291F38)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(comment["username"]?.toString().orEmpty().ifBlank { "لاعب Gamerz" },
                                        fontWeight = FontWeight.Bold, color = Color(0xFFE3B9FF))
                                    if (comment["uid"] == uid || isOwner) {
                                        IconButton(enabled = !working, onClick = {
                                            scope.launch {
                                                try {
                                                    working = true
                                                    commentsRef.document(comment["_id"].toString()).delete().await()
                                                    reload()
                                                } catch (e: Exception) {
                                                    error = e.localizedMessage ?: "تعذر حذف التعليق"
                                                } finally { working = false }
                                            }
                                        }, modifier = Modifier.size(30.dp)) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف التعليق",
                                                tint = Color(0xFFFFA5A5), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                Text(comment["text"]?.toString().orEmpty(), color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    label = { Text("اكتب تعليقًا") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !working && draft.isNotBlank() && uid.isNotBlank(), onClick = {
                scope.launch {
                    try {
                        working = true
                        val username = db.collection("users").document(uid).get().await()
                            .getString("username").orEmpty()
                        require(username.isNotBlank()) { "أكمل ملفك الشخصي أولًا" }
                        commentsRef.add(mapOf(
                            "uid" to uid,
                            "username" to username,
                            "text" to draft.trim(),
                            "createdAt" to FieldValue.serverTimestamp()
                        )).await()
                        draft = ""
                        error = ""
                        reload()
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "تعذر إرسال التعليق"
                    } finally { working = false }
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("إرسال")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("إغلاق") } },
        shape = RoundedCornerShape(24.dp)
    )
}
