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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

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
    val storage = remember { FirebaseStorage.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var platform by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var game by remember { mutableStateOf("") }
    var completion by remember { mutableStateOf("0") }
    var pros by remember { mutableStateOf("") }
    var cons by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var platinum by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var loggedIn by remember { mutableStateOf(auth.currentUser != null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF5B3DF5))) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gamerz | قيمرز", style = MaterialTheme.typography.headlineMedium)
                if (platform.isBlank()) {
                    Text(stringResource(R.string.choose_platform))
                    listOf("PlayStation", "Xbox", "PC").forEach { p ->
                        Button(onClick = { platform = p }, modifier = Modifier.fillMaxWidth()) { Text(p) }
                    }
                } else if (!loggedIn) {
                    OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.username)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { scope.launch {
                        try {
                            require(Regex("^[A-Za-z0-9_]{2,20}$").matches(username)) { "Username must be 2-20 letters, numbers or underscore." }
                            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                            db.collection("users").document(result.user!!.uid).set(mapOf("username" to username, "platform" to platform, "createdAt" to System.currentTimeMillis())).await()
                            loggedIn = true
                        } catch (e: Exception) { message = e.message ?: "Sign up failed" }
                    } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.signup)) }
                    Button(onClick = { scope.launch {
                        try { auth.signInWithEmailAndPassword(email.trim(), password).await(); loggedIn = true }
                        catch (e: Exception) { message = e.message ?: "Login failed" }
                    } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login)) }
                    TextButton(onClick = { platform = "" }) { Text("Change platform") }
                } else {
                    Text(stringResource(R.string.feed), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(game, { game = it }, label = { Text(stringResource(R.string.game_name)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(completion, { completion = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.completion)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pros, { pros = it }, label = { Text("Pros (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cons, { cons = it }, label = { Text("Cons (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pick_image)) }
                    imageUri?.let { Image(painter = rememberAsyncImagePainter(it), contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp)) }
                    if (platform == "PlayStation" && completion.toIntOrNull() == 100) {
                        Row { Checkbox(platinum, { platinum = it }); Text(stringResource(R.string.platinum_question)) }
                    }
                    Button(onClick = { scope.launch {
                        try {
                            val percent = completion.toIntOrNull() ?: error("Enter completion from 0 to 100")
                            require(game.isNotBlank() && percent in 0..100 && imageUri != null) { "Image, game name and completion (0-100) are required." }
                            require(platform == "PlayStation" || !platinum) { "Platinum is PlayStation only." }
                            val uid = auth.currentUser!!.uid
                            val bytes = context.contentResolver.openInputStream(imageUri!!).use { input ->
                                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                                val out = ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
                                out.toByteArray()
                            }
                            val ref = storage.reference.child("postImages/$uid/${System.currentTimeMillis()}.jpg")
                            ref.putBytes(bytes).await()
                            val url = ref.downloadUrl.await().toString()
                            db.collection("posts").add(mapOf("uid" to uid, "platform" to platform, "gameName" to game.trim(), "completion" to percent, "imageUrl" to url, "pros" to pros, "cons" to cons, "platinum" to (platform == "PlayStation" && percent == 100 && platinum), "createdAt" to System.currentTimeMillis(), "likes" to 0, "commentCount" to 0)).await()
                            game = ""; completion = "0"; pros = ""; cons = ""; imageUri = null; platinum = false
                            message = "Post published"
                        } catch (e: Exception) { message = e.message ?: "Publish failed" }
                    } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.publish)) }
                    Button(onClick = { scope.launch {
                        try { posts = db.collection("posts").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(30).get().await().documents.mapNotNull { it.data } }
                        catch (e: Exception) { message = e.message ?: "Feed failed" }
                    } }, modifier = Modifier.fillMaxWidth()) { Text("Refresh feed") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(posts) { post ->
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(post["gameName"]?.toString() ?: "Game", style = MaterialTheme.typography.titleMedium)
                                    Text("${post["completion"] ?: 0}% complete")
                                    if (post["platinum"] == true) Text("👑 PLATINUM")
                                    Text(post["pros"]?.toString() ?: "")
                                    Text(post["cons"]?.toString() ?: "")
                                }
                            }
                        }
                    }
                    TextButton(onClick = { auth.signOut(); loggedIn = false }) { Text("Sign out") }
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
