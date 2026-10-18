package com.tytebyte.sf2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    group: DupGroup,
    selected: Set<Long>,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onSuggest: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onTrash: () -> Unit,
) {
    val chosen = group.members.filter { it.id in selected }
    val chosenBytes = chosen.sumOf { it.size }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${group.kind.title} · ${photos(group.members.size)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { confirm = true },
                        enabled = chosen.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(
                            if (chosen.isEmpty()) "Отметьте лишние фото"
                            else "В корзину: ${photos(chosen.size)} · ${formatBytes(chosenBytes)}",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = if (chosen.isEmpty()) onSuggest else onClear) {
                            Text(if (chosen.isEmpty()) "Предложить выбор" else "Снять отметки")
                        }
                        TextButton(onClick = onDismiss) { Text("Это не дубликаты") }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Нажмите на фото, чтобы рассмотреть его целиком. Ничего не отмечено заранее: «Предложить выбор» лишь подсказывает, что убрать (всё, кроме лучшего по качеству).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            itemsIndexed(group.members, key = { _, m -> m.id }) { index, m ->
                val isChosen = m.id in selected
                Card(
                    border = if (isChosen) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
                    colors = CardDefaults.cardColors(),
                ) {
                    Box {
                        Thumb(
                            m.uri,
                            Modifier.fillMaxWidth().height(260.dp).background(Color.Black).clickable { onOpenViewer(index) },
                            contentScale = ContentScale.Fit,
                            sizePx = 1024,
                            fit = true,
                        )
                        if (index == 0) Badge("Лучшее по качеству", MaterialTheme.colorScheme.primary, Modifier.align(Alignment.TopStart))
                        if (isChosen) Badge("Уйдёт в корзину", MaterialTheme.colorScheme.error, Modifier.align(Alignment.TopEnd))
                    }
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${m.width}×${m.height} · ${formatBytes(m.size)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${formatDate(m.dateAdded)}${if (m.folder.isNotBlank()) " · ${m.folder}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(m.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isChosen, onCheckedChange = { onToggle(m.id) })
                            Text(if (isChosen) "Убрать в корзину" else "Оставить")
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        ConfirmTrashDialog(
            names = chosen.map { it.name },
            count = chosen.size,
            bytes = chosenBytes,
            keep = group.members.size - chosen.size,
            onConfirm = {
                confirm = false
                onTrash()
            },
            onCancel = { confirm = false },
        )
    }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        modifier = modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ConfirmTrashDialog(
    names: List<String>,
    count: Int,
    bytes: Long,
    keep: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Переместить в корзину?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Будет перемещено: ${photos(count)} (${formatBytes(bytes)}). Останется в галерее: ${photos(keep)} из этой группы.")
                Text(
                    names.take(5).joinToString("\n") { "• $it" } + if (names.size > 5) "\n• …и ещё ${names.size - 5}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Это не удаление навсегда: файлы попадут в корзину Android и их можно восстановить из неё в галерее (обычно в течение 30 дней). Затем система попросит подтвердить ещё раз.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.clickable { checked = !checked }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text("Я посмотрел(а) отмеченные фото и уверен(а)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = checked) {
                Text("Переместить", color = if (checked) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } },
    )
}
