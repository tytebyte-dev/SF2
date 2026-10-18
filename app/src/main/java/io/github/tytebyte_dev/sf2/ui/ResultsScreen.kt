package io.github.tytebyte_dev.sf2.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tytebyte_dev.sf2.engine.Sensitivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    state: UiState,
    onBack: () -> Unit,
    onSensitivity: (Sensitivity) -> Unit,
    onOpen: (String) -> Unit,
    onResetDismissed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Результаты") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Проверено: ${photos(state.totalImages)}", fontWeight = FontWeight.SemiBold)
                        if (state.groups.isNotEmpty()) {
                            val total = state.groups.sumOf { it.reclaimable }
                            Text("Найдено групп: ${state.groups.size}. Если в каждой оставить лучшее фото, освободится до ${formatBytes(total)}.")
                        }
                        Text("Пока ничего не удалено и не изменено.", style = MaterialTheme.typography.bodySmall)
                        if (state.failedCount > 0) {
                            Text(
                                "Не удалось прочитать и пропущено: ${files(state.failedCount)}.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sensitivity.entries.forEach { s ->
                        FilterChip(
                            selected = state.sensitivity == s,
                            onClick = { onSensitivity(s) },
                            label = { Text(s.title) },
                            leadingIcon = if (state.sensitivity == s) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                        )
                    }
                }
            }
            if (state.groups.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Повторов не найдено", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.sensitivity == Sensitivity.LOOSE) "Даже в самом широком режиме похожих фото нет."
                                else "Это точность «${state.sensitivity.title}». Для поиска похожих артов попробуйте режим «Широко».",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                items(state.groups, key = { it.key }) { g -> GroupCard(g, onClick = { onOpen(g.key) }) }
            }
            if (state.dismissedCount > 0) {
                item {
                    TextButton(onClick = onResetDismissed) {
                        Text("Скрытых групп: ${state.dismissedCount}. Показать снова")
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(g: DupGroup, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                g.members.take(4).forEach { m ->
                    Thumb(m.uri, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)), sizePx = 200)
                }
                if (g.members.size > 4) Text("+${g.members.size - 4}", style = MaterialTheme.typography.titleMedium)
            }
            Text(g.kind.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("${photos(g.members.size)} · можно убрать до ${formatBytes(g.reclaimable)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
