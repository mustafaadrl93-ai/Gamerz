package com.gamerz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

private val chromeBright = Color(0xFFCE9AFF)
private val chromeText = Color(0xFFE9DFF5)
private val chromeDim = Color(0xFFA79AB8)

@Composable
fun GamerzOrbitTopBar(onActivity: () -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(
                Color(0xFF231235), Color(0xFF100E1E), Color(0xFF201333)
            )))
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = Color(0xFF342244)) {
            IconButton(onClick = onActivity) {
                Icon(Icons.Default.NotificationsNone, contentDescription = "النشاط",
                    tint = chromeText)
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(14.dp),
            color = Color(0xFF4B2A67)) {
            Icon(Icons.Default.SportsEsports, contentDescription = null,
                tint = Color(0xFFE7B5FF),
                modifier = Modifier.padding(8.dp).size(27.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Gamerz", fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White)
            Text("✦ مجتمع اللاعبين", style = MaterialTheme.typography.labelSmall,
                color = chromeBright)
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = Color(0xFF342244)) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "بحث",
                    tint = chromeText)
            }
        }
    }
}

private data class OrbitTab(val key: String, val title: String)

@Composable
fun GamerzOrbitBottomBar(page: String, onNavigate: (String) -> Unit) {
    val tabs = listOf(
        OrbitTab("feed", "الرئيسية"),
        OrbitTab("search", "اكتشف"),
        OrbitTab("create", "نشر"),
        OrbitTab("activity", "النشاط"),
        OrbitTab("profile", "ملفي")
    )
    Row(Modifier.fillMaxWidth()
        .background(Color(0xFF120E1D))
        .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly) {
        tabs.forEach { tab ->
            val selected = if (tab.key == "profile")
                page in listOf("profile", "achievements", "library", "saved", "membership", "admin")
                else page == tab.key
            Column(
                Modifier.weight(1f).clickable { onNavigate(tab.key) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(Modifier.size(if (tab.key == "create") 52.dp else 43.dp)
                    .clip(RoundedCornerShape(if (tab.key == "create") 18.dp else 15.dp))
                    .background(
                        if (tab.key == "create") Brush.linearGradient(listOf(
                            Color(0xFF8B4AF9), Color(0xFFDF6DE6)))
                        else if (selected) Brush.linearGradient(listOf(
                            Color(0xFF55316D), Color(0xFF37244B)))
                        else Brush.linearGradient(listOf(
                            Color.Transparent, Color.Transparent))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    val vector = when (tab.key) {
                        "feed" -> if (selected) Icons.Default.Home else Icons.Default.Home
                        "search" -> Icons.Default.Explore
                        "create" -> Icons.Default.Add
                        "activity" -> Icons.Default.NotificationsNone
                        else -> Icons.Default.PersonOutline
                    }
                    Icon(vector, tab.title,
                        tint = if (tab.key == "create") Color.White
                            else if (selected) chromeBright else chromeDim,
                        modifier = Modifier.size(if (tab.key == "create") 29.dp else 24.dp))
                }
                Text(tab.title,
                    color = if (selected) chromeText else chromeDim,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelSmall)
                Box(Modifier.width(20.dp).height(3.dp).clip(CircleShape)
                    .background(if (selected) chromeBright else Color.Transparent))
            }
        }
    }
}
