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
import coil.compose.AsyncImage
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

private suspend fun uploadImageToUploadcare(bytes: ByteArray): Pair<String, String> = withContext(Dispatchers.IO) {
    val boundary = "----GamerzUpload" + System.currentTimeMillis()
    val connection = (URL("https://upload.uploadcare.com/base/").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 30000
        readTimeout = 60000
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    }
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
    connection.disconnect()
    check(status in 200..299) { "Image upload failed ($status): $response" }
    val fileId = JSONObject(response).getString("file")
    val url = "https://ucarecdn.com/$fileId/"
    Pair(url, "uploadcare/$fileId")
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
                            require(Regex("^[A-Za-z0-9]{2,20}$").matches(username)) { "Username must be 2-20 letters, numbers or underscore." }
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
                            val (url, uploadPath) = uploadImageToUploadcare(bytes)
                            functions.getHttpsCallable("createPost").call(mapOf(
                                "gameName" to game.trim(), "completion" to percent, "imageUrl" to url,
                                "storagePath" to uploadPath, "pros" to pros, "cons" to cons,
                                "platinum" to (platform == "PlayStation" && percent == 100 && platinum)
                            )).await()
                            game = ""; completion = "0"; pros = ""; cons = ""; imageUri = null; platinum = false
                            message = "Post published"
                            posts = db.collection("posts").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(30).get().await().documents.mapNotNull { it.data }
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
                                    AsyncImage(model = post["imageUrl"], contentDescription = post["gameName"]?.toString(), modifier = Modifier.fillMaxWidth().height(190.dp))
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
