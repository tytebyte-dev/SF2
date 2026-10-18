package io.github.tytebyte_dev.sf2.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/** Компактный «отпечаток» картинки; хранится в памяти, пока открыт список. */
class ImageSignature(
    val pHash: Long,
    val dHash: Long,
    /** Яркость 16x16, центрирована и нормирована — корреляция считается скалярным произведением. */
    val corr: FloatArray,
    /** Средние R,G,B по сетке 4x4 (48 чисел, 0..1). */
    val color: FloatArray,
    /** ln(ширина / высота) */
    val aspectLog: Double,
)

object HashEngine {
    private const val N = 32
    private val cosTable = Array(N) { u -> DoubleArray(N) { x -> cos((2 * x + 1) * u * PI / (2 * N)) } }

    /** [pixels] — ARGB, как у Bitmap.getPixels. Прозрачность смешивается с белым фоном. */
    fun signature(pixels: IntArray, w: Int, h: Int): ImageSignature {
        require(w > 0 && h > 0 && pixels.size >= w * h)
        val n = w * h
        val r = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        val l = FloatArray(n)
        for (i in 0 until n) {
            val p = pixels[i]
            val a = (p ushr 24) / 255f
            val rr = ((p shr 16) and 0xFF) * a + 255f * (1f - a)
            val gg = ((p shr 8) and 0xFF) * a + 255f * (1f - a)
            val bb = (p and 0xFF) * a + 255f * (1f - a)
            r[i] = rr; g[i] = gg; b[i] = bb
            l[i] = 0.299f * rr + 0.587f * gg + 0.114f * bb
        }

        val pHash = pHash(areaResize(l, w, h, 32, 32))

        val g98 = areaResize(l, w, h, 9, 8)
        var dHash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            if (g98[y * 9 + x] < g98[y * 9 + x + 1]) dHash = dHash or (1L shl (y * 8 + x))
        }

        val corr = normalize(areaResize(l, w, h, 16, 16))

        val color = FloatArray(48)
        val rs = areaResize(r, w, h, 4, 4)
        val gs = areaResize(g, w, h, 4, 4)
        val bs = areaResize(b, w, h, 4, 4)
        for (i in 0 until 16) {
            color[i] = rs[i] / 255f
            color[16 + i] = gs[i] / 255f
            color[32 + i] = bs[i] / 255f
        }
        return ImageSignature(pHash, dHash, corr, color, ln(w.toDouble() / h.toDouble()))
    }

    /** Усреднение по площади: [src] размером w*h -> nw*nh. Работает и при уменьшении, и при увеличении. */
    fun areaResize(src: FloatArray, w: Int, h: Int, nw: Int, nh: Int): FloatArray {
        val out = FloatArray(nw * nh)
        for (oy in 0 until nh) {
            val y0 = oy * h / nh
            val y1 = minOf(h, maxOf(y0 + 1, ((oy + 1) * h + nh - 1) / nh))
            for (ox in 0 until nw) {
                val x0 = ox * w / nw
                val x1 = minOf(w, maxOf(x0 + 1, ((ox + 1) * w + nw - 1) / nw))
                var sum = 0f
                for (y in y0 until y1) {
                    val row = y * w
                    for (x in x0 until x1) sum += src[row + x]
                }
                out[oy * nw + ox] = sum / ((y1 - y0) * (x1 - x0))
            }
        }
        return out
    }

    /** pHash: DCT 32x32, берём низкие частоты 8x8, бит = коэффициент больше медианы (DC не используется). */
    fun pHash(gray32: FloatArray): Long {
        val tmp = DoubleArray(N * 8)
        for (y in 0 until N) for (u in 0 until 8) {
            var s = 0.0
            for (x in 0 until N) s += gray32[y * N + x] * cosTable[u][x]
            tmp[y * 8 + u] = s
        }
        val coef = DoubleArray(64)
        for (v in 0 until 8) for (u in 0 until 8) {
            var s = 0.0
            for (y in 0 until N) s += tmp[y * 8 + u] * cosTable[v][y]
            coef[v * 8 + u] = s
        }
        val median = coef.copyOfRange(1, 64).sortedArray()[31]
        var bits = 0L
        for (i in 1 until 64) if (coef[i] > median) bits = bits or (1L shl i)
        return bits
    }

    private fun normalize(v: FloatArray): FloatArray {
        var mean = 0f
        for (x in v) mean += x
        mean /= v.size
        var norm = 0f
        val out = FloatArray(v.size)
        for (i in v.indices) {
            out[i] = v[i] - mean
            norm += out[i] * out[i]
        }
        norm = sqrt(norm)
        // Почти однотонная картинка: корреляция бессмысленна, оставляем нули.
        if (norm < 1e-3f) return FloatArray(v.size)
        for (i in out.indices) out[i] /= norm
        return out
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Коэффициент корреляции яркости, -1..1. Две однотонные картинки считаются совпадающими. */
    fun correlation(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var za = true
        var zb = true
        for (i in a.indices) {
            dot += a[i] * b[i]
            if (a[i] != 0f) za = false
            if (b[i] != 0f) zb = false
        }
        if (za && zb) return 1f
        if (za || zb) return 0f
        return dot
    }

    /** Средняя абсолютная разница цветов сетки 4x4, 0..1. */
    fun colorDistance(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += kotlin.math.abs(a[i] - b[i])
        return s / a.size
    }
}
