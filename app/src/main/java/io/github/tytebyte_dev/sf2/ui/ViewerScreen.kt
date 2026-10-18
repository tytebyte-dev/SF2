package io.github.tytebyte_dev.sf2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Полноэкранный просмотр с масштабированием (щипок, двойной тап) и переключением между копиями группы. */
@Composable
fun ViewerScreen(
    group: DupGroup,
    index: Int,
    selected: Set<Long>,
    onIndex: (Int) -> Unit,
    onToggle: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val i = index.coerceIn(0, group.members.lastIndex)
    val item = group.members[i]
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val isChosen = item.id in selected

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { box = it }
                .pointerInput(item.id) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    })
                }
                .pointerInput(item.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(1f, 6f)
                        val maxX = box.width * (ns - 1f) / 2f
                        val maxY = box.height * (ns - 1f) / 2f
                        offset = if (ns <= 1f) Offset.Zero else Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                        scale = ns
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            Thumb(
                item.uri,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                sizePx = 2048,
                fit = true,
            )
        }

        // верхняя панель
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${i + 1} из ${group.members.size}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }

        // нижняя панель
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (i == 0) Text("Лучшее по качеству в группе", color = Color(0xFFB9ADFF), fontWeight = FontWeight.Bold)
            Text(
                "${item.width}×${item.height} · ${formatBytes(item.size)} · ${formatDate(item.dateAdded)}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (item.folder.isNotBlank()) {
                Text(item.folder, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onIndex(i - 1) }, enabled = i > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущее", tint = if (i > 0) Color.White else Color.Gray)
                }
                IconButton(onClick = { onIndex(i + 1) }, enabled = i < group.members.lastIndex) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Следующее",
                        tint = if (i < group.members.lastIndex) Color.White else Color.Gray,
                    )
                }
                Row(
                    Modifier.weight(1f).clickable { onToggle(item.id) },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (isChosen) "Убрать в корзину" else "Оставить", color = Color.White)
                    Checkbox(
                        checked = isChosen,
                        onCheckedChange = { onToggle(item.id) },
                        colors = CheckboxDefaults.colors(checkmarkColor = Color.White, uncheckedColor = Color.White),
                    )
                }
            }
        }
    }
}
