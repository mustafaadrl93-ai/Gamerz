package com.gamerz.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
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
        Pair("https://ucarecdn.com/$fileId/", "uploadcare/$fileId")
    } finally {
        connection.disconnect()
    }
}

private fun readCompressedImage(context: android.content.Context, uri: Uri): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open image")
    val bitmap = input.use { android.graphics.BitmapFactory.decodeStream(it) } ?: error("Invalid image")
    return try {
        ByteArrayOutputStream().use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, output)
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GamerzApp() }
    }
}

@Composable
fun GamerzApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val functions = remember { FirebaseFunctions.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUid = auth.currentUser?.uid.orEmpty()
    val isOwner = currentUid == OWNER_UID

    var platform by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var game by remember { mutableStateOf("") }
    var completion by remember { mutableStateOf("0") }
    var review by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var storyUri by remember { mutableStateOf<Uri?>(null) }
    var loggedIn by remember { mutableStateOf(auth.currentUser != null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("feed") }
    var posts by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var stories by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var reports by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var reportTarget by remember { mutableStateOf<Map<String, Any>?>(null) }
    var reportReason by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }
    val storyPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { storyUri = it }

    suspend fun refreshFeed() {
        posts = db.collection("posts")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50).get().await().documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
            }
    }
    suspend fun refreshStories() {
        stories = db.collection("stories").whereGreaterThan("expiresAt", Timestamp.now())
            .orderBy("expiresAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .limit(50).get().await().documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
            }
    }
    suspend fun refreshReports() {
        if (!isOwner) return
        reports = db.collection("reports").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100).get().await().documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
            }
    }
    fun refreshAll() {
        scope.launch {
            try {
                busy = true
                refreshFeed()
                refreshStories()
                if (isOwner) refreshReports()
            } catch (e: Exception) {
                message = e.localizedMessage ?: "تعذر تحديث المحتوى"
            } finally { busy = false }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF8B72FF),
        secondary = androidx.compose.ui.graphics.Color(0xFF52E0C4),
        background = androidx.compose.ui.graphics.Color(0xFF101321),
        surface = androidx.compose.ui.graphics.Color(0xFF1B2235)
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!loggedIn) {
                Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
                    Text("GAMERZ", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.secondary)
                    Text("مجتمع اللاعبين", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(20.dp))
                    if (platform.isBlank()) {
                        Text("اختر منصة اللعب")
                        listOf("PlayStation", "Xbox", "PC").forEach { value ->
                            OutlinedButton(onClick = { platform = value }, modifier = Modifier.fillMaxWidth()) { Text(value) }
                        }
                    } else {
                        OutlinedTextField(username, { username = it }, label = { Text("اسم اللاعب عند إنشاء حساب") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) }, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy, onClick = {
                            scope.launch {
                                try {
                                    busy = true
                                    require(username.trim().matches(Regex("^[A-Za-z0-9_]{2,20}$"))) { "اسم اللاعب يجب أن يكون 2-20 حرفًا أو رقمًا أو _" }
                                    val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
                                    db.collection("users").document(user.uid).set(mapOf(
                                        "username" to username.trim(), "platform" to platform,
                                        "createdAt" to System.currentTimeMillis()
                                    )).await()
                                    loggedIn = true
                                    message = "تم إنشاء الحساب"
                                } catch (e: Exception) { message = e.localizedMessage ?: "تعذر إنشاء الحساب" }
                                finally { busy = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("إنشاء حساب") }
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                try {
                                    busy = true
                                    auth.signInWithEmailAndPassword(email.trim(), password).await()
                                    loggedIn = true
                                    message = "تم تسجيل الدخول"
                                } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تسجيل الدخول" }
                                finally { busy = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الدخول") }
                        TextButton(onClick = { platform = "" }) { Text("تغيير المنصة") }
                    }
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                LaunchedEffect(loggedIn) {
                    if (loggedIn && !profileLoaded) {
                        try {
                            val user = db.collection("users").document(currentUid).get().await()
                            username = user.getString("username").orEmpty()
                            platform = user.getString("platform").orEmpty()
                            profileLoaded = true
                        } catch (_: Exception) { }
                        refreshAll()
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("GAMERZ", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                            Text("قيمرز • مجتمع اللاعبين", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { refreshAll() }) { Text("تحديث") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { page = "feed" }) { Text("الرئيسية") }
                        TextButton(onClick = { page = "create" }) { Text("نشر") }
                        TextButton(onClick = { page = "profile" }) { Text("ملفي") }
                        if (isOwner) TextButton(onClick = { page = "admin"; refreshAll() }) { Text("الإدارة") }
                    }
                    when (page) {
                        "create" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("منشور جديد", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(game, { game = it }, label = { Text("اسم اللعبة") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(completion, { completion = it.filter(Char::isDigit).take(3) }, label = { Text("نسبة الإنجاز 0–100") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(review, { review = it }, label = { Text("رأيك باللعبة (اختياري)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("اختر صورة المنشور") }
                            imageUri?.let { Image(painter = rememberAsyncImagePainter(it), contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp)) }
                            Button(enabled = !busy, onClick = {
                                scope.launch {
                                    try {
                                        busy = true
                                        val percent = completion.toIntOrNull() ?: error("أدخل نسبة صحيحة")
                                        require(game.isNotBlank() && percent in 0..100 && imageUri != null) { "اسم اللعبة والصورة ونسبة 0–100 مطلوبة" }
                                        val bytes = readCompressedImage(context, imageUri!!)
                                        val (url, uploadPath) = uploadImageToUploadcare(bytes)
                                        functions.getHttpsCallable("createPost").call(mapOf(
                                            "gameName" to game.trim(), "completion" to percent,
                                            "imageUrl" to url, "storagePath" to uploadPath,
                                            "pros" to review, "cons" to "", "platinum" to false
                                        )).await()
                                        game = ""; completion = "0"; review = ""; imageUri = null
                                        message = "تم نشر المنشور"; page = "feed"; refreshFeed()
                                    } catch (e: Exception) { message = e.localizedMessage ?: "فشل النشر" }
                                    finally { busy = false }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "جارٍ النشر..." else "نشر") }
                        }
                        "profile" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("ملفي الشخصي", style = MaterialTheme.typography.titleLarge)
                            Text(auth.currentUser?.email.orEmpty())
                            OutlinedTextField(username, { username = it }, label = { Text("اسم اللاعب") }, modifier = Modifier.fillMaxWidth())
                            Text("منصة اللعب: $platform")
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        db.collection("users").document(currentUid)
                                            .update("username", username.trim()).await()
                                        message = "تم حفظ اسم اللاعب"
                                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر الحفظ" }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الملف") }
                            TextButton(onClick = { auth.signOut(); loggedIn = false; profileLoaded = false; platform = ""; page = "feed" }) { Text("تسجيل الخروج") }
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
                                                        functions.getHttpsCallable("adminResolveReport").call(mapOf("reportId" to report["_id"], "status" to "reviewed")).await()
                                                        refreshReports(); message = "تمت مراجعة البلاغ"
                                                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحديث البلاغ" }
                                                }
                                            }) { Text("تمت المراجعة") }
                                            TextButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        functions.getHttpsCallable("adminResolveReport").call(mapOf("reportId" to report["_id"], "status" to "dismissed")).await()
                                                        refreshReports(); message = "تم إغلاق البلاغ"
                                                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحديث البلاغ" }
                                                }
                                            }) { Text("رفض البلاغ") }
                                        }
                                    }
                                }
                            }
                            item { Text("حذف القصص المنتهية يتم تلقائيًا عبر وظيفة الخادم بعد نشرها.", style = MaterialTheme.typography.bodySmall) }
                        } else Text("غير مصرح")
                        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                            item {
                                Text("القصص • 24 ساعة", style = MaterialTheme.typography.titleMedium)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    item {
                                        OutlinedButton(onClick = { storyPicker.launch("image/*") }) { Text("+ قصتك") }
                                    }
                                    items(stories) { story ->
                                        Column(Modifier.width(100.dp)) {
                                            AsyncImage(model = story["imageUrl"], contentDescription = "Story", modifier = Modifier.size(92.dp))
                                            Text("قصة لاعب", style = MaterialTheme.typography.labelSmall)
                                            if (isOwner) TextButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        functions.getHttpsCallable("adminDeleteStory").call(mapOf("storyId" to story["_id"])).await()
                                                        refreshStories(); message = "تم حذف القصة"
                                                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر حذف القصة" }
                                                }
                                            }) { Text("حذف") }
                                        }
                                    }
                                }
                                Button(enabled = !busy && storyUri == null, onClick = { storyPicker.launch("image/*") }) { Text("اختيار صورة قصة") }
                                storyUri?.let { Image(painter = rememberAsyncImagePainter(it), contentDescription = null, modifier = Modifier.fillMaxWidth().height(100.dp)) }
                                if (storyUri != null) Button(enabled = !busy, onClick = {
                                    scope.launch {
                                        try {
                                            busy = true
                                            val bytes = readCompressedImage(context, storyUri!!)
                                            val (url, _) = uploadImageToUploadcare(bytes)
                                            functions.getHttpsCallable("createStory").call(mapOf("imageUrl" to url, "caption" to "")).await()
                                            storyUri = null; refreshStories(); message = "تم نشر القصة لمدة 24 ساعة"
                                        } catch (e: Exception) { message = e.localizedMessage ?: "فشل نشر القصة" }
                                        finally { busy = false }
                                    }
                                }) { Text("نشر القصة") }
                                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(10.dp))
                            }
                            items(posts) { post ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(post["gameName"]?.toString() ?: "Game", style = MaterialTheme.typography.titleMedium)
                                        AsyncImage(model = post["imageUrl"], contentDescription = "Post image", modifier = Modifier.fillMaxWidth().height(210.dp))
                                        Text("${post["completion"] ?: 0}% إنجاز")
                                        Text(post["pros"]?.toString().orEmpty())
                                        Row {
                                            TextButton(onClick = { reportTarget = post; reportReason = "" }) { Text("إبلاغ") }
                                            if (post["uid"] == currentUid || isOwner) TextButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        functions.getHttpsCallable("deletePost").call(mapOf("postId" to post["_id"])).await()
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
                    if (message.isNotBlank() && page != "feed") Text(message, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
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
                            functions.getHttpsCallable("reportPost").call(mapOf(
                                "postId" to target["_id"], "reason" to reportReason.trim()
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
