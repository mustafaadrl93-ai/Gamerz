package com.gamerz.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private fun gamerzStoryImage(url: Any?): String =
    url?.toString().orEmpty()
        .replace("https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/")
        .replace("http://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/")

@Composable
fun GamerzStoryBubble(
    story: Map<String, Any>,
    storyCount: Int,
    db: FirebaseFirestore,
    currentUid: String,
    onClick: () -> Unit
) {
    val authorUid = story["uid"]?.toString().orEmpty()
    var name by remember(authorUid) { mutableStateOf("لاعب") }
    LaunchedEffect(authorUid) {
        if (authorUid.isNotBlank()) {
            try {
                name = db.collection("users").document(authorUid).get().await()
                    .getString("username").orEmpty().ifBlank { "لاعب" }
            } catch (_: Exception) { }
        }
    }
    Column(
        modifier = Modifier.width(87.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(78.dp)
            .border(3.dp, Brush.sweepGradient(listOf(
                Color(0xFFFFB85C), Color(0xFFE65CC7), Color(0xFF8756FF), Color(0xFFFFB85C)
            )), CircleShape).padding(4.dp)) {
            AsyncImage(
                model = gamerzStoryImage(story["imageUrl"]),
                contentDescription = "قصة " + name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
            if (storyCount > 1) {
                Surface(
                    color = Color(0xFF6C2BB3),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(storyCount.toString(), color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(if (authorUid == currentUid) "قصصك" else name,
            color = Color(0xFFECE2F8), maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GamerzStoryViewer(
    stories: List<Map<String, Any>>,
    startStoryId: String,
    currentUid: String,
    isOwner: Boolean,
    db: FirebaseFirestore,
    onDismiss: () -> Unit,
    onDeleteStory: (String) -> Unit
) {
    // Keep each player's stories together, oldest first within that player.
    val ordered = remember(stories) {
        val userIds = stories.map { it["uid"]?.toString().orEmpty() }.distinct()
        userIds.flatMap { owner ->
            stories.filter { it["uid"] == owner }
                .sortedBy { (it["createdAt"] as? Timestamp)?.seconds ?: 0L }
        }
    }
    if (ordered.isEmpty()) return
    val initialIndex = remember(startStoryId, ordered) {
        ordered.indexOfFirst { it["_id"]?.toString() == startStoryId }
            .coerceAtLeast(0)
    }
    var index by remember(startStoryId) { mutableIntStateOf(initialIndex) }
    val safeIndex = index.coerceIn(0, ordered.lastIndex)
    val current = ordered[safeIndex]
    val storyId = current["_id"]?.toString().orEmpty()
    val ownerUid = current["uid"]?.toString().orEmpty()
    val ownerStories = ordered.filter { it["uid"] == ownerUid }
    val segmentIndex = ownerStories.indexOfFirst { it["_id"] == current["_id"] }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    var progress by remember(storyId) { mutableFloatStateOf(0f) }
    var imageReady by remember(storyId) { mutableStateOf(false) }
    var imageError by remember(storyId) { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }
    var reporting by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var notice by remember { mutableStateOf("") }
    var authorName by remember(ownerUid) { mutableStateOf("لاعب Gamerz") }
    var authorAvatar by remember(ownerUid) { mutableStateOf("") }

    LaunchedEffect(ownerUid) {
        if (ownerUid.isNotBlank()) {
            try {
                val user = db.collection("users").document(ownerUid).get().await()
                authorName = user.getString("username").orEmpty().ifBlank { "لاعب Gamerz" }
                authorAvatar = user.getString("avatarUrl").orEmpty()
            } catch (_: Exception) { }
        }
    }

    fun next() {
        if (safeIndex < ordered.lastIndex) index = safeIndex + 1
        else onDismiss()
    }
    fun previous() {
        if (safeIndex > 0) index = safeIndex - 1
        else progress = 0f
    }

    LaunchedEffect(storyId, imageReady, paused, menuExpanded, deleteConfirmation, showReport) {
        if (!imageReady || paused || menuExpanded || deleteConfirmation || showReport) return@LaunchedEffect
        while (progress < 1f) {
            delay(50)
            progress = (progress + 0.01f).coerceAtMost(1f)
        }
        next()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                Modifier.fillMaxSize()
                    .offset(y = with(density) { dragY.toDp() })
                    .pointerInput(storyId) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                if (amount > 0 || dragY > 0f) {
                                    dragY = (dragY + amount).coerceAtLeast(0f)
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (dragY > 100.dp.toPx()) onDismiss()
                                else dragY = 0f
                            },
                            onDragCancel = { dragY = 0f }
                        )
                    }
            ) {
                AsyncImage(
                    model = gamerzStoryImage(current["imageUrl"]),
                    contentDescription = "قصة بملء الشاشة",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onLoading = { imageReady = false },
                    onSuccess = { imageReady = true; imageError = false },
                    onError = { imageReady = true; imageError = true }
                )
                if (!imageReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFE2B1FF)
                    )
                }
                if (imageError) {
                    Text("تعذر عرض الصورة",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color(0xB3000000),
                    0.23f to Color.Transparent,
                    0.65f to Color.Transparent,
                    1f to Color(0xB3000000)
                )))
                // Tap either side to navigate; holding a finger pauses the 5-second timer.
                Box(Modifier.fillMaxSize().pointerInput(storyId) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { pos ->
                            if (pos.x < size.width * 0.30f) previous()
                            else next()
                        }
                    )
                })
                Column(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .padding(top = 34.dp, start = 12.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ownerStories.forEachIndexed { position, _ ->
                            LinearProgressIndicator(
                                progress = {
                                    when {
                                        position < segmentIndex -> 1f
                                        position > segmentIndex -> 0f
                                        else -> progress
                                    }
                                },
                                modifier = Modifier.weight(1f).height(3.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color.White,
                                trackColor = Color(0x80FFFFFF),
                                drawStopIndicator = {}
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (authorAvatar.isNotBlank()) {
                            AsyncImage(
                                model = gamerzStoryImage(authorAvatar),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(39.dp).clip(CircleShape)
                                    .border(1.dp, Color.White, CircleShape)
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null,
                                modifier = Modifier.size(39.dp), tint = Color.White)
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(authorName, color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold)
                            Text("Gamerz • 5 ثوانٍ",
                                color = Color(0xFFEEE3F0),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "إعدادات القصة",
                                    tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                containerColor = Color(0xFF272034)
                            ) {
                                if (ownerUid == currentUid || isOwner) {
                                    DropdownMenuItem(
                                        text = { Text("حذف القصة", color = Color(0xFFFFA7B3)) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null,
                                            tint = Color(0xFFFFA7B3)) },
                                        onClick = {
                                            menuExpanded = false
                                            deleteConfirmation = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("الإبلاغ عن القصة") },
                                        leadingIcon = { Icon(Icons.Default.Flag, null) },
                                        onClick = {
                                            menuExpanded = false
                                            reportText = ""
                                            showReport = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("مشاركة الرابط") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = {
                                        menuExpanded = false
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT,
                                                gamerzStoryImage(current["imageUrl"]))
                                        }
                                        context.startActivity(Intent.createChooser(intent, "مشاركة القصة"))
                                    }
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق القصة",
                                tint = Color.White)
                        }
                    }
                }
                val caption = current["caption"]?.toString().orEmpty()
                if (caption.isNotBlank()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 95.dp),
                        color = Color(0x99000000),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(caption, modifier = Modifier.padding(15.dp),
                            color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (notice.isNotBlank()) Text(notice, color = Color(0xFFFFD1DD))
                    Icon(Icons.Default.KeyboardArrowDown,
                        contentDescription = "اسحب للأسفل للإغلاق",
                        tint = Color(0xFFE5DBEB),
                        modifier = Modifier.size(28.dp))
                    Text("اسحب للأسفل للإغلاق", color = Color(0xFFD2C7D8),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (deleteConfirmation) {
            AlertDialog(
                onDismissRequest = { deleteConfirmation = false },
                title = { Text("حذف القصة؟") },
                text = { Text("سيتم حذف هذه الصورة من قصص Gamerz ولا يمكن استعادتها.") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteConfirmation = false
                        onDeleteStory(storyId)
                    }) { Text("حذف", color = Color(0xFFE8556C)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmation = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
        if (showReport) {
            AlertDialog(
                onDismissRequest = { if (!reporting) showReport = false },
                title = { Text("الإبلاغ عن قصة") },
                text = {
                    Column {
                        Text("أخبرنا لماذا يجب مراجعة هذه القصة")
                        OutlinedTextField(
                            reportText,
                            { reportText = it.take(300) },
                            label = { Text("سبب البلاغ") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !reporting && reportText.trim().isNotEmpty(),
                        onClick = {
                            scope.launch {
                                try {
                                    reporting = true
                                    db.collection("storyReports").add(mapOf(
                                        "storyId" to storyId,
                                        "storyOwnerUid" to ownerUid,
                                        "reporterUid" to currentUid,
                                        "reason" to reportText.trim(),
                                        "status" to "open",
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )).await()
                                    showReport = false
                                    notice = "تم إرسال البلاغ للإدارة"
                                } catch (e: Exception) {
                                    notice = e.localizedMessage ?: "تعذر إرسال البلاغ"
                                    showReport = false
                                } finally { reporting = false }
                            }
                        }
                    ) { Text("إرسال") }
                },
                dismissButton = {
                    TextButton(onClick = { showReport = false }, enabled = !reporting) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}
