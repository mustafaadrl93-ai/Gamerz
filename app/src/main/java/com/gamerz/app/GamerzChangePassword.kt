package com.gamerz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Change only the existing signed-in Firebase Authentication password.
 * Reauthenticates with the current password before updating, and never writes
 * passwords to Firestore, logcat, analytics, GitHub, or saved state.
 * The current Firebase UID (including owner/admin roles) remains unchanged.
 */
@Composable
fun GamerzChangePasswordScreen(onBack: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    val valid = !busy && !success && oldPassword.isNotBlank() &&
        newPassword.length >= 12 && confirmPassword == newPassword &&
        newPassword != oldPassword

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(
                Color(0xFF120E20), Color(0xFF0B0915), Color(0xFF191126)
            )))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        TextButton(onClick = { if (!busy) onBack() }, enabled = !busy) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("رجوع إلى ملفي")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp),
                color = Color(0xFF38264F),
                border = BorderStroke(1.dp, Color(0xFF9668CB))) {
                Icon(Icons.Default.Security, contentDescription = null,
                    tint = Color(0xFFDCB7FF),
                    modifier = Modifier.padding(15.dp).size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("GAMERZ / SECURITY",
                    color = Color(0xFFC59DFA),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black)
                Text("تغيير كلمة المرور", color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall)
            }
        }
        Surface(shape = RoundedCornerShape(24.dp),
            color = Color(0xFF231A31),
            border = BorderStroke(1.dp, Color(0xFF4E3A61))) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("لحماية حسابك، أكّد كلمة المرور الحالية ثم عيّن كلمة جديدة مختلفة عن حساب Google.",
                    color = Color(0xFFD6C8E6),
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { if (!busy && !success) { oldPassword = it; errorText = "" } },
                    label = { Text("كلمة المرور الحالية") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, enabled = !busy && !success,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { if (!busy && !success) { newPassword = it; errorText = "" } },
                    label = { Text("كلمة المرور الجديدة (12 حرفًا على الأقل)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, enabled = !busy && !success,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { if (!busy && !success) { confirmPassword = it; errorText = "" } },
                    label = { Text("تأكيد كلمة المرور الجديدة") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, enabled = !busy && !success,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
                Text("استخدم كلمة فريدة وقوية، واحفظها في مدير كلمات مرور. لا ترسلها لأحد.",
                    color = Color(0xFFBDAACF),
                    style = MaterialTheme.typography.bodySmall)

                if (errorText.isNotBlank()) {
                    Text(errorText, color = Color(0xFFFFA6B6),
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (success) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = Color(0xFF82E6BD))
                        Spacer(Modifier.width(8.dp))
                        Text("تم تغيير كلمة المرور بنجاح. حسابك ورتبة المؤسس كما هما.",
                            color = Color(0xFFB7F7DB),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                busy = true
                                errorText = ""
                                val user = auth.currentUser
                                    ?: error("انتهت الجلسة. سجّل الدخول ثم حاول مجددًا.")
                                val email = user.email
                                    ?: error("هذا الحساب لا يملك بريدًا لتسجيل الدخول بكلمة مرور.")
                                // Firebase Auth checks current credentials on the server.
                                user.reauthenticate(
                                    EmailAuthProvider.getCredential(email, oldPassword)
                                ).await()
                                user.updatePassword(newPassword).await()
                                oldPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                success = true
                            } catch (e: FirebaseAuthException) {
                                errorText = when (e.errorCode) {
                                    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL",
                                    "ERROR_INVALID_LOGIN_CREDENTIALS" ->
                                        "كلمة المرور الحالية غير صحيحة. حاول مرة أخرى."
                                    "ERROR_TOO_MANY_REQUESTS" ->
                                        "محاولات كثيرة. أعد المحاولة لاحقًا."
                                    "ERROR_WEAK_PASSWORD" ->
                                        "اختر كلمة مرور أقوى."
                                    else ->
                                        "تعذّر تغيير كلمة المرور. تحقّق من الاتصال وحاول مجددًا."
                                }
                            } catch (_: Exception) {
                                errorText = "تعذّر تغيير كلمة المرور. تأكد من اتصالك ثم حاول مجددًا."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = valid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9652D6),
                        disabledContainerColor = Color(0xFF42354C)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    if (busy) CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp))
                    else Text(if (success) "تم التحديث" else "تأكيد تغيير كلمة المرور",
                        fontWeight = FontWeight.Bold)
                }
                if (success) {
                    OutlinedButton(onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)) {
                        Text("العودة إلى الملف الشخصي")
                    }
                }
            }
        }
    }
}
