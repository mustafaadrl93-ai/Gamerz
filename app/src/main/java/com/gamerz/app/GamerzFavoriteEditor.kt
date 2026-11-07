package com.gamerz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GamerzFavoriteEditor(
    seriesName: String, onSeriesNameChange: (String) -> Unit,
    seriesHasPhoto: Boolean, onPickSeries: () -> Unit, onClearSeries: () -> Unit,
    gameName: String, onGameNameChange: (String) -> Unit,
    gameHasPhoto: Boolean, onPickGame: () -> Unit, onClearGame: () -> Unit,
    busy: Boolean
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21192F)),
        border = BorderStroke(1.dp, Color(0xFF57416F))
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("✦ عالمك المفضل", color = Color(0xFFE7CFFF),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium)
            Text("خانتان اختياريتان؛ أضف اسم السلسلة أو اللعبة وصورتها المصغّرة.",
                color = Color(0xFFC3B1D4), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = seriesName, onValueChange = { onSeriesNameChange(it.take(80)) },
                label = { Text("السلسلة المفضلة (اختياري)") },
                placeholder = { Text("اكتب اسم سلسلتك المفضلة") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy, onClick = onPickSeries,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("صورة السلسلة")
                }
                if (seriesHasPhoto) TextButton(
                    enabled = !busy, onClick = onClearSeries
                ) { Text("إزالة") }
            }
            HorizontalDivider(color = Color(0xFF4D3B64))
            OutlinedTextField(
                value = gameName, onValueChange = { onGameNameChange(it.take(80)) },
                label = { Text("اللعبة المفضلة (اختياري)") },
                placeholder = { Text("اكتب اسم لعبتك المفضلة") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy, onClick = onPickGame,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("صورة اللعبة")
                }
                if (gameHasPhoto) TextButton(
                    enabled = !busy, onClick = onClearGame
                ) { Text("إزالة") }
            }
            Text("يُحفظ اختيارك عند الضغط على حفظ الملف بالأسفل. الصورة اختيارية كذلك.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFBBA4D0))
        }
    }
}
