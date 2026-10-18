package com.tytebyte.sf2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tytebyte.sf2.engine.Sensitivity

@Composable
fun HomeScreen(
    access: Access,
    sensitivity: Sensitivity,
    onSensitivity: (Sensitivity) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onScan: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("SF2", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Поиск повторов и похожих фото прямо на телефоне", style = MaterialTheme.typography.titleMedium)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Безопасность прежде всего", fontWeight = FontWeight.Bold)
                    Text("• Всё считается на телефоне. У приложения нет доступа в интернет.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Сначала только поиск: ничего не удаляется и не меняется.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Что убрать — решаете вы. В каждой группе обязательно остаётся хотя бы одно фото.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Убранное попадает в корзину Android, откуда его можно вернуть. Перед этим два подтверждения.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("Точность поиска", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Sensitivity.entries.forEach { s ->
            val selected = s == sensitivity
            Card(
                onClick = { onSensitivity(s) },
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            if (s == Sensitivity.BALANCED) "${s.title} (рекомендуется)" else s.title,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(s.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text(
            "Точность можно менять и после проверки — результаты пересчитаются мгновенно.",
            style = MaterialTheme.typography.bodySmall,
        )

        when (access) {
            Access.NONE -> {
                Text(
                    "Для поиска нужен доступ к фото. Приложение только читает их и ничего не отправляет.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) { Text("Разрешить доступ к фото") }
            }
            Access.PARTIAL -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Text(
                            "Выдан доступ только к выбранным фото. Чтобы проверить всю галерею, разрешите доступ ко всем фото в настройках.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Открыть настройки") }
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Проверить выбранные фото") }
            }
            Access.FULL -> Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Начать проверку") }
        }
    }
}

@Composable
fun ScanningScreen(done: Int, total: Int, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Проверяю галерею…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        if (total > 0) {
            LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("Обработано $done из $total")
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Файлы только читаются, ничего не меняется. Можно свернуть приложение, но лучше подождать на этом экране.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) { Text("Отмена") }
    }
}
