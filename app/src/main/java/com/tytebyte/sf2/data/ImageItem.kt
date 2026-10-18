package com.tytebyte.sf2.data

import android.net.Uri

data class ImageItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    /** Папка, например «Pictures/Anime/» */
    val folder: String,
    val size: Long,
    val width: Int,
    val height: Int,
    /** Секунды с 1970 */
    val dateAdded: Long,
) {
    val pixels: Long get() = width.toLong() * height.toLong()
}
