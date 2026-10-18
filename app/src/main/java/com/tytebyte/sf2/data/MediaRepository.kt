package com.tytebyte.sf2.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest

/**
 * Единственное место, которое трогает галерею. Чтение — через MediaStore (только запросы),
 * «удаление» — только через системную корзину с подтверждением самой Android.
 */
class MediaRepository(private val resolver: ContentResolver) {

    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun loadImages(): List<ImageItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val out = ArrayList<ImageItem>()
        // По умолчанию MediaStore не отдаёт файлы из корзины и недокачанные файлы.
        resolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val size = c.getLong(iSize)
                if (size <= 0L) continue
                val id = c.getLong(iId)
                out.add(
                    ImageItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = c.getString(iName) ?: "image_$id",
                        folder = c.getString(iPath) ?: "",
                        size = size,
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        dateAdded = c.getLong(iDate),
                    ),
                )
            }
        }
        return out
    }

    /** Какие из [ids] всё ещё есть в галерее (то есть не ушли в корзину). */
    fun stillPresent(ids: Collection<Long>): Set<Long> {
        val present = HashSet<Long>()
        for (chunk in ids.chunked(500)) {
            val sel = "${MediaStore.Images.Media._ID} IN (${chunk.joinToString(",")})"
            resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), sel, null, null)?.use { c ->
                while (c.moveToNext()) present.add(c.getLong(0))
            }
        }
        return present
    }

    /**
     * Запрос на перемещение в КОРЗИНУ (не удаление). Android сама показывает диалог подтверждения;
     * без согласия пользователя ни один файл не изменится.
     */
    fun createTrashRequest(items: List<ImageItem>): IntentSenderRequest {
        val pi = MediaStore.createTrashRequest(resolver, items.map { it.uri }, true)
        return IntentSenderRequest.Builder(pi.intentSender).build()
    }
}
