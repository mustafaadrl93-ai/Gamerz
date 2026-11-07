package com.gamerz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OrbitPlan(
    val title: String, val caption: String,
    val symbol: String, val perks: List<String>,
    val tone: Color, val active: Boolean
)

@Composable
fun GamerzMembershipPreview(onBack: () -> Unit, uid: String) {
    val plans = listOf(
        OrbitPlan(
            "Gamerz الأساس", "مجتمع الألعاب للجميع", "✦",
            listOf("النشر والقصص", "المكتبة والإنجازات", "كل الرتب المكتسبة والتحديات"),
            Color(0xFF8DE6C4), true
        ),
        OrbitPlan(
            "Gamerz ومضة", "هوية شخصية أكثر تميزًا", "✧",
            listOf("إطارات شخصية حصرية من تصميم Gamerz",
                "خيارات ألوان إضافية للبروفايل", "أدوات تخصيص تجميلية"),
            Color(0xFFCE9EFF), false
        ),
        OrbitPlan(
            "Gamerz سديم", "مساحة إبداع إضافية", "◇",
            listOf("ثيمات للبروفايل", "إحصاءات شخصية موسعة",
                "مجموعة أوسع من عناصر التخصيص"),
            Color(0xFFFFC98E), false
        )
    )
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0B0914)),
        verticalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(16.dp)) {
        item {
            Text("GAMERZ / MEMBERSHIP",
                color = Color(0xFFCFA7FF),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text("مسارات العضوية",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineMedium)
            Text("خطط اختيارية — الدفع الآمن مباشرة من Google Play عند تفعيل المنتجات",
                color = Color(0xFFB8A9CB),
                style = MaterialTheme.typography.bodySmall)
        }
        items(plans.size) { i ->
            val plan = plans[i]
            Surface(shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, plan.tone.copy(alpha = 0.64f)),
                color = Color(0xFF1E172C)) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(Brush.linearGradient(listOf(
                                plan.tone.copy(alpha = 0.3f),
                                plan.tone.copy(alpha = 0.13f)))),
                            contentAlignment = Alignment.Center) {
                            Text(plan.symbol, color = plan.tone,
                                style = MaterialTheme.typography.headlineMedium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plan.title, color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge)
                            Text(plan.caption, color = Color(0xFFC9B8D6),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Surface(shape = CircleShape,
                            color = if (plan.active) Color(0xFF234B43) else Color(0xFF40344A)) {
                            Text(if (plan.active) "مجاني" else "Google Play",
                                color = if (plan.active) Color(0xFF9CF6D8) else Color(0xFFE0CCE9),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF443551))
                    plan.perks.forEach { perk ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null,
                                modifier = Modifier.size(17.dp),
                                tint = plan.tone)
                            Spacer(Modifier.width(9.dp))
                            Text(perk, color = Color(0xFFE5D9F0),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp),
                color = Color(0xFF291E36)) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("الإنجازات تُكتسب ولا تُشترى", color = Color(0xFFFFD593),
                        fontWeight = FontWeight.Bold)
                    Text("التحديات والرتب الأساسية متاحة للجميع. أي اشتراك مستقبلي سيكون منفصلًا عن نقاط الإنجاز، ولن يغيّر رتبة المؤسس.",
                        color = Color(0xFFD7C4E4),
                        style = MaterialTheme.typography.bodySmall)
                    Text("يعرض متجر Google Play السعر الحقيقي وشروط التجديد والإلغاء عند توفر الاشتراك.",
                        color = Color(0xFFBAABCA),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { GamerzBillingControls(uid) }
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text("العودة إلى ملفي")
            }
        }
    }
}
