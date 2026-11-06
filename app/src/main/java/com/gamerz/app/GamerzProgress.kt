package com.gamerz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

private val gamerzViolet = Color(0xFFC99BF9)
private val gamerzGold = Color(0xFFFFCD70)

private fun platinumGames(posts: List<Map<String, Any>>): Int =
    posts.filter {
        it["platinum"] == true && it["platform"] == "PlayStation" &&
            (it["completion"] as? Number)?.toInt() == 100
    }.map {
        it["gameName"]?.toString().orEmpty().trim().lowercase()
    }.filter { it.isNotEmpty() }.distinct().size

@Composable
fun GamerzPlatinumCounter(
    profileUid: String, db: FirebaseFirestore,
    modifier: Modifier = Modifier
) {
    var count by remember(profileUid) { mutableIntStateOf(0) }
    LaunchedEffect(profileUid) {
        if (profileUid.isBlank()) return@LaunchedEffect
        try {
            val authored = db.collection("posts").whereEqualTo("uid", profileUid)
                .limit(500).get().await().documents.mapNotNull { it.data }
            count = platinumGames(authored)
        } catch (_: Exception) { }
    }
    Surface(modifier, shape = RoundedCornerShape(14.dp),
        color = Color(0xFF44301E),
        border = BorderStroke(1.dp, gamerzGold)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = gamerzGold,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(4.dp))
            Text("بلاتينيوم", color = Color(0xFFFFE4B1),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

// Kept for existing screen callers. Orbit is the new achievements experience.
@Composable
fun GamerzAchievementsPanel(
    profileUid: String, db: FirebaseFirestore, modifier: Modifier = Modifier
) {
    GamerzOrbitRankHub(profileUid, db, modifier)
}

@Composable
fun GamerzSaveButton(uid: String, postId: String, db: FirebaseFirestore) {
    val doc = remember(uid, postId) {
        db.collection("savedPosts").document(uid).collection("items").document(postId)
    }
    val scope = rememberCoroutineScope()
    var saved by remember(uid, postId) { mutableStateOf(false) }
    var busy by remember(uid, postId) { mutableStateOf(false) }
    var error by remember(uid, postId) { mutableStateOf(false) }
    LaunchedEffect(uid, postId) {
        if (uid.isBlank() || postId.isBlank()) return@LaunchedEffect
        try { saved = doc.get().await().exists() }
        catch (_: Exception) { error = true }
    }
    IconButton(enabled = !busy && uid.isNotBlank(), onClick = {
        scope.launch {
            try {
                busy = true
                if (saved) doc.delete().await()
                else doc.set(mapOf(
                    "uid" to uid, "postId" to postId,
                    "createdAt" to FieldValue.serverTimestamp()
                )).await()
                saved = !saved
                error = false
            } catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }) {
        Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = if (saved) "إزالة من المحفوظات" else "حفظ المنشور",
            tint = if (error) Color(0xFFFF99A5) else if (saved) gamerzGold else gamerzViolet)
    }
}

@Composable
fun GamerzSavedScreen(
    uid: String, db: FirebaseFirestore,
    blockedUsers: Set<String>,
    onProfile: (String) -> Unit
) {
    var savedPosts by remember(uid) { mutableStateOf(listOf<Map<String, Any>>()) }
    var loading by remember(uid) { mutableStateOf(true) }
    var message by remember(uid) { mutableStateOf("") }
    var refresh by remember(uid) { mutableIntStateOf(0) }
    LaunchedEffect(uid, refresh) {
        try {
            loading = true
            val entries = db.collection("savedPosts").document(uid)
                .collection("items").limit(100).get().await().documents
            val resolved = mutableListOf<Map<String, Any>>()
            for (entry in entries) {
                val postId = entry.id
                val post = db.collection("posts").document(postId).get().await()
                if (post.exists()) {
                    val data = post.data?.toMutableMap() ?: continue
                    data["_id"] = postId
                    resolved.add(data)
                }
            }
            savedPosts = resolved
            message = ""
        } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحميل المحفوظات" }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🔖 المنشورات المحفوظة",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { refresh++ }) { Text("تحديث") }
            }
            Text("احفظ أي منشور من الرئيسية لتعود إليه لاحقًا",
                color = Color(0xFFBDAACB))
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (message.isNotBlank()) item { Text(message, color = Color(0xFFFFA4B5)) }
        if (savedPosts.isEmpty() && !loading) item {
            Text("لم تحفظ أي منشور بعد.", color = Color(0xFFCEB7DF))
        }
        items(savedPosts.filter {
            it["uid"]?.toString().orEmpty() !in blockedUsers
        }, key = { it["_id"].toString() }) { post ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF231A30)),
                shape = RoundedCornerShape(19.dp)) {
                Row(Modifier.fillMaxWidth().padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = post["imageUrl"]?.toString().orEmpty()
                            .replace("https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/"),
                        contentDescription = "صورة اللعبة", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(78.dp))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(post["title"]?.toString().orEmpty().ifBlank {
                            post["gameName"]?.toString().orEmpty()
                        }, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("🎮 " + post["gameName"]?.toString().orEmpty(),
                            color = Color(0xFFC2A4D8))
                        TextButton(onClick = {
                            val postUid = post["uid"]?.toString().orEmpty()
                            if (postUid.isNotBlank()) onProfile(postUid)
                        }) { Text("عرض اللاعب") }
                    }
                    GamerzSaveButton(uid, post["_id"].toString(), db)
                }
            }
        }
    }
}

@Composable
fun GamerzBlockButton(
    uid: String, targetUid: String, db: FirebaseFirestore, onChanged: () -> Unit = {}
) {
    if (uid.isBlank() || targetUid.isBlank() || uid == targetUid) return
    val doc = remember(uid, targetUid) {
        db.collection("blocks").document(uid).collection("users").document(targetUid)
    }
    val scope = rememberCoroutineScope()
    var blocked by remember(uid, targetUid) { mutableStateOf(false) }
    var busy by remember(uid, targetUid) { mutableStateOf(false) }
    LaunchedEffect(uid, targetUid) {
        try { blocked = doc.get().await().exists() }
        catch (_: Exception) { }
    }
    OutlinedButton(
        enabled = !busy,
        shape = RoundedCornerShape(12.dp),
        onClick = {
            scope.launch {
                try {
                    busy = true
                    if (blocked) {
                        doc.delete().await()
                        blocked = false
                    } else {
                        doc.set(mapOf(
                            "ownerUid" to uid, "blockedUid" to targetUid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )).await()
                        blocked = true
                        // Remove both existing follow links once the block is saved.
                        try {
                            for (followId in listOf(
                                uid + "_" + targetUid, targetUid + "_" + uid
                            )) {
                                val link = db.collection("follows").document(followId)
                                if (link.get().await().exists()) link.delete().await()
                            }
                        } catch (_: Exception) { }
                    }
                    onChanged()
                } catch (_: Exception) { }
                finally { busy = false }
            }
        }
    ) {
        Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (blocked) "إلغاء الحظر" else "حظر اللاعب")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamerzLibraryScreen(uid: String, db: FirebaseFirestore) {
    var games by remember(uid) { mutableStateOf(listOf<Map<String, Any>>()) }
    var loading by remember(uid) { mutableStateOf(true) }
    var message by remember(uid) { mutableStateOf("") }
    var addGame by remember { mutableStateOf(false) }
    var gameName by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("PlayStation") }
    var status by remember { mutableStateOf("backlog") }
    var filter by remember { mutableStateOf("all") }
    var busy by remember { mutableStateOf(false) }
    var revision by remember(uid) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uid, revision) {
        try {
            loading = true
            games = db.collection("users").document(uid).collection("library")
                .limit(200).get().await().documents.mapNotNull { doc ->
                    doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
                }
            message = ""
        } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحميل المكتبة" }
        finally { loading = false }
    }
    val filtered = if (filter == "all") games else games.filter { it["status"] == filter }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("🎮 مكتبة ألعابك", style = MaterialTheme.typography.headlineSmall,
                color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text("أضف الألعاب التي تخطط لها أو تلعبها أو أنهيتها",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCEB8DB))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addGame = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة لعبة")
                }
                OutlinedButton(onClick = { revision++ }) { Text("تحديث") }
            }
            Text(games.size.toString() + " لعبة في مكتبتك",
                color = gamerzGold, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(
                    "all" to "الكل", "playing" to "ألعبها",
                    "completed" to "أنهيتها", "backlog" to "لاحقًا"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = filter == id, onClick = { filter = id },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (message.isNotBlank()) item { Text(message, color = Color(0xFFFFA2B2)) }
        if (filtered.isEmpty() && !loading) item {
            Text("لا توجد ألعاب في هذا القسم. أضف أول لعبة!",
                color = Color(0xFFB7A8C9))
        }
        items(filtered, key = { it["_id"].toString() }) { game ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF261B36)),
                shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎮", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game["name"]?.toString().orEmpty(),
                            color = Color.White, fontWeight = FontWeight.Bold)
                        Text(game["platform"]?.toString().orEmpty() + " • " +
                            when (game["status"]) {
                                "playing" -> "ألعبها الآن"
                                "completed" -> "مكتملة 💯"
                                else -> "في الانتظار"
                            }, color = Color(0xFFCDB8DC),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    var statusMenu by remember(game["_id"]) { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { statusMenu = true }) {
                            Icon(Icons.Default.Edit, "تغيير حالة اللعبة",
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("الحالة")
                        }
                        DropdownMenu(expanded = statusMenu,
                            onDismissRequest = { statusMenu = false }) {
                            listOf(
                                "backlog" to "لاحقًا", "playing" to "ألعبها",
                                "completed" to "أنهيتها"
                            ).forEach { (newStatus, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        statusMenu = false
                                        scope.launch {
                                            try {
                                                db.collection("users").document(uid)
                                                    .collection("library")
                                                    .document(game["_id"].toString())
                                                    .update("status", newStatus).await()
                                                revision++
                                            } catch (e: Exception) {
                                                message = e.localizedMessage ?: "فشل تحديث الحالة"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                db.collection("users").document(uid).collection("library")
                                    .document(game["_id"].toString()).delete().await()
                                revision++
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "فشل الحذف"
                            }
                        }
                    }) {
                        Icon(Icons.Default.DeleteOutline, "حذف اللعبة",
                            tint = Color(0xFFFFA9B7))
                    }
                }
            }
        }
    }
    if (addGame) {
        AlertDialog(
            onDismissRequest = { if (!busy) addGame = false },
            title = { Text("أضف لعبة للمكتبة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(gameName, { gameName = it.take(80) },
                        label = { Text("اسم اللعبة") }, singleLine = true)
                    Text("المنصة")
                    listOf("PlayStation", "Xbox", "PC").forEach { choice ->
                        FilterChip(
                            selected = platform == choice,
                            onClick = { platform = choice },
                            label = { Text(choice) }
                        )
                    }
                    Text("حالة اللعبة")
                    listOf(
                        "backlog" to "لاحقًا", "playing" to "ألعبها",
                        "completed" to "أنهيتها"
                    ).forEach { (key, label) ->
                        FilterChip(selected = status == key,
                            onClick = { status = key },
                            label = { Text(label) })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && gameName.trim().isNotEmpty(),
                    onClick = {
                        scope.launch {
                            try {
                                busy = true
                                db.collection("users").document(uid)
                                    .collection("library").add(mapOf(
                                        "uid" to uid,
                                        "name" to gameName.trim(),
                                        "platform" to platform,
                                        "status" to status,
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )).await()
                                gameName = ""
                                addGame = false
                                revision++
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "تعذر إضافة اللعبة"
                            } finally { busy = false }
                        }
                    }) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { addGame = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
