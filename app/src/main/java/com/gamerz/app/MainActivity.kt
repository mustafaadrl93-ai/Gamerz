package com.gamerz.app

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val UPLOADCARE_PUBLIC_KEY = "a019be01bbe664bae2db"
private const val OWNER_UID = "NdoyBz3UMcWGWQ3DhuwrIbVbZvl2"

private fun displayImageUrl(value: Any?): String {
    val url = value?.toString()?.trim().orEmpty()
    return url.replace("https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/")
        .replace("http://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/")
}


private suspend fun uploadImageToUploadcare(bytes: ByteArray): Pair<String, String> = withContext(Dispatchers.IO) {
    val boundary = "----GamerzUpload" + System.currentTimeMillis()
    val connection = (URL("https://upload.uploadcare.com/base/").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 30000
        readTimeout = 60000
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    }
    try {
        DataOutputStream(connection.outputStream).use { out ->
            fun field(name: String, value: String) {
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.write(value.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n")
            }
            field("UPLOADCARE_PUB_KEY", UPLOADCARE_PUBLIC_KEY)
            field("UPLOADCARE_STORE", "1")
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"gamerz.jpg\"\r\n")
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            out.write(bytes)
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        check(status in 200..299) { "Image upload failed ($status): $response" }
        val fileId = JSONObject(response).getString("file")
        check(fileId.matches(Regex("[a-f0-9-]{20,40}"))) { "استجابة غير صالحة من خدمة الصور" }
        Pair("https://26vsxjozef.ucarecd.net/$fileId/", "uploadcare/$fileId")
    } finally {
        connection.disconnect()
    }
}

private fun readCompressedImage(context: android.content.Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsInput = resolver.openInputStream(uri) ?: error("تعذر قراءة الصورة")
    boundsInput.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ملف الصورة غير صالح" }

    // Decode a reduced bitmap before allocating memory: modern camera photos
    // may exceed 30 megapixels, which previously caused upload crashes.
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 2000) sample *= 2
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, options)
    } ?: error("تعذر فتح الصورة")
    val scale = minOf(1f, 1600f / bitmap.width, 1600f / bitmap.height)
    val resized = if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else bitmap
    return try {
        ByteArrayOutputStream().use { output ->
            check(resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output)) { "فشل ضغط الصورة" }
            val bytes = output.toByteArray()
            require(bytes.size <= 8 * 1024 * 1024) { "الصورة كبيرة جدًا بعد الضغط" }
            bytes
        }
    } finally {
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GamerzApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamerzApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUid = auth.currentUser?.uid.orEmpty()
    val isOwner = currentUid == OWNER_UID

    var authMode by remember { mutableStateOf("login") }
    var platform by remember { mutableStateOf("") }
    var selectedPlatforms by remember { mutableStateOf(setOf<String>()) }
    var avatarUrl by remember { mutableStateOf("") }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var game by remember { mutableStateOf("") }
    var postTitle by remember { mutableStateOf("") }
    var completion by remember { mutableStateOf("0") }
    var rating by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var storyTarget by remember { mutableStateOf<Map<String, Any>?>(null) }
    var review by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var storyUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var storyCaption by remember { mutableStateOf("") }
    var storyUploadCount by remember { mutableIntStateOf(0) }
    var feedMode by remember { mutableStateOf("forYou") }
    var followedUsers by remember { mutableStateOf(setOf<String>()) }
    var playerResults by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var loggedIn by remember { mutableStateOf(auth.currentUser != null) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("feed") }
    var posts by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var stories by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var reports by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var storyReports by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var reportTarget by remember { mutableStateOf<Map<String, Any>?>(null) }
    var commentTarget by remember { mutableStateOf<Map<String, Any>?>(null) }
    var viewedProfileUid by remember { mutableStateOf<String?>(null) }
    var reportReason by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }
    val storyPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        storyUris = uris.take(10)
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && currentUid.isNotBlank() && !busy) scope.launch {
            try {
                busy = true
                val bytes = readCompressedImage(context, uri)
                val (url, path) = uploadImageToUploadcare(bytes)
                db.collection("users").document(currentUid).update(mapOf("avatarUrl" to url, "avatarStoragePath" to path)).await()
                avatarUrl = url
                message = "تم تحديث صورتك الشخصية"
            } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تغيير الصورة الشخصية" }
            finally { busy = false }
        }
    }

    suspend fun refreshFeed() {
        posts = db.collection("posts")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50).get().await().documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
            }
    }
    suspend fun refreshStories() {
        val now = Timestamp.now().seconds
        stories = db.collection("stories")
            .orderBy("expiresAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100).get().await().documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
            }.filter { (it["expiresAt"] as? Timestamp)?.seconds?.let { expiry -> expiry > now } == true }
    }
    suspend fun refreshReports() {
        if (!isOwner) return
        reports = db.collection("reports").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100).get().await().documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
            }
        storyReports = db.collection("storyReports").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100).get().await().documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
            }
    }
    suspend fun refreshFollowing() {
        if (currentUid.isBlank()) return
        try {
            followedUsers = db.collection("follows")
                .whereEqualTo("followerUid", currentUid)
                .limit(300).get().await().documents.mapNotNull {
                    it.getString("targetUid")
                }.toSet()
        } catch (_: Exception) { /* Following feed remains available offline. */ }
    }
    fun refreshAll() {
        scope.launch {
            try {
                busy = true
                refreshFeed()
                refreshStories()
                refreshFollowing()
                if (isOwner) refreshReports()
            } catch (e: Exception) {
                message = e.localizedMessage ?: "تعذر تحديث المحتوى"
            } finally { busy = false }
        }
    }

    fun pullToRefresh() {
        if (refreshing || busy) return
        scope.launch {
            refreshing = true
            try {
                refreshFeed()
                refreshStories()
                refreshFollowing()
            } catch (e: Exception) {
                message = e.localizedMessage ?: "تعذر تحديث الصفحة"
            } finally { refreshing = false }
        }
    }

    val visiblePosts = if (feedMode == "following") {
        posts.filter { it["uid"]?.toString().orEmpty() in followedUsers }
    } else posts
    LaunchedEffect(page, searchQuery) {
        if (page == "search" && searchQuery.trim().length >= 2) {
            delay(350)
            try {
                val query = searchQuery.trim()
                playerResults = db.collection("users").orderBy("username")
                    .startAt(query).endAt(query + "\uf8ff").limit(15).get().await()
                    .documents.mapNotNull { d ->
                        d.data?.toMutableMap()?.apply { put("_uid", d.id) }
                    }
            } catch (_: Exception) { playerResults = emptyList() }
        } else playerResults = emptyList()
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFAD65FF),
        secondary = Color(0xFFD5BEFF),
        background = Color(0xFF090711),
        surface = Color(0xFF181521)
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!loggedIn) {
                Column(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF25103F), Color(0xFF0A0713), Color(0xFF150921)))
                    ).verticalScroll(rememberScrollState()).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(20.dp))
                    Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF3B1660), shadowElevation = 12.dp) {
                        Icon(Icons.Default.SportsEsports, contentDescription = "Gamerz", tint = Color(0xFFE5B8FF),
                            modifier = Modifier.padding(18.dp).size(62.dp))
                    }
                    Text("Gamerz", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = Color(0xFFEBDAFF))
                    Text("قيمرز • مجتمع اللاعبين", color = Color(0xFFC8ADE0))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { authMode = "login"; message = "" }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (authMode == "login") Color(0xFF902FD7) else Color(0xFF30203F)),
                            shape = RoundedCornerShape(14.dp)) { Text("تسجيل الدخول") }
                        Button(onClick = { authMode = "register"; message = "" }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (authMode == "register") Color(0xFF902FD7) else Color(0xFF30203F)),
                            shape = RoundedCornerShape(14.dp)) { Text("إنشاء حساب") }
                    }
                    if (authMode == "register") {
                        Text("اختر منصات اللعب (يمكن اختيار أكثر من منصة)", modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf("PlayStation", "Xbox", "PC").forEach { choice ->
                            OutlinedButton(onClick = { selectedPlatforms = if (choice in selectedPlatforms) selectedPlatforms - choice else selectedPlatforms + choice; platform = selectedPlatforms.firstOrNull().orEmpty() }, modifier = Modifier.fillMaxWidth().height(51.dp),
                                border = BorderStroke(if (choice in selectedPlatforms) 2.dp else 1.dp,
                                    if (choice in selectedPlatforms) Color(0xFFC175FF) else Color(0xFF58426D)),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (platform == choice) Color(0xFF302046) else Color(0xFF171022))) {
                                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Color(0xFFC68DFF))
                                Spacer(Modifier.width(12.dp))
                                Text(choice, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedTextField(value = username, onValueChange = { username = it.take(20) },
                            label = { Text("اسم اللاعب (حروف وأرقام)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(value = email, onValueChange = { email = it.trim() },
                        label = { Text("البريد الإلكتروني") }, leadingIcon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it },
                        label = { Text("كلمة المرور") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    GamerzAccentButton(
                        text = if (authMode == "register") "إنشاء حساب Gamerz" else "دخول إلى Gamerz",
                        enabled = !busy
                    ) {
                        scope.launch {
                            try {
                                busy = true
                                require(email.isNotBlank() && password.isNotBlank()) { "أدخل البريد وكلمة المرور" }
                                if (authMode == "register") {
                                    require(selectedPlatforms.isNotEmpty()) { "اختر منصة لعب واحدة على الأقل" }
                                    require(username.trim().matches(Regex("^[A-Za-z0-9_]{2,20}$"))) {
                                        "اسم اللاعب يجب أن يكون من 2 إلى 20 حرفًا أو رقمًا أو _"
                                    }
                                    val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
                                        ?: error("تعذّر إنشاء الحساب")
                                    db.collection("users").document(user.uid).set(mapOf(
                                        "username" to username.trim(), "platform" to selectedPlatforms.first(),
                                        "platforms" to selectedPlatforms.sorted(), "createdAt" to System.currentTimeMillis()
                                    )).await()
                                } else {
                                    auth.signInWithEmailAndPassword(email.trim(), password).await()
                                }
                                profileLoaded = false
                                loggedIn = true
                                message = ""
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "تعذر تسجيل الدخول"
                            } finally { busy = false }
                        }
                    }
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color(0xFFC47CFF))
                    if (message.isNotBlank()) Text(message, color = Color(0xFFFFB0B0), style = MaterialTheme.typography.bodyMedium)
                    Text("GAMERZ • العب، شارك، اكتشف", color = Color(0xFFAE94C5), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                LaunchedEffect(loggedIn) {
                    if (loggedIn && !profileLoaded) {
                        try {
                            val user = db.collection("users").document(currentUid).get().await()
                            username = user.getString("username").orEmpty()
                            selectedPlatforms = (user.get("platforms") as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty().ifEmpty { user.getString("platform")?.let { setOf(it) }.orEmpty() }
                            platform = selectedPlatforms.firstOrNull().orEmpty()
                            avatarUrl = user.getString("avatarUrl").orEmpty()
                            profileLoaded = true
                        } catch (_: Exception) { }
                        refreshAll()
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF2A0D46), Color(0xFF110B20), Color(0xFF241039)))).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page = "activity" }) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = "النشاط", tint = Color(0xFFE9D6FF))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Color(0xFFD9A3FF), modifier = Modifier.size(29.dp))
                            Text("Gamerz", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color(0xFFF0DDFF))
                        }
                        IconButton(onClick = { page = "search" }) {
                            Icon(Icons.Default.Search, contentDescription = "بحث", tint = Color(0xFFE9D6FF))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        "create" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GamerzPageHeading("شارك إنجازك", "أضف عنوانًا مميزًا وصورة وقيّم لعبتك مع المجتمع")
                            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E172C))) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (imageUri != null) {
                                        Image(painter = rememberAsyncImagePainter(imageUri), contentDescription = "معاينة صورة اللعبة",
                                            modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                                    } else {
                                        Column(Modifier.fillMaxWidth().height(110.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(36.dp), tint = Color(0xFFC18BFF))
                                            Text("صورة اللعبة مطلوبة", color = Color(0xFFCCB4E7))
                                        }
                                    }
                                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.PhotoLibrary, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("اختيار صورة")
                                    }
                                }
                            }
                            OutlinedTextField(postTitle, { postTitle = it.take(100) },
                                label = { Text("عنوان المنشور *") },
                                placeholder = { Text("مثال: أخيرًا حققت البلاتينيوم!") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(16.dp))
                            Text("العنوان إلزامي ويظهر فوق اسم اللعبة", color = Color(0xFFBDA6D0),
                                style = MaterialTheme.typography.labelSmall)
                            OutlinedTextField(game, { game = it.take(80) }, label = { Text("اسم اللعبة *") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(16.dp))
                            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E172C))) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("تقييمك للعبة *", fontWeight = FontWeight.Bold)
                                    GamerzRatingStars(rating = rating, onRatingChange = { rating = it })
                                    if (rating == 0) Text("اختر من 1 إلى 5 نجوم قبل النشر", color = Color(0xFFB9ADC9), style = MaterialTheme.typography.bodySmall)
                                    OutlinedTextField(completion, { completion = it.filter(Char::isDigit).take(3) }, label = { Text("نسبة الإنجاز من 0 إلى 100% *") }, modifier = Modifier.fillMaxWidth())
                                    GamerzCompletionBar(completion.toIntOrNull() ?: 0)
                                }
                            }
                            OutlinedTextField(review, { review = it.take(500) }, label = { Text("رأيك باللعبة (اختياري)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                            Button(enabled = !busy, onClick = {
                                scope.launch {
                                    try {
                                        busy = true
                                        val percent = completion.toIntOrNull() ?: error("أدخل نسبة صحيحة")
                                        require(postTitle.trim().length in 2..100) { "عنوان المنشور مطلوب (من حرفين إلى 100 حرف)" }
                                        require(game.isNotBlank() && percent in 0..100 && imageUri != null) { "اسم اللعبة والصورة ونسبة الإكمال مطلوبة" }
                                        require(rating in 1..5) { "يجب اختيار تقييم من 1 إلى 5 نجوم قبل النشر" }
                                        require(platform.isNotBlank()) { "اختر منصات اللعب واحفظها من صفحة ملفي قبل النشر" }
                                        val bytes = readCompressedImage(context, imageUri!!)
                                        val (url, uploadPath) = uploadImageToUploadcare(bytes)
                                        db.collection("posts").add(mapOf(
                                            "uid" to currentUid, "platform" to platform,
                                            "gameName" to game.trim(), "title" to postTitle.trim(), "completion" to percent, "rating" to rating,
                                            "imageUrl" to url, "storagePath" to uploadPath,
                                            "pros" to review.trim().take(500), "cons" to "",
                                            "platinum" to false, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                            "likes" to 0, "commentCount" to 0
                                        )).await()
                                        game = ""; postTitle = ""; completion = "0"; rating = 0; review = ""; imageUri = null
                                        message = "تم نشر المنشور"; page = "feed"; refreshFeed()
                                    } catch (e: Exception) { message = e.localizedMessage ?: "فشل النشر" }
                                    finally { busy = false }
                                }
                            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9131D6))) { Text(if (busy) "جارٍ النشر..." else "نشر المنشور", fontWeight = FontWeight.Bold) }
                        }
                        "profile" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF231B32))) {
                                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (avatarUrl.isNotBlank()) {
                                        AsyncImage(model = displayImageUrl(avatarUrl), contentDescription = "صورتك الشخصية", contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(78.dp).border(2.dp, Color(0xFFC77CFF), CircleShape).padding(3.dp).clip(CircleShape))
                                    } else {
                                        Icon(Icons.Default.AccountCircle, contentDescription = "صورة افتراضية", tint = Color(0xFFC79AFF), modifier = Modifier.size(78.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(username.ifBlank { "لاعب Gamerz" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        GamerzPlatformBadge(platform)
                                        Text(auth.currentUser?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9ADC9))
                                    }
                                }
                            }
                            Card(shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF22192F))) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(posts.count { it["uid"] == currentUid }.toString(),
                                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                        Text("منشورات", color = Color(0xFFB9ADC9),
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                    GamerzFollowStats(profileUid = currentUid, db = db,
                                        modifier = Modifier.weight(2f))
                                }
                            }
                            Text("قصصك النشطة: " + stories.count { it["uid"] == currentUid },
                                style = MaterialTheme.typography.labelMedium, color = Color(0xFFB9ADC9))
                            OutlinedButton(enabled = !busy, onClick = { avatarPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("تغيير الصورة الرمزية")
                            }
                            if (isOwner) OutlinedButton(onClick = { page = "admin"; refreshAll() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Security, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("لوحة إدارة Gamerz")
                            }
                            Text("تعديل الملف الشخصي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(username, { username = it }, label = { Text("اسم اللاعب") }, modifier = Modifier.fillMaxWidth())
                            Text("منصات اللعب (اختر واحدة أو أكثر)")
                            listOf("PlayStation", "Xbox", "PC").forEach { choice ->
                                OutlinedButton(onClick = { selectedPlatforms = if (choice in selectedPlatforms) selectedPlatforms - choice else selectedPlatforms + choice; platform = selectedPlatforms.firstOrNull().orEmpty() }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(if (choice in selectedPlatforms) 2.dp else 1.dp, Color(0xFFC175FF))) {
                                    Text((if (choice in selectedPlatforms) "✓ " else "") + choice)
                                }
                            }
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        require(selectedPlatforms.isNotEmpty()) { "اختر منصة واحدة على الأقل" }
                                        require(username.trim().matches(Regex("^[A-Za-z0-9_]{2,20}$"))) { "اسم اللاعب غير صالح" }
                                        val profileRef = db.collection("users").document(currentUid)
                                        val profileData = mapOf("username" to username.trim(), "platform" to selectedPlatforms.first(), "platforms" to selectedPlatforms.sorted())
                                        if (profileRef.get().await().exists()) profileRef.update(profileData).await()
                                        else profileRef.set(profileData + ("createdAt" to System.currentTimeMillis())).await()
                                        platform = selectedPlatforms.first()
                                        message = "تم حفظ اسم اللاعب"
                                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر الحفظ" }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الملف") }
                            Text("معرض إنجازاتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val myPosts = posts.filter { it["uid"] == currentUid }
                            if (myPosts.isEmpty()) Text("لم تنشر ألعابًا بعد", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9ADC9))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                items(myPosts) { post ->
                                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF23182F))) {
                                        Column(Modifier.width(122.dp).padding(6.dp)) {
                                            AsyncImage(model = displayImageUrl(post["imageUrl"]), contentDescription = "غلاف اللعبة", contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(10.dp)))
                                            Text(post["gameName"]?.toString().orEmpty(), style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mustafaadrl93-ai/Gamerz/blob/main/docs/privacy-policy.md")))
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PrivacyTip, null); Spacer(Modifier.width(8.dp)); Text("مسودة سياسة الخصوصية") }
                            OutlinedButton(onClick = { deletePassword = ""; showDeleteAccount = true }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA5AD))) {
                                Icon(Icons.Default.DeleteForever, null)
                                Spacer(Modifier.width(8.dp))
                                Text("حذف حسابي")
                            }
                            TextButton(onClick = { auth.signOut(); loggedIn = false; profileLoaded = false; platform = ""; selectedPlatforms = emptySet(); avatarUrl = ""; page = "feed" }) { Text("تسجيل الخروج") }
                        }
                        "activity" -> GamerzActivityScreen(posts = posts, uid = currentUid, db = db)
                        "search" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            GamerzPageHeading("استكشاف الألعاب", "ابحث في آخر المنشورات وأبرز إنجازات اللاعبين")
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("ابحث بعنوان أو لعبة أو منصة") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            val results = posts.filter { post ->
                                searchQuery.isBlank() ||
                                    post["gameName"]?.toString().orEmpty().contains(searchQuery.trim(), ignoreCase = true) ||
                                    post["title"]?.toString().orEmpty().contains(searchQuery.trim(), ignoreCase = true) ||
                                    post["platform"]?.toString().orEmpty().contains(searchQuery.trim(), ignoreCase = true)
                            }
                            if (searchQuery.trim().length >= 2) {
                                Text("اللاعبون", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = Color(0xFFE7CEFF))
                                if (playerResults.isEmpty()) {
                                    Text("لا يوجد لاعبون مطابقون في نتائج البحث",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB8A7CA))
                                }
                                playerResults.filter {
                                    it["_uid"]?.toString() != currentUid
                                }.forEach { player ->
                                    val userId = player["_uid"]?.toString().orEmpty()
                                    Surface(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { viewedProfileUid = userId },
                                        color = Color(0xFF21182F),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(11.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                tint = Color(0xFFBD8DEB),
                                                modifier = Modifier.size(36.dp))
                                            Spacer(Modifier.width(9.dp))
                                            Text(player["username"]?.toString().orEmpty(),
                                                modifier = Modifier.weight(1f),
                                                color = Color.White, fontWeight = FontWeight.Bold)
                                            GamerzFollowButton(currentUid, userId, db,
                                                onChanged = { scope.launch { refreshFollowing() } })
                                        }
                                    }
                                }
                            }
                            Text(if (searchQuery.isBlank()) "أحدث الألعاب في المجتمع" else "نتائج البحث: " + results.size,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (results.isEmpty()) {
                                Text("لا توجد نتائج في المنشورات المحملة. جرب اسم لعبة آخر.",
                                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9ADC9))
                            }
                            results.forEach { post ->
                                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF22192F))) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(model = displayImageUrl(post["imageUrl"]), contentDescription = "صورة لعبة",
                                            contentScale = ContentScale.Crop, modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(post["title"]?.toString().orEmpty()
                                                .ifBlank { post["gameName"]?.toString().orEmpty() },
                                                fontWeight = FontWeight.Bold, maxLines = 2)
                                            Text(post["gameName"]?.toString().orEmpty(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFBAACCD))
                                            GamerzPlatformBadge(post["platform"]?.toString().orEmpty())
                                            GamerzRatingStars(rating = (post["rating"] as? Number)?.toInt() ?: 0)
                                        }
                                    }
                                }
                            }
                        }
                        "admin" -> if (isOwner) LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                            item { Text("لوحة المالك", style = MaterialTheme.typography.headlineSmall) }
                            item { Text("البلاغات المفتوحة والمراجعة", style = MaterialTheme.typography.titleMedium) }
                            items(reports) { report ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("المنشور: ${report["postId"] ?: ""}")
                                        Text("السبب: ${report["reason"] ?: ""}")
                                        Text("الحالة: ${report["status"] ?: ""}")
                                        Row {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        db.collection("reports").document(report["_id"].toString())
                                                            .update(mapOf("status" to "reviewed", "reviewedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "reviewedBy" to currentUid)).await()
                                                        refreshReports(); message = "تمت مراجعة البلاغ"
                                                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحديث البلاغ" }
                                                }
                                            }) { Text("تمت المراجعة") }
                                            TextButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        db.collection("reports").document(report["_id"].toString())
                                                            .update(mapOf("status" to "dismissed", "reviewedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "reviewedBy" to currentUid)).await()
                                                        refreshReports(); message = "تم إغلاق البلاغ"
                                                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحديث البلاغ" }
                                                }
                                            }) { Text("رفض البلاغ") }
                                        }
                                    }
                                }
                            }
                            item { Text("القصص تختفي بعد 24 ساعة، لكن حذف سجلاتها المنتهية تلقائيًا غير متاح على الخطة المجانية.", style = MaterialTheme.typography.bodySmall) }
                        } else Text("غير مصرح")
                        else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { pullToRefresh() }, modifier = Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("لحظات اللاعبين", style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold, color = Color(0xFFF0DDFF))
                                        Text("قصص يومية • صور تختفي بعد 24 ساعة",
                                            color = Color(0xFFBCA7CE),
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF322247)) {
                                        Text("LIVE", color = Color(0xFFDFA7FF),
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                val featuredStories = stories.groupBy {
                                    it["uid"]?.toString().orEmpty()
                                }.values.mapNotNull { accountStories ->
                                    accountStories.maxByOrNull {
                                        (it["createdAt"] as? Timestamp)?.seconds ?: 0L
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    modifier = Modifier.fillMaxWidth()) {
                                    item {
                                        Column(Modifier.width(87.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF352149),
                                                modifier = Modifier.size(78.dp)
                                                    .border(2.dp, Color(0xFFB77AFF), CircleShape)
                                                    .clickable { if (!busy) storyPicker.launch("image/*") }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Add, contentDescription = "أضف صور قصتك",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(30.dp))
                                                }
                                            }
                                            Text("قصتك +", color = Color(0xFFE9CFFF),
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    items(featuredStories) { story ->
                                        val number = stories.count { it["uid"] == story["uid"] }
                                        GamerzStoryBubble(
                                            story = story, storyCount = number,
                                            db = db, currentUid = currentUid,
                                            onClick = { storyTarget = story }
                                        )
                                    }
                                }
                                if (storyUris.isNotEmpty()) {
                                    Card(
                                        shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261B35)),
                                        border = BorderStroke(1.dp, Color(0xFF553476))
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("قصتك الجديدة", fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleMedium)
                                            Text("اخترت " + storyUris.size + " صور • حد أقصى 10 صور في كل دفعة",
                                                color = Color(0xFFC9B2DC),
                                                style = MaterialTheme.typography.bodySmall)
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(storyUris) { uri ->
                                                    Image(painter = rememberAsyncImagePainter(uri),
                                                        contentDescription = "معاينة صورة القصة",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.size(90.dp)
                                                            .clip(RoundedCornerShape(12.dp)))
                                                }
                                            }
                                            OutlinedTextField(
                                                value = storyCaption,
                                                onValueChange = { storyCaption = it.take(180) },
                                                label = { Text("وصف بسيط للقصص (اختياري)") },
                                                maxLines = 2, modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            if (busy) Text("جارٍ رفع الصورة " + (storyUploadCount + 1),
                                                color = Color(0xFFE7C7FF))
                                            Button(
                                                enabled = !busy && storyUris.isNotEmpty(),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp),
                                                onClick = {
                                                    scope.launch {
                                                        val queue = storyUris
                                                        var published = 0
                                                        try {
                                                            busy = true
                                                            storyUploadCount = 0
                                                            for (uri in queue) {
                                                                val bytes = readCompressedImage(context, uri)
                                                                val (url, _) = uploadImageToUploadcare(bytes)
                                                                val now = Timestamp.now()
                                                                db.collection("stories").add(mapOf(
                                                                    "uid" to currentUid,
                                                                    "imageUrl" to url,
                                                                    "caption" to storyCaption.trim(),
                                                                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                                                    "expiresAt" to Timestamp(now.seconds + 24 * 60 * 60, now.nanoseconds)
                                                                )).await()
                                                                published++
                                                                storyUploadCount = published
                                                                storyUris = queue.drop(published)
                                                            }
                                                            storyCaption = ""
                                                            message = "تم نشر " + published + " قصص لمدة 24 ساعة"
                                                            refreshStories()
                                                        } catch (e: Exception) {
                                                            message = "نُشرت " + published +
                                                                " صور. تعذر إكمال الباقي: " +
                                                                (e.localizedMessage ?: "تحقق من اتصالك")
                                                            try { refreshStories() } catch (_: Exception) { }
                                                        } finally {
                                                            busy = false
                                                            storyUploadCount = 0
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.AutoStories, contentDescription = null)
                                                Spacer(Modifier.width(7.dp))
                                                Text("نشر " + storyUris.size + " صور في قصتي")
                                            }
                                            TextButton(enabled = !busy, onClick = {
                                                storyUris = emptyList()
                                                storyCaption = ""
                                            }) { Text("إلغاء الاختيار") }
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = feedMode == "forYou",
                                        onClick = { feedMode = "forYou" },
                                        label = { Text("اكتشف") },
                                        leadingIcon = { Icon(Icons.Default.Explore, null) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = feedMode == "following",
                                        onClick = { feedMode = "following"; scope.launch { refreshFollowing() } },
                                        label = { Text("أتابعهم") },
                                        leadingIcon = { Icon(Icons.Default.Groups, null) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (feedMode == "following" && followedUsers.isEmpty()) {
                                    Text("تابع لاعبين لتشاهد منشوراتهم هنا",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFCCB3D9))
                                }
                                if (message.isNotBlank()) Text(message,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            if (visiblePosts.isEmpty() && !busy && !refreshing) item {
                                Card(Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF21182E))) {
                                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Color(0xFFC59AFF), modifier = Modifier.size(42.dp))
                                        Text("ابدأ مجتمع اللاعبين", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("لا توجد منشورات بعد. شارك أول لعبة أنجزتها!", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9ADC9))
                                        Button(onClick = { page = "create" }) { Text("أنشئ منشورك") }
                                    }
                                }
                            }
                            items(visiblePosts) { post ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E172A)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                    border = BorderStroke(1.dp, Color(0xFF3B2B4D))) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            GamerzPostAuthor(uid = post["uid"]?.toString().orEmpty(), fallbackPlatform = post["platform"]?.toString().orEmpty(),
                                                db = db, modifier = Modifier.weight(1f),
                                                onOpenProfile = { if (it != currentUid) viewedProfileUid = it else page = "profile" })
                                            GamerzFollowButton(currentUid = currentUid,
                                                targetUid = post["uid"]?.toString().orEmpty(),
                                                db = db, modifier = Modifier.height(36.dp),
                                                onChanged = { scope.launch { refreshFollowing() } })
                                            Spacer(Modifier.width(6.dp))
                                            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF39254E)) { Text("${post["completion"] ?: 0}%", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color(0xFFE3C7FF), fontWeight = FontWeight.Bold) }
                                        }
                                        AsyncImage(model = displayImageUrl(post["imageUrl"]), contentDescription = "صورة اللعبة", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(0xFF0E0B18)))
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                            Text(post["title"]?.toString().orEmpty()
                                                .ifBlank { post["gameName"]?.toString().orEmpty() },
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold, color = Color(0xFFF2E3FF))
                                            Text("🎮 " + post["gameName"]?.toString().orEmpty(),
                                                color = Color(0xFFC3A9D8),
                                                style = MaterialTheme.typography.bodyMedium)
                                            GamerzRatingStars(rating = (post["rating"] as? Number)?.toInt() ?: 0)
                                            GamerzCompletionBar((post["completion"] as? Number)?.toInt() ?: 0)
                                            if (!post["pros"]?.toString().isNullOrBlank()) Text(post["pros"]?.toString().orEmpty(), style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            GamerzLikeButton(postId = post["_id"].toString(), uid = currentUid, db = db)
                                            TextButton(onClick = { commentTarget = post }) {
                                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text("التعليقات")
                                            }
                                            IconButton(onClick = {
                                                val share = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT,
                                                        (post["title"]?.toString().orEmpty()
                                                            .ifBlank { post["gameName"]?.toString().orEmpty() }) +
                                                        " 🎮 Gamerz\n" +
                                                        post["gameName"]?.toString().orEmpty() + "\n" +
                                                        displayImageUrl(post["imageUrl"]))
                                                }
                                                context.startActivity(Intent.createChooser(share, "مشاركة المنشور"))
                                            }) {
                                                Icon(Icons.Default.Share, contentDescription = "مشاركة المنشور",
                                                    tint = Color(0xFFD9AFE9))
                                            }
                                            Spacer(Modifier.weight(1f))
                                            TextButton(onClick = { reportTarget = post; reportReason = "" }) { Text("إبلاغ") }
                                            if (post["uid"] == currentUid || isOwner) TextButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        val postRef = db.collection("posts").document(post["_id"].toString())
                                                        for (collectionName in listOf("likes", "comments")) {
                                                            for (item in postRef.collection(collectionName).get().await().documents) item.reference.delete().await()
                                                        }
                                                        postRef.delete().await()
                                                        refreshFeed(); message = "تم حذف المنشور"
                                                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر حذف المنشور" }
                                                }
                                            }) { Text("حذف") }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                    }
                    if (message.isNotBlank() && page != "feed") Text(message, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.secondary)
                    NavigationBar(containerColor = Color(0xFF130F1D), tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                        NavigationBarItem(selected = page == "feed", onClick = { page = "feed" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية") })
                        NavigationBarItem(selected = page == "search", onClick = { page = "search" }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("استكشاف") })
                        NavigationBarItem(selected = page == "create", onClick = { page = "create" }, icon = { Icon(Icons.Default.AddCircle, null, tint = Color(0xFFC77CFF)) }, label = { Text("نشر") })
                        NavigationBarItem(selected = page == "activity", onClick = { page = "activity" }, icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("النشاط") })
                        NavigationBarItem(selected = page == "profile", onClick = { page = "profile" }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("ملفي") })
                    }

                }
            }
        }
    }
    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDeleteAccount = false },
            title = { Text("حذف حساب Gamerz نهائيًا") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("سيتم حذف حسابك وملفك ومنشوراتك وقصصك من Firebase. لا يمكن التراجع عن هذه العملية.")
                    Text("ملفات الصور لدى Uploadcare تحتاج إزالة منفصلة بواسطة إدارة التطبيق؛ سيتم إرسال طلب إزالتها.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFBF95))
                    OutlinedTextField(
                        value = deletePassword, onValueChange = { deletePassword = it },
                        label = { Text("أدخل كلمة المرور للتأكيد") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && deletePassword.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            busy = true
                            val user = auth.currentUser ?: error("الحساب غير متوفر")
                            val signedInEmail = user.email ?: error("بريد الحساب غير متوفر")
                            val uid = user.uid
                            user.reauthenticate(EmailAuthProvider.getCredential(signedInEmail, deletePassword)).await()
                            val ownedPosts = db.collection("posts").whereEqualTo("uid", uid).get().await().documents
                            val ownedStories = db.collection("stories").whereEqualTo("uid", uid).get().await().documents
                            val urls = ownedPosts.mapNotNull { it.getString("imageUrl") } +
                                ownedStories.mapNotNull { it.getString("imageUrl") } + listOf(avatarUrl)
                            val imagePaths = urls.mapNotNull { url ->
                                if (!displayImageUrl(url).startsWith("https://26vsxjozef.ucarecd.net/")) null
                                else {
                                    val id = displayImageUrl(url).removePrefix("https://26vsxjozef.ucarecd.net/").substringBefore("/")
                                    if (id.matches(Regex("[a-f0-9-]{20,40}"))) "uploadcare/" + id else null
                                }
                            }.distinct().take(500)
                            db.collection("deletionRequests").add(mapOf(
                                "uid" to uid,
                                "imagePaths" to imagePaths,
                                "status" to "pending",
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )).await()
                            for (doc in ownedPosts) {
                                for (collectionName in listOf("likes", "comments")) {
                                    for (item in doc.reference.collection(collectionName).get().await().documents) item.reference.delete().await()
                                }
                                doc.reference.delete().await()
                            }
                            for (doc in ownedStories) doc.reference.delete().await()
                            // Clean both sides of follow relationships before deleting the profile.
                            for (field in listOf("followerUid", "targetUid")) {
                                val links = db.collection("follows").whereEqualTo(field, uid).get().await()
                                for (link in links.documents) link.reference.delete().await()
                            }
                            db.collection("users").document(uid).delete().await()
                            user.delete().await()
                            auth.signOut()
                            loggedIn = false
                            profileLoaded = false
                            avatarUrl = ""
                            platform = ""
                            posts = emptyList()
                            stories = emptyList()
                            page = "feed"
                            showDeleteAccount = false
                            message = "حُذف الحساب من Firebase؛ طلب إزالة الصور بانتظار المعالجة."
                        } catch (e: Exception) {
                            message = "تعذر إكمال حذف الحساب: " + (e.localizedMessage ?: "راجع اتصالك وقواعد Firebase")
                        } finally {
                            busy = false
                            deletePassword = ""
                        }
                    }
                }) { Text("تأكيد حذف الحساب", color = Color(0xFFE58D93)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showDeleteAccount = false; deletePassword = "" }) { Text("إلغاء") } }
        )
    }

    commentTarget?.let { target ->
        GamerzCommentsDialog(
            postId = target["_id"].toString(), uid = currentUid, isOwner = isOwner,
            db = db, onDismiss = { commentTarget = null },
            postTitle = target["title"]?.toString().orEmpty()
                .ifBlank { target["gameName"]?.toString().orEmpty() }
        )
    }
    viewedProfileUid?.let { targetUid ->
        GamerzPublicProfileSheet(
            targetUid = targetUid, currentUid = currentUid,
            db = db, posts = posts, onDismiss = { viewedProfileUid = null }
        )
    }
    storyTarget?.let { selected ->
        GamerzStoryViewer(
            stories = stories,
            startStoryId = selected["_id"]?.toString().orEmpty(),
            currentUid = currentUid,
            isOwner = isOwner,
            db = db,
            onDismiss = { storyTarget = null },
            onDeleteStory = { storyId ->
                scope.launch {
                    try {
                        db.collection("stories").document(storyId).delete().await()
                        stories = stories.filterNot { it["_id"]?.toString() == storyId }
                        storyTarget = null
                        message = "تم حذف القصة من Gamerz"
                    } catch (e: Exception) {
                        message = e.localizedMessage ?: "تعذر حذف القصة"
                    }
                }
            }
        )
    }
    reportTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("الإبلاغ عن منشور") },
            text = {
                Column {
                    Text("اكتب سبب البلاغ")
                    OutlinedTextField(reportReason, { reportReason = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            require(target["uid"] != currentUid) { "لا يمكنك الإبلاغ عن منشورك" }
                            db.collection("reports").add(mapOf(
                                "postId" to target["_id"].toString(), "postOwnerUid" to target["uid"].toString(),
                                "reporterUid" to currentUid, "reason" to reportReason.trim().take(300),
                                "status" to "open", "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )).await()
                            message = "تم إرسال البلاغ"; reportTarget = null
                        } catch (e: Exception) { message = e.localizedMessage ?: "تعذر إرسال البلاغ" }
                    }
                }, enabled = reportReason.isNotBlank()) { Text("إرسال") }
            },
            dismissButton = { TextButton(onClick = { reportTarget = null }) { Text("إلغاء") } }
        )
    }
}
