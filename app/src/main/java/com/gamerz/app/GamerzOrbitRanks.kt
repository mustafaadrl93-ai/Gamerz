package com.gamerz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ORBIT_FOUNDER_UID = "NdoyBz3UMcWGWQ3DhuwrIbVbZvl2"
private val orbitInk = Color(0xFF0B0913)
private val orbitMuted = Color(0xFFBDAECF)
private val orbitGold = Color(0xFFFFCF83)
private val orbitViolet = Color(0xFFBF87FF)

private data class OrbitRank(
    val level: Int, val name: String, val message: String, val required: Int,
    val tone: Color, val accent: Color
)

// Nine original Gamerz ranks; #9 is reserved for the founder UID by design.
private val orbitRanks = listOf(
    OrbitRank(1, "شرارة", "كل ملحمة تبدأ بخطوة", 0, Color(0xFF94A1BA), Color(0xFF596C8E)),
    OrbitRank(2, "نبضة", "أطلقت إشارتك الأولى", 80, Color(0xFF75DBE7), Color(0xFF237EA5)),
    OrbitRank(3, "متتبع المدار", "مسارك بدأ يتضح", 250, Color(0xFF8FC9FF), Color(0xFF396ECC)),
    OrbitRank(4, "صانع المسار", "تكتب إنجازاتك بنفسك", 650, Color(0xFFA9A0FF), Color(0xFF6656D4)),
    OrbitRank(5, "كاسر الحاجز", "تجاوزت حدود البدايات", 1600, Color(0xFFEF9CFF), Color(0xFF9D4EC1)),
    OrbitRank(6, "صانع الأمجاد", "أثرُك واضح في المجتمع", 3500, Color(0xFFFA94C3), Color(0xFFE0569D)),
    OrbitRank(7, "حارس الأفق", "اسمك حاضر في الميدان", 7800, Color(0xFFFFBA79), Color(0xFFE16D43)),
    OrbitRank(8, "أسطورة النواة", "مكانة تُبنى بجهد طويل", 18000, Color(0xFFFFD778), Color(0xFFE69F2E)),
    OrbitRank(9, "مؤسس النواة", "بصمة البداية لا تغيب", Int.MAX_VALUE, Color(0xFFFFE9A5), Color(0xFFC99A52))
)

private data class OrbitSnapshot(
    val posts: List<Map<String, Any>> = emptyList(),
    val checkins: List<Map<String, Any>> = emptyList(),
    val followers: Int = 0,
    val likes: Int = 0,
    val comments: Int = 0
)

private fun postEpoch(post: Map<String, Any>): Long =
    (post["createdAt"] as? Timestamp)?.seconds ?: 0L

private fun utcDayKey(): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    return cal.get(Calendar.YEAR).toString() + "-" + cal.get(Calendar.DAY_OF_YEAR)
}

private fun utcWeekStartSeconds(): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val offset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    cal.add(Calendar.DAY_OF_YEAR, -offset)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 1000L
}

private fun normalizedGame(post: Map<String, Any>): String =
    post["gameName"]?.toString().orEmpty().trim().lowercase()

private fun isComplete(post: Map<String, Any>): Boolean =
    (post["completion"] as? Number)?.toInt() == 100

private fun isPlatinum(post: Map<String, Any>): Boolean =
    isComplete(post) && post["platinum"] == true &&
        post["platform"]?.toString() == "PlayStation"

private fun distinctGameCount(posts: List<Map<String, Any>>): Int =
    posts.map(::normalizedGame).filter { it.isNotEmpty() }.distinct().size

private fun distinctCompleted(posts: List<Map<String, Any>>): Int =
    distinctGameCount(posts.filter(::isComplete))

private fun distinctPlatinum(posts: List<Map<String, Any>>): Int =
    distinctGameCount(posts.filter(::isPlatinum))

private fun utcDayNumber(epochSeconds: Long): Long = epochSeconds / 86400L

private fun streakDays(checkins: List<Map<String, Any>>, nowSeconds: Long): Int {
    val days = checkins.map { utcDayNumber(postEpoch(it)) }.toSet()
    val today = utcDayNumber(nowSeconds)
    var cursor = if (today in days) today else today - 1
    var count = 0
    while (cursor in days && count < 366) {
        count++
        cursor--
    }
    return count
}

private fun orbitScore(snapshot: OrbitSnapshot): Int {
    val posts = snapshot.posts
    val visits = snapshot.checkins.size
    val streak = streakDays(snapshot.checkins, System.currentTimeMillis() / 1000L)
    return posts.size * 20 +
        distinctCompleted(posts) * 85 +
        distinctPlatinum(posts) * 150 +
        visits * 22 +
        streak.coerceAtMost(30) * 10 +
        snapshot.followers.coerceAtMost(300) * 6
}

private fun absenceDays(snapshot: OrbitSnapshot): Int {
    val latest = (snapshot.posts.map(::postEpoch) +
        snapshot.checkins.map(::postEpoch)).maxOrNull() ?: 0L
    if (latest == 0L) return 0
    val now = System.currentTimeMillis() / 1000L
    return ((now - latest).coerceAtLeast(0L) / 86400L).toInt()
}

// Cosmetic rank, calculated from Firestore activity; no payment entitlements rely on XP.
private fun effectiveScore(snapshot: OrbitSnapshot, uid: String): Int {
    if (uid == ORBIT_FOUNDER_UID) return orbitScore(snapshot)
    val inactivity = (absenceDays(snapshot) - 3).coerceAtLeast(0)
    val percentLost = (inactivity * 2).coerceAtMost(80)
    return orbitScore(snapshot) * (100 - percentLost) / 100
}

private fun currentRank(uid: String, effective: Int): OrbitRank {
    if (uid == ORBIT_FOUNDER_UID) return orbitRanks.last()
    return orbitRanks.take(8).lastOrNull { effective >= it.required } ?: orbitRanks.first()
}

private suspend fun loadOrbit(
    uid: String, db: FirebaseFirestore, fetchEngagement: Boolean
): OrbitSnapshot {
    val posts = db.collection("posts").whereEqualTo("uid", uid)
        .limit(500).get().await().documents.mapNotNull { doc ->
            doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
        }
    val checkins = db.collection("users").document(uid).collection("checkins")
        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
        .limit(366).get().await().documents.mapNotNull { doc ->
            doc.data?.toMutableMap()?.apply { put("_id", doc.id) }
        }
    val followers = db.collection("follows").whereEqualTo("targetUid", uid)
        .count().get(AggregateSource.SERVER).await().count.coerceAtMost(20000).toInt()
    var receivedLikes = 0
    var receivedComments = 0
    if (fetchEngagement) {
        val recent = posts.sortedByDescending(::postEpoch).take(6)
        for (post in recent) {
            val postId = post["_id"]?.toString().orEmpty()
            if (postId.isEmpty()) continue
            val ref = db.collection("posts").document(postId)
            try {
                receivedLikes += ref.collection("likes")
                    .count().get(AggregateSource.SERVER).await().count.toInt()
                receivedComments += ref.collection("comments")
                    .count().get(AggregateSource.SERVER).await().count.toInt()
            } catch (_: Exception) { }
        }
    }
    return OrbitSnapshot(posts, checkins, followers, receivedLikes, receivedComments)
}

@Composable
fun GamerzOrbitSigil(rank: Int, modifier: Modifier = Modifier, size: Dp = 66.dp) {
    val data = orbitRanks[(rank - 1).coerceIn(0, 8)]
    val color = data.tone
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = center.x
            val cy = center.y
            val r = this.size.minDimension * 0.42f
            drawCircle(brush = Brush.sweepGradient(listOf(
                data.accent, color, Color.White, data.accent
            )), radius = r, style = Stroke(width = 2.5.dp.toPx()))
            drawCircle(color.copy(alpha = 0.24f),
                radius = r * 0.73f, style = Stroke(width = 1.dp.toPx()))
            val diamond = Path().apply {
                moveTo(cx, cy - r * 0.66f)
                lineTo(cx + r * 0.58f, cy)
                lineTo(cx, cy + r * 0.66f)
                lineTo(cx - r * 0.58f, cy)
                close()
            }
            drawPath(diamond, color, style = Stroke(width = 2.2.dp.toPx()))
            drawCircle(color, radius = r * 0.16f)
            drawCircle(Color.White.copy(alpha = 0.78f), radius = r * 0.07f)
            val rays = (rank + 2).coerceAtMost(11)
            for (i in 0 until rays) {
                val angle = 2.0 * PI * i.toDouble() / rays - PI / 2
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(color.copy(alpha = 0.87f),
                    Offset(cx + dx * r * 0.81f, cy + dy * r * 0.81f),
                    Offset(cx + dx * r * 0.97f, cy + dy * r * 0.97f),
                    strokeWidth = 2.dp.toPx())
            }
        }
    }
}

@Composable
fun GamerzOrbitRankPill(uid: String, db: FirebaseFirestore) {
    var rank by remember(uid) { mutableStateOf(orbitRanks.first()) }
    LaunchedEffect(uid) {
        if (uid.isBlank()) return@LaunchedEffect
        if (uid == ORBIT_FOUNDER_UID) {
            rank = orbitRanks.last()
        } else try {
            rank = currentRank(uid, effectiveScore(loadOrbit(uid, db, false), uid))
        } catch (_: Exception) { }
    }
    Surface(shape = RoundedCornerShape(50),
        color = Color(0xFF292037), border = BorderStroke(1.dp, rank.tone.copy(alpha = 0.65f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GamerzOrbitSigil(rank.level, size = 28.dp)
            Spacer(Modifier.width(7.dp))
            Text(rank.name, color = rank.tone, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class OrbitQuest(
    val name: String, val subtitle: String, val icon: String,
    val value: Int, val goal: Int
)

@Composable
private fun QuestCard(quest: OrbitQuest) {
    val complete = quest.value >= quest.goal
    val tone = if (complete) Color(0xFF67E7B9) else orbitViolet
    Surface(
        color = Color(0xFF1E182B),
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, if (complete) Color(0xFF2E625C) else Color(0xFF42334D))
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFF352543)) {
                Text(quest.icon, modifier = Modifier.padding(11.dp),
                    style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(quest.name, modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold, color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(if (complete) "✓" else quest.value.toString() + "/" + quest.goal,
                        color = tone, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium)
                }
                Text(quest.subtitle, color = orbitMuted, style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(
                    progress = { (quest.value.toFloat() / quest.goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = tone, trackColor = Color(0xFF45374F),
                    drawStopIndicator = {}
                )
            }
        }
    }
}

@Composable
private fun OrbitMiniMetric(value: String, label: String, color: Color,
    modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black,
            color = color, style = MaterialTheme.typography.titleLarge)
        Text(label, color = orbitMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OrbitRankPreview(rank: OrbitRank, currentLevel: Int, isFounder: Boolean) {
    val unlocked = isFounder || (rank.level <= currentLevel && rank.level < 9)
    val forbidden = rank.level == 9 && !isFounder
    Surface(
        color = if (unlocked) Color(0xFF322342) else Color(0xFF1A1624),
        shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.dp, if (unlocked) rank.tone else Color(0xFF3D344A)),
        modifier = Modifier.width(136.dp)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GamerzOrbitSigil(rank.level, size = 59.dp)
            Text(rank.name, color = if (unlocked) rank.tone else orbitMuted,
                fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium)
            Text(if (forbidden) "حصرية للمؤسس"
                else if (unlocked) "مفتوحة"
                else rank.required.toString() + " نقطة",
                color = if (unlocked) Color(0xFF95E8C8) else orbitMuted,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun GamerzOrbitRankHub(
    uid: String,
    db: FirebaseFirestore,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember(uid) { mutableStateOf(OrbitSnapshot()) }
    var loading by remember(uid) { mutableStateOf(true) }
    var checkingIn by remember(uid) { mutableStateOf(false) }
    var notice by remember(uid) { mutableStateOf("") }
    var refresh by remember(uid) { mutableIntStateOf(0) }
    val founder = uid == ORBIT_FOUNDER_UID
    val today = utcDayKey()

    LaunchedEffect(uid, refresh) {
        if (uid.isBlank()) return@LaunchedEffect
        try {
            loading = true
            snapshot = loadOrbit(uid, db, true)
            notice = ""
        } catch (e: Exception) {
            notice = "تعذر تحميل التقدم. تحقّق من الاتصال أو قواعد Firebase."
        } finally {
            loading = false
        }
    }

    val totalScore = orbitScore(snapshot)
    val score = effectiveScore(snapshot, uid)
    val rank = currentRank(uid, score)
    val next = if (founder) null else orbitRanks.getOrNull(rank.level)
        ?.takeIf { it.level < 9 }
    val start = rank.required.coerceAtMost(score)
    val progress = if (next == null) 1f
        else ((score - start).toFloat() / (next.required - start).coerceAtLeast(1))
            .coerceIn(0f, 1f)
    val posts = snapshot.posts
    val checkedToday = snapshot.checkins.any {
        it["_id"]?.toString() == today
    }
    val weekStart = utcWeekStartSeconds()
    val weekPosts = posts.filter { postEpoch(it) >= weekStart }
    val weekCheckins = snapshot.checkins.count { postEpoch(it) >= weekStart }
    val weekCompleted = distinctCompleted(weekPosts)
    val weekPlatinum = distinctPlatinum(weekPosts)
    val xpDecay = (totalScore - score).coerceAtLeast(0)
    val missions = listOf(
        OrbitQuest("بصمة حضور", "سجّل دخولك اليوم إلى عالم Gamerz", "✦",
            if (checkedToday) 1 else 0, 1),
        OrbitQuest("ثلاث نبضات", "سجّل حضورك 3 أيام هذا الأسبوع", "◈", weekCheckins, 3),
        OrbitQuest("إيقاع ثابت", "حافظ على 7 أيام حضور في الأسبوع", "⚡", weekCheckins, 7),
        OrbitQuest("أول رسالة", "انشر تجربة لعبة هذا الأسبوع", "✎", weekPosts.size, 1),
        OrbitQuest("صوت المجتمع", "شارك 3 تجارب خلال الأسبوع", "✧", weekPosts.size, 3),
        OrbitQuest("من قلب المغامرة", "أنهِ لعبة بنسبة 100%", "◆", weekCompleted, 1),
        OrbitQuest("جامع النهايات", "أنهِ لعبتين مختلفتين هذا الأسبوع", "◇", weekCompleted, 2),
        OrbitQuest("وهج الكأس", "سجّل كأسًا ذهبيًا هذا الأسبوع", "🏆", weekPlatinum, 1),
        OrbitQuest("مجموعة الضوء", "سجّل كأسين ذهبيين مختلفين", "✴", weekPlatinum, 2),
        OrbitQuest("دائرة الأصدقاء", "اجمع 3 متابعين في مجتمعك", "◎", snapshot.followers, 3),
        OrbitQuest("صانع الصدى", "اجمع 10 متابعين في مجتمعك", "⟡", snapshot.followers, 10),
        OrbitQuest("أثر واضح", "اجمع 5 إعجابات على منشوراتك الأخيرة", "♥", snapshot.likes, 5),
        OrbitQuest("نبض الحوار", "استقبل 3 تعليقات على منشوراتك الأخيرة", "❋",
            snapshot.comments, 3)
    )
    val completedMissions = missions.count { it.value >= it.goal }

    LazyColumn(
        modifier.fillMaxSize().background(orbitInk),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("GAMERZ / ORBIT", color = orbitViolet,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium)
                    Text("مسار اللاعب", color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineMedium)
                }
                Surface(shape = CircleShape, color = Color(0xFF2A1C3C)) {
                    IconButton(onClick = { refresh++ }) {
                        Icon(Icons.Default.Refresh, "تحديث التقدم", tint = orbitViolet)
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(29.dp))
                .background(Brush.linearGradient(listOf(
                    Color(0xFF35204B), Color(0xFF1A132B), Color(0xFF171D32)
                )))
                .border(1.dp, rank.tone.copy(alpha = 0.43f), RoundedCornerShape(29.dp))
                .padding(19.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF56386A)) {
                            Text(if (founder) "FOUNDER / 09" else "RANK / 0" + rank.level,
                                color = Color(0xFFF6DBFF),
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text("✦ مسار النواة", color = Color(0xFFE3D5F1),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GamerzOrbitSigil(rank.level, size = 93.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rank.name, color = rank.tone,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineSmall)
                            Text(rank.message, color = Color(0xFFDBCDE8),
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(7.dp))
                            Text(if (founder) "رتبة المؤسس • ثابتة"
                                 else "المستوى " + rank.level + " من 8",
                                color = Color(0xFFBDAECD),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF5F426C))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("نقاط المدار", color = Color(0xFFE1D2EE),
                            style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Text(score.toString() + " XP", color = rank.tone,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                        color = rank.tone, trackColor = Color(0xFF4A3957),
                        drawStopIndicator = {}
                    )
                    Text(if (founder) "علامة المؤسس ثابتة ولا تتأثر بالغياب."
                         else if (next != null) "تبقى " +
                            (next.required - score).coerceAtLeast(0).toString() +
                            " نقطة للانتقال إلى " + next.name
                         else "وصلت لأعلى رتبة قابلة للاكتساب.",
                        color = Color(0xFFD1C1E2),
                        style = MaterialTheme.typography.bodySmall)
                    if (!founder && xpDecay > 0) {
                        Text("الغياب خفّض رصيد نشاطك بمقدار " +
                            xpDecay + " نقطة مؤقتًا. عُد للنشاط لاستعادة التقدم.",
                            color = Color(0xFFFFCBA4),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(17.dp),
                    color = Color(0xFF21182C)) {
                    OrbitMiniMetric(distinctPlatinum(posts).toString(),
                        "🏆 كؤوس", orbitGold, Modifier.fillMaxWidth().padding(11.dp))
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(17.dp),
                    color = Color(0xFF21182C)) {
                    OrbitMiniMetric(distinctCompleted(posts).toString(),
                        "💯 مكتملة", Color(0xFF91EBC2),
                        Modifier.fillMaxWidth().padding(11.dp))
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(17.dp),
                    color = Color(0xFF21182C)) {
                    OrbitMiniMetric(streakDays(snapshot.checkins,
                        System.currentTimeMillis() / 1000L).toString(),
                        "✦ أيام متتالية", orbitViolet,
                        Modifier.fillMaxWidth().padding(11.dp))
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(23.dp),
                color = Color(0xFF211C30),
                border = BorderStroke(1.dp, Color(0xFF4E3B66))) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("بصمة الحضور", color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (checkedToday) "سجّلت حضورك اليوم ✦"
                             else "مرحبًا بعودتك إلى المدار",
                            color = orbitMuted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = !checkedToday && !checkingIn && !loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8C4ACB),
                            disabledContainerColor = Color(0xFF3B3650)),
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            scope.launch {
                                try {
                                    checkingIn = true
                                    val todayKey = utcDayKey()
                                    val ref = db.collection("users").document(uid)
                                        .collection("checkins").document(todayKey)
                                    if (!ref.get().await().exists()) {
                                        ref.set(mapOf(
                                            "uid" to uid,
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )).await()
                                    }
                                    refresh++
                                } catch (_: Exception) {
                                    notice = "تعذّر تسجيل الحضور. تأكد من نشر القواعد الجديدة."
                                } finally { checkingIn = false }
                            }
                        }
                    ) {
                        Text(if (checkedToday) "تم ✓" else if (checkingIn) "..." else "سجّل حضوري")
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("شارات المدار", color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge)
                Text("تسع هويات بصرية صُممت لمسار Gamerz",
                    color = orbitMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 2.dp)) {
                items(orbitRanks) { tier ->
                    OrbitRankPreview(tier, rank.level, founder)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF25182D)) {
                Row(Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = orbitGold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("تحديات المدار", color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge)
                        Text("حضور • مشاركة • إنجاز • تفاعل",
                            color = orbitMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(completedMissions.toString() + "/" + missions.size,
                        color = orbitGold, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        items(missions) { quest -> QuestCard(quest) }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = orbitViolet) }
        if (notice.isNotEmpty()) item { Text(notice, color = Color(0xFFFFAABF),
            style = MaterialTheme.typography.bodySmall) }
        item {
            Text("تُحسب المهام الأسبوعية من بداية الاثنين بالتوقيت العالمي. رتب Gamerz رمزية وتستند إلى نشاط المجتمع.",
                color = Color(0xFF9D8CB4),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
