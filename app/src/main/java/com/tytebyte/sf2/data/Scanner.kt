package com.tytebyte.sf2.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.tytebyte.sf2.engine.Candidate
import com.tytebyte.sf2.engine.HashEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** Читает файлы ТОЛЬКО на чтение и считает отпечатки. Ничего не пишет и не меняет. */
class Scanner(private val resolver: ContentResolver) {

    class Output(
        val items: Map<Long, ImageItem>,
        val candidates: List<Candidate>,
        val failed: List<ImageItem>,
    )

    suspend fun scan(items: List<ImageItem>, onProgress: (done: Int, total: Int) -> Unit): Output = coroutineScope {
        val sizeCounts = items.groupingBy { it.size }.eachCount()
        val gate = Semaphore(3) // не грузим память и процессор сильнее, чем нужно
        val done = AtomicInteger(0)
        onProgress(0, items.size)
        val results = items.map { item ->
            async(Dispatchers.Default) {
                gate.withPermit {
                    val r = processOne(item, (sizeCounts[item.size] ?: 0) > 1)
                    onProgress(done.incrementAndGet(), items.size)
                    item to r
                }
            }
        }.awaitAll()

        val ok = results.filter { it.second != null }
        Output(
            items = ok.associate { (_, r) -> r!!.first.id to r.first },
            candidates = ok.map { it.second!!.second },
            failed = results.filter { it.second == null }.map { it.first },
        )
    }

    private fun processOne(item: ImageItem, needSha: Boolean): Pair<ImageItem, Candidate>? = try {
        val sha = if (needSha) sha256(item.uri) else null
        var w = item.width
        var h = item.height
        val bmp = decode(item.uri) { dw, dh ->
            if (w <= 0 || h <= 0) {
                w = dw; h = dh
            }
        }
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val sig = HashEngine.signature(px, bmp.width, bmp.height)
        bmp.recycle()
        item.copy(width = w, height = h) to Candidate(item.id, sha, sig)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        null // битый или неподдерживаемый файл: пропускаем, но считаем, чтобы показать пользователю
    }

    private fun decode(uri: Uri, onSize: (Int, Int) -> Unit): Bitmap {
        val src = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            onSize(w, h)
            // Целая степень двойки, чтобы не зависеть от EXIF-поворота: итог 256..512 px по длинной стороне.
            var sample = 1
            while (maxOf(w, h) / (sample * 2) >= TARGET) sample *= 2
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun sha256(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)!!.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TARGET = 256
    }
}
