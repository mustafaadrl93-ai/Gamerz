package com.gamerz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.AccountCircle
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamerzCommentsDialog(
    postId: String, uid: String, isOwner: Boolean,
    db: FirebaseFirestore, onDismiss: () -> Unit, postTitle: String = ""
) {
    val scope = rememberCoroutineScope()
    val ref = remember(postId) { db.collection("posts").document(postId).collection("comments") }
    var comments by remember(postId) { mutableStateOf(listOf<Map<String, Any>>()) }
    var draft by remember(postId) { mutableStateOf("") }
    var replyTo by remember(postId) { mutableStateOf<Pair<String, String>?>(null) }
    var working by remember(postId) { mutableStateOf(false) }
    var loaded by remember(postId) { mutableStateOf(false) }
    var error by remember(postId) { mutableStateOf("") }
    suspend fun reload() {
        comments = ref.orderBy("createdAt", Query.Direction.DESCENDING).limit(75).get().await()
            .documents.mapNotNull { d -> d.data?.toMutableMap()?.apply { put("_id", d.id) } }.asReversed()
        loaded = true
    }
    LaunchedEffect(postId) {
        try { reload() } catch (e: Exception) {
            loaded = true
            error = e.localizedMessage ?: "تعذر تحميل التعليقات"
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = Color(0xFF181321),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("التعليقات", color = Color.White, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge)
                    Text(postTitle.ifBlank { "نقاش اللاعبين" }, maxLines = 1,
                        color = Color(0xFFB7A6C8), style = MaterialTheme.typography.bodySmall)
                }
                Surface(color = Color(0xFF342640), shape = RoundedCornerShape(12.dp)) {
                    Text(comments.size.toString(), color = Color(0xFFE5C8FF),
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
                }
            }
            HorizontalDivider(color = Color(0xFF3E304A))
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF9FAD),
                style = MaterialTheme.typography.bodySmall)
            if (!loaded) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFBD83FF))
                }
            } else if (comments.isEmpty()) {
                Column(Modifier.fillMaxWidth().height(170.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null,
                        modifier = Modifier.size(38.dp), tint = Color(0xFFC692FF))
                    Spacer(Modifier.height(9.dp))
                    Text("ابدأ النقاش عن هذه اللعبة", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("كن أول من يشارك رأيه", color = Color(0xFFB7A6C8))
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(comments, key = { it["_id"].toString() }) { c ->
                        val name = c["username"]?.toString().orEmpty().ifBlank { "لاعب Gamerz" }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Surface(color = Color(0xFF47305F), shape = CircleShape,
                                modifier = Modifier.size(38.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(name.take(1).uppercase(), color = Color.White,
                                        fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, color = Color(0xFFE8C6FF),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(7.dp))
                                    val date = (c["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                                    if (date != null) Text(
                                        java.text.SimpleDateFormat("dd MMM • HH:mm",
                                            java.util.Locale.getDefault()).format(date),
                                        color = Color(0xFF9C8DAE),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                                Surface(color = Color(0xFF2D2339),
                                    shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        if (c["replyTo"] is String) Text("↳ رد على تعليق",
                                            color = Color(0xFFBF8BF4),
                                            style = MaterialTheme.typography.labelSmall)
                                        Text(c["text"]?.toString().orEmpty(), color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { replyTo = c["_id"].toString() to name },
                                        contentPadding = PaddingValues(horizontal = 3.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) { Text("رد", color = Color(0xFFC7A0E9),
                                            style = MaterialTheme.typography.labelSmall) }
                                    if (c["uid"] == uid || isOwner) {
                                        TextButton(
                                            enabled = !working,
                                            modifier = Modifier.height(30.dp),
                                            contentPadding = PaddingValues(horizontal = 3.dp),
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        working = true
                                                        ref.document(c["_id"].toString()).delete().await()
                                                        reload()
                                                    } catch (e: Exception) {
                                                        error = e.localizedMessage ?: "تعذر الحذف"
                                                    } finally { working = false }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف",
                                                tint = Color(0xFFFF9FAE), modifier = Modifier.size(15.dp))
                                            Text(" حذف", color = Color(0xFFFF9FAE),
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (replyTo != null) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF30253D),
                    RoundedCornerShape(11.dp)).padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("الرد على " + replyTo!!.second, modifier = Modifier.weight(1f),
                        color = Color(0xFFD3ACF6), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { replyTo = null }) { Text("إلغاء") }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it.take(500) },
                    placeholder = { Text(if (replyTo == null) "اكتب تعليقك…" else "اكتب ردك…") },
                    modifier = Modifier.weight(1f), maxLines = 4,
                    shape = RoundedCornerShape(18.dp)
                )
                FilledIconButton(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !working && draft.isNotBlank() && uid.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF9131D6)),
                    onClick = {
                        scope.launch {
                            try {
                                working = true
                                val name = db.collection("users").document(uid).get().await()
                                    .getString("username").orEmpty()
                                require(name.isNotBlank()) { "أكمل ملفك الشخصي أولًا" }
                                val body = mutableMapOf<String, Any>(
                                    "uid" to uid, "username" to name, "text" to draft.trim(),
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                                replyTo?.let { body["replyTo"] = it.first }
                                ref.add(body).await()
                                draft = ""
                                replyTo = null
                                error = ""
                                reload()
                            } catch (e: Exception) {
                                error = e.localizedMessage ?: "تعذر إرسال التعليق"
                            } finally { working = false }
                        }
                    }
                ) {
                    if (working) CircularProgressIndicator(modifier = Modifier.size(23.dp),
                        color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun GamerzFollowButton(
    currentUid: String, targetUid: String, db: FirebaseFirestore,
    modifier: Modifier = Modifier, onChanged: () -> Unit = {}
) {
    if (currentUid.isBlank() || targetUid.isBlank() || currentUid == targetUid) return
    val scope = rememberCoroutineScope()
    val doc = remember(currentUid, targetUid) {
        db.collection("follows").document(currentUid + "_" + targetUid)
    }
    var following by remember(currentUid, targetUid) { mutableStateOf(false) }
    var busy by remember(currentUid, targetUid) { mutableStateOf(true) }
    var error by remember(currentUid, targetUid) { mutableStateOf(false) }
    LaunchedEffect(currentUid, targetUid) {
        try { following = doc.get().await().exists() }
        catch (_: Exception) { error = true }
        finally { busy = false }
    }
    OutlinedButton(
        modifier = modifier, enabled = !busy,
        shape = RoundedCornerShape(13.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (following) Color(0xFF30283D) else Color(0xFF7833BD),
            contentColor = Color.White),
        border = BorderStroke(1.dp, if (error) Color(0xFFFF9FAA) else Color(0xFFA675E3)),
        onClick = {
            scope.launch {
                try {
                    busy = true
                    if (following) doc.delete().await()
                    else doc.set(mapOf(
                        "followerUid" to currentUid, "targetUid" to targetUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )).await()
                    following = !following
                    error = false
                    onChanged()
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
    ) {
        Icon(if (following) Icons.Default.Check else Icons.Default.PersonAdd,
            contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (busy) "..." else if (following) "أتابعه" else "متابعة",
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GamerzFollowStats(profileUid: String, db: FirebaseFirestore,
    modifier: Modifier = Modifier, refreshKey: Int = 0) {
    var followers by remember(profileUid) { mutableIntStateOf(0) }
    var following by remember(profileUid) { mutableIntStateOf(0) }
    LaunchedEffect(profileUid, refreshKey) {
        if (profileUid.isBlank()) return@LaunchedEffect
        try {
            followers = db.collection("follows").whereEqualTo("targetUid", profileUid)
                .count().get(AggregateSource.SERVER).await().count.toInt()
            following = db.collection("follows").whereEqualTo("followerUid", profileUid)
                .count().get(AggregateSource.SERVER).await().count.toInt()
        } catch (_: Exception) { }
    }
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(followers.toString(), color = Color.White,
                fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            Text("المتابعون", color = Color(0xFFB9ADC9),
                style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(following.toString(), color = Color.White,
                fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            Text("أتابع", color = Color(0xFFB9ADC9),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamerzPublicProfileSheet(
    targetUid: String, currentUid: String, db: FirebaseFirestore,
    posts: List<Map<String, Any>>, onDismiss: () -> Unit,
    onBlockChanged: () -> Unit = {}
) {
    var profile by remember(targetUid) { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var refreshKey by remember(targetUid) { mutableIntStateOf(0) }
    LaunchedEffect(targetUid) {
        try { profile = db.collection("users").document(targetUid).get().await().data.orEmpty() }
        catch (_: Exception) { }
    }
    val name = profile["username"]?.toString().orEmpty().ifBlank { "لاعب Gamerz" }
    val avatar = profile["avatarUrl"]?.toString().orEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = Color(0xFF181321),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (avatar.isNotBlank()) AsyncImage(
                model = avatar.replace("https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/"),
                contentDescription = "الصورة الشخصية", contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(CircleShape))
            else Icon(Icons.Default.AccountCircle, contentDescription = null,
                modifier = Modifier.size(80.dp), tint = Color(0xFFD9A9FF))
            Text(name, style = MaterialTheme.typography.headlineSmall,
                color = Color.White, fontWeight = FontWeight.ExtraBold)
            GamerzPlatformBadge(profile["platform"]?.toString().orEmpty())
            GamerzFavoriteBadges(
                seriesName = profile["favoriteSeriesName"]?.toString().orEmpty(),
                seriesImageUrl = profile["favoriteSeriesImageUrl"]?.toString().orEmpty(),
                gameName = profile["favoriteGameName"]?.toString().orEmpty(),
                gameImageUrl = profile["favoriteGameImageUrl"]?.toString().orEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            GamerzPlatinumCounter(targetUid, db)
            GamerzOrbitRankPill(targetUid, db)
            GamerzFollowStats(targetUid, db, Modifier.fillMaxWidth(), refreshKey)
            GamerzFollowButton(currentUid, targetUid, db,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                onChanged = { refreshKey++ })
            GamerzBlockButton(currentUid, targetUid, db,
                onChanged = onBlockChanged)
            Text("منشورات اللاعب", modifier = Modifier.fillMaxWidth(),
                color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            val authored = posts.filter { it["uid"] == targetUid }.take(8)
            if (authored.isEmpty()) Text("لا توجد منشورات في المحتوى المحمّل",
                color = Color(0xFFB9ADC9))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 210.dp)) {
                items(authored) { post ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = post["imageUrl"]?.toString().orEmpty()
                                .replace("https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/"),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(51.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(post["title"]?.toString().orEmpty().ifBlank {
                                post["gameName"]?.toString().orEmpty() },
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(post["gameName"]?.toString().orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB9ADC9))
                            if (post["platinum"] == true &&
                                post["platform"] == "PlayStation" &&
                                (post["completion"] as? Number)?.toInt() == 100) {
                                Text("🏆 بلاتينيوم", color = Color(0xFFFFD078),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
