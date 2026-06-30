package com.ybmusic.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.unit.*
import com.ybmusic.tv.ui.MainViewModel
import com.ybmusic.tv.ui.theme.*

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val quality   by vm.audioQuality.collectAsState()
    val cacheUrls by vm.cacheUrls.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Settings, null, tint = Purple, modifier = Modifier.size(32.dp))
            Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        HorizontalDivider(color = BgVariant)

        // Audio quality
        SettingSection(title = "Chất lượng audio") {
            val opts = listOf("high" to "Cao (opus 160kbps)", "medium" to "Trung bình (128kbps)", "low" to "Thấp (64kbps)")
            opts.forEach { (key, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .focusable()
                        .selectable(selected = quality == key, onClick = { vm.setAudioQuality(key) })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = quality == key,
                        onClick  = { vm.setAudioQuality(key) },
                        colors   = RadioButtonDefaults.colors(selectedColor = Purple),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        HorizontalDivider(color = BgVariant)

        // Cache
        SettingSection(title = "Bộ nhớ đệm") {
            Row(
                Modifier.fillMaxWidth().focusable(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Cache stream URL", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text("Lưu URL 2 giờ để tránh tải lại", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked  = cacheUrls,
                    onCheckedChange = { vm.setCacheUrls(it) },
                    colors   = SwitchDefaults.colors(checkedThumbColor = Purple, checkedTrackColor = Purple.copy(alpha = 0.4f)),
                )
            }
        }

        HorizontalDivider(color = BgVariant)

        // About
        SettingSection(title = "Thông tin") {
            Text("YB Music TV  v1.0.0", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
            Text("Powered by NewPipe Extractor + Media3", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Purple)
        content()
    }
}
