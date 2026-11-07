package com.gamerz.app

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Remove a Firestore post/story/avatar using the authenticated Netlify endpoint.
 * The server independently reads the content and author; no file UUID or
 * Uploadcare secret leaves the server.
 */
suspend fun deleteGamerzMedia(type: String, id: String) = withContext(Dispatchers.IO) {
    require(type in setOf("post", "story", "avatar"))
    require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
    val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        ?: error("سجّل دخولك مجددًا")
    val connection = (URL(
        "https://gamerz-image-cleanup.netlify.app/.netlify/functions/delete-media"
    ).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 12000
        readTimeout = 20000
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Authorization", "Bearer " + token)
    }
    try {
        connection.outputStream.use { output ->
            output.write(JSONObject().put("type", type).put("id", id)
                .toString().toByteArray(Charsets.UTF_8))
        }
        val code = connection.responseCode
        if (code != 200 && code != 404) {
            throw IllegalStateException(
                "تعذّر حذف الوسائط بأمان (رمز " + code + "). لم نزل المنشور."
            )
        }
    } finally {
        connection.disconnect()
    }
}
