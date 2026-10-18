package com.tytebyte.sf2.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val loc = Locale.getDefault()
    return when {
        bytes >= gb -> String.format(loc, "%.2f ГБ", bytes / gb)
        bytes >= mb -> String.format(loc, "%.1f МБ", bytes / mb)
        bytes >= kb -> String.format(loc, "%.0f КБ", bytes / kb)
        else -> "$bytes Б"
    }
}

fun formatDate(epochSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochSeconds * 1000))

/** Русское склонение: 1 фото, 2 фото-а, 5 фото-в */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> many
        m10 == 1 -> one
        m10 in 2..4 -> few
        else -> many
    }
}

fun photos(n: Int) = "$n ${plural(n, "фото", "фото", "фото")}"
fun files(n: Int) = "$n ${plural(n, "файл", "файла", "файлов")}"
