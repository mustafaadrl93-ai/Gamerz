package com.gamerz.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Night = Color(0xFF101321)
private val CardBg = Color(0xFF1B2235)
private val Purple = Color(0xFF8B72FF)
private val Aqua = Color(0xFF52E0C4)
private val Grey = Color(0xFF9AA3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GamerzApp() }
    }
}

@Composable
private fun GamerzApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val functions = remember { FirebaseFunctions.getInstance() }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(auth.currentUser != null) }
    var page by remember { mutableStateOf("home") }
    var ar by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("PlayStation") }
    var avatar by remember { mutableStateOf("🎮") }
    var posts by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var query by remember { mutableStateOf("") }
    var game by remember { mutableStateOf("") }
    var percent by remember { mutableStateOf("0") }
    var review by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }

    fun refresh() {
        scope.launch {
            try {
                busy = true
                posts = db.collection("posts").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await().documents.mapNotNull { it.data }
            } catch (e: Exception) { message = e.localizedMessage ?: "Feed error" }
            finally { busy = false }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, secondary = Aqua, background = Night, surface = CardBg)) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            if (!loggedIn) {
                Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center) {
                    Text("G", color = Aqua, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                    Text("GAMERZ", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text(if (ar) "مكانك بين اللاعبين." else "YOUR PLACE IN THE GAME.", color = Grey)
                    Spacer(Modifier.height(26.dp))
                    Text(if (mode == "login") (if (ar) "مرحبًا بعودتك" else "Welcome back") else (if (ar) "أنشئ حسابك" else "Create account"),
                        color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    if (mode == "signup") {
                        Spacer(Modifier.height(12.dp))
                        Field(username, { username = it }, if (ar) "اسم اللاعب" else "Username")
                    }
                    Spacer(Modifier.height(12.dp))
                    Field(email, { email = it }, if (ar) "البريد الإلكتروني" else "Email", KeyboardType.Email)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (ar) "كلمة المرور" else "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            try {
                                busy = true; message = ""
                                if (mode == "signup") {
                                    require(username.trim().length in 3..20) { "Username must be 3-20 characters" }
                                    val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
                                    db.collection("users").document(user.uid).set(mapOf("username" to username.trim(), "platform" to platform, "avatar" to avatar)).await()
                                } else auth.signInWithEmailAndPassword(email.trim(), password).await()
                                loggedIn = true; refresh()
                            } catch (e: Exception) { message = e.localizedMessage ?: "Sign-in failed" }
                            finally { busy = false }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(if (busy) "..." else if (mode == "login") (if (ar) "دخول" else "Sign in") else (if (ar) "إنشاء الحساب" else "Create account"))
                    }
                    OutlinedButton(onClick = { message = if (ar) "تسجيل Google يحتاج إعداد OAuth في Firebase." else "Google sign-in requires Firebase OAuth setup." },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp)) { Text("G  Continue with Google") }
                    TextButton(onClick = { mode = if (mode == "login") "signup" else "login" }) {
                        Text(if (mode == "login") (if (ar) "مستخدم جديد؟ أنشئ حسابًا" else "Create an account") else (if (ar) "لديك حساب؟ دخول" else "Sign in instead"))
                    }
                    TextButton(onClick = { ar = !ar }) { Text(if (ar) "English" else "العربية") }
                    if (message.isNotBlank()) Text(message, color = Aqua)
                }
            } else {
                LaunchedEffect(Unit) { refresh() }
                Scaffold(containerColor = Night,
                    topBar = {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("GAMERZ", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                                Text(if (ar) "مجتمع اللاعبين" else "THE GAMING COMMUNITY", color = Aqua, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { ar = !ar }) { Icon(Icons.Default.Language, "Language", tint = Color.White) }
                            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor = CardBg) {
                            listOf(Triple("home", Icons.Default.Home, if (ar) "الرئيسية" else "Home"),
                                Triple("explore", Icons.Default.Search, if (ar) "استكشاف" else "Explore"),
                                Triple("create", Icons.Default.AddCircle, if (ar) "انشر" else "Post"),
                                Triple("profile", Icons.Default.Person, if (ar) "ملفي" else "Profile")).forEach { (key, icon, label) ->
                                NavigationBarItem(selected = page == key, onClick = { page = key },
                                    icon = { Icon(icon, label) }, label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Aqua, selectedTextColor = Aqua,
                                        indicatorColor = Purple.copy(alpha = .2f), unselectedIconColor = Grey, unselectedTextColor = Grey))
                            }
                        }
                    }) { pad ->
                    Column(Modifier.fillMaxSize().padding(pad)) {
                        when (page) {
                            "home" -> Feed(posts, busy, ar)
                            "explore" -> {
                                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    placeholder = { Text(if (ar) "ابحث عن لعبة..." else "Search games...") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp), singleLine = true)
                                Feed(posts.filter { (it["gameName"]?.toString() ?: "").contains(query, true) }, busy, ar)
                            }
                            "create" -> {
                                Column(Modifier.fillMaxSize().padding(20.dp)) {
                                    Text(if (ar) "شارك تجربتك" else "SHARE YOUR EXPERIENCE", color = Aqua)
                                    Text(if (ar) "منشور جديد" else "New post", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                                    Spacer(Modifier.height(14.dp))
                                    Field(game, { game = it }, if (ar) "اسم اللعبة" else "Game title")
                                    Spacer(Modifier.height(10.dp))
                                    Field(percent, { percent = it.filter(Char::isDigit).take(3) }, if (ar) "نسبة الإكمال 0–100" else "Completion 0–100", KeyboardType.Number)
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedTextField(review, { review = it }, modifier = Modifier.fillMaxWidth().height(110.dp),
                                        label = { Text(if (ar) "مراجعتك" else "Your review") }, shape = RoundedCornerShape(16.dp))
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.AddPhotoAlternate, null); Text(if (ar) "  اختر صورة" else "  Choose image")
                                    }
                                    photo?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp), contentScale = ContentScale.Crop) }
                                    Spacer(Modifier.height(14.dp))
                                    Button(enabled = !busy, onClick = {
                                        scope.launch {
                                            try {
                                                busy = true
                                                val value = percent.toIntOrNull() ?: error("Invalid completion")
                                                require(game.isNotBlank() && value in 0..100 && photo != null) { "Game, image and completion 0-100 are required" }
                                                val uid = auth.currentUser!!.uid
                                                val bitmap = android.graphics.BitmapFactory.decodeStream(contentResolver.openInputStream(photo!!)) ?: error("Invalid image")
                                                val out = java.io.ByteArrayOutputStream()
                                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 84, out)
                                                val ref = storage.reference.child("postImages/$uid/${System.currentTimeMillis()}.jpg")
                                                ref.putBytes(out.toByteArray()).await()
                                                val url = ref.downloadUrl.await().toString()
                                                functions.getHttpsCallable("createPost").call(mapOf("gameName" to game.trim(), "completion" to value,
                                                    "imageUrl" to url, "storagePath" to ref.path, "pros" to review, "cons" to "", "platinum" to false)).await()
                                                game = ""; percent = "0"; review = ""; photo = null
                                                message = if (ar) "تم النشر" else "Published"; page = "home"; refresh()
                                            } catch (e: Exception) { message = e.localizedMessage ?: "Publish failed" }
                                            finally { busy = false }
                                        }
                                    }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                                        Text(if (busy) "..." else if (ar) "نشر المنشور" else "Publish")
                                    }
                                    Text(if (ar) "رفع الصور يتطلب تفعيل Firebase Storage." else "Image uploads require Firebase Storage.", color = Grey, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            else -> {
                                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(76.dp).background(Purple.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
                                                Text(avatar, style = MaterialTheme.typography.displaySmall)
                                            }
                                            Column(Modifier.padding(start = 14.dp)) {
                                                Text(username.ifBlank { if (ar) "لاعب قيمرز" else "Gamerz player" }, color = Color.White, style = MaterialTheme.typography.titleLarge)
                                                Text(auth.currentUser?.email ?: "", color = Grey)
                                            }
                                        }
                                    }
                                    item { Text(if (ar) "صورتك الرمزية" else "Your avatar", color = Color.White) }
                                    item {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("🎮", "👾", "🦊", "🐼", "🐉", "🤖", "👑").forEach {
                                                Box(Modifier.size(42.dp).background(if (avatar == it) Purple.copy(alpha = .4f) else CardBg, CircleShape)
                                                    .clickable { avatar = it }, contentAlignment = Alignment.Center) { Text(it) }
                                            }
                                        }
                                    }
                                    item { Field(username, { username = it }, if (ar) "اسم اللاعب" else "Username") }
                                    item { Text(if (ar) "منصتك" else "Platform", color = Color.White) }
                                    item {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("PlayStation", "Xbox", "PC", "Nintendo").forEach {
                                                FilterChip(selected = platform == it, onClick = { platform = it }, label = { Text(it) })
                                            }
                                        }
                                    }
                                    item {
                                        Button(onClick = {
                                            scope.launch {
                                                try {
                                                    db.collection("users").document(auth.currentUser!!.uid).set(
                                                        mapOf("username" to username, "platform" to platform, "avatar" to avatar),
                                                        com.google.firebase.firestore.SetOptions.merge()).await()
                                                    message = if (ar) "تم حفظ الملف" else "Profile saved"
                                                } catch (e: Exception) { message = e.localizedMessage ?: "Save failed" }
                                            }
                                        }, modifier = Modifier.fillMaxWidth()) { Text(if (ar) "حفظ الملف" else "Save profile") }
                                    }
                                    item { Text(if (ar) "المنشورات الظاهرة: ${posts.size}" else "Visible community posts: ${posts.size}", color = Aqua) }
                                    item { OutlinedButton(onClick = { auth.signOut(); loggedIn = false; page = "home" }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Logout, null); Text(if (ar) "  تسجيل الخروج" else "  Sign out")
                                    } }
                                }
                            }
                        }
                        if (message.isNotBlank()) Text(message, color = Aqua, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(16.dp), singleLine = true)
}

@Composable
private fun Feed(posts: List<Map<String, Any>>, busy: Boolean, ar: Boolean) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(if (ar) "آخر إنجازات المجتمع" else "COMMUNITY HIGHLIGHTS", color = Aqua, style = MaterialTheme.typography.labelMedium)
            Text(if (ar) "وش تلعب اليوم؟" else "WHAT ARE YOU PLAYING?", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (ar) "اكتشف تجارب اللاعبين ومراجعاتهم" else "Discover player stories and reviews", color = Grey)
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (posts.isEmpty() && !busy) item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎮", style = MaterialTheme.typography.displayMedium)
                    Text(if (ar) "البداية منك!" else "Be the first to post", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (ar) "شارك أول مراجعة لعبة" else "Share your first game review", color = Grey)
                }
            }
        }
        items(posts) { post ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(22.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(Purple.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) { Text("🎮") }
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(post["username"]?.toString() ?: if (ar) "لاعب قيمرز" else "Gamerz player", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(post["platform"]?.toString() ?: "Gamer", color = Grey, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    val image = post["imageUrl"]?.toString()
                    if (!image.isNullOrBlank()) AsyncImage(image, null, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(16.dp)) {
                        Text(post["gameName"]?.toString() ?: "Game", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val p = post["completion"]?.toString() ?: "0"
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (ar) "نسبة الإنجاز" else "COMPLETION", color = Grey)
                            Text("$p%", color = Aqua, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { (p.toFloatOrNull() ?: 0f).coerceIn(0f, 100f) / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp), color = Aqua, trackColor = Night)
                        val review = post["pros"]?.toString().orEmpty()
                        if (review.isNotBlank()) Text(review, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                        if (post["platinum"] == true) Text("🏆 PLATINUM", color = Aqua, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
