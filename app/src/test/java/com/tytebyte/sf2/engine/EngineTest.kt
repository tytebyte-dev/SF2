package com.tytebyte.sf2.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Синтетические «картинки» из цветных фигур: проверяем, что движок находит копии и не склеивает разное. */
class EngineTest {

    private class Shape(val circle: Boolean, val cx: Double, val cy: Double, val sx: Double, val sy: Double, val rgb: IntArray)
    private class Scene(val bgTop: IntArray, val bgBottom: IntArray, val shapes: List<Shape>)

    private fun scene(seed: Long): Scene {
        val r = Random(seed)
        fun col() = intArrayOf(r.nextInt(256), r.nextInt(256), r.nextInt(256))
        val top = col()
        val bottom = col()
        val shapes = List(14) {
            Shape(r.nextBoolean(), r.nextDouble(), r.nextDouble(), 0.05 + r.nextDouble() * 0.3, 0.05 + r.nextDouble() * 0.3, col())
        }
        return Scene(top, bottom, shapes)
    }

    private fun render(
        sc: Scene, w: Int, h: Int,
        crop: Double = 0.0, noise: Double = 0.0, shift: Int = 0, invert: Boolean = false,
    ): IntArray {
        val rnd = Random(7)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val u = crop + ((x + 0.5) / w) * (1 - 2 * crop)
            val v = crop + ((y + 0.5) / h) * (1 - 2 * crop)
            val c = DoubleArray(3) { k -> sc.bgTop[k] * (1 - v) + sc.bgBottom[k] * v }
            for (s in sc.shapes) {
                val inside = if (s.circle) ((u - s.cx) / s.sx).let { a -> a * a } + ((v - s.cy) / s.sy).let { b -> b * b } < 1
                else Math.abs(u - s.cx) < s.sx && Math.abs(v - s.cy) < s.sy
                if (inside) for (k in 0 until 3) c[k] = s.rgb[k].toDouble()
            }
            val px = IntArray(3) { k ->
                var q = c[k] + shift + (if (noise > 0) rnd.nextGaussian() * noise else 0.0)
                if (invert) q = 255 - q
                q.toInt().coerceIn(0, 255)
            }
            out[y * w + x] = (0xFF shl 24) or (px[0] shl 16) or (px[1] shl 8) or px[2]
        }
        return out
    }

    private fun cand(id: Long, px: IntArray, w: Int, h: Int, sha: String? = null) =
        Candidate(id, sha, HashEngine.signature(px, w, h))

    private val seeds = (1L..12L).toList()

    @Test
    fun resizedCopyMatchesEvenInStrictMode() {
        for (s in seeds) {
            val sc = scene(s)
            val a = cand(1, render(sc, 300, 450), 300, 450)
            val b = cand(2, render(sc, 120, 180), 120, 180)
            assertTrue("seed $s", DuplicateFinder.isMatch(a, b, Sensitivity.STRICT))
        }
    }

    @Test
    fun recompressedBrightenedAndSlightlyCroppedMatchInBalancedMode() {
        for (s in seeds) {
            val sc = scene(s)
            val a = cand(1, render(sc, 300, 450), 300, 450)
            val noisy = cand(2, render(sc, 300, 450, noise = 3.0, shift = 4), 300, 450)
            val cropped = cand(3, render(sc, 300, 450, crop = 0.01), 300, 450)
            assertTrue("noise seed $s", DuplicateFinder.isMatch(a, noisy, Sensitivity.BALANCED))
            assertTrue("crop seed $s", DuplicateFinder.isMatch(a, cropped, Sensitivity.BALANCED))
        }
    }

    @Test
    fun differentPicturesNeverMatchEvenInLooseMode() {
        val cands = seeds.map { s -> cand(s, render(scene(s), 150, 225), 150, 225) }
        for (i in cands.indices) for (j in i + 1 until cands.size) {
            assertFalse("pair ${seeds[i]}/${seeds[j]}", DuplicateFinder.isMatch(cands[i], cands[j], Sensitivity.LOOSE))
        }
    }

    @Test
    fun invertedColorsAreNotADuplicate() {
        for (s in seeds) {
            val sc = scene(s)
            val a = cand(1, render(sc, 150, 225), 150, 225)
            val b = cand(2, render(sc, 150, 225, invert = true), 150, 225)
            assertFalse("seed $s", DuplicateFinder.isMatch(a, b, Sensitivity.LOOSE))
        }
    }

    @Test
    fun differentAspectRatioIsNotADuplicateInStrictMode() {
        val sc = scene(3)
        val a = cand(1, render(sc, 300, 450), 300, 450)
        val b = cand(2, render(sc, 300, 300), 300, 300)
        assertFalse(DuplicateFinder.isMatch(a, b, Sensitivity.STRICT))
    }

    @Test
    fun groupingIsCompleteLinkageAndLabelsKinds() {
        val a1 = cand(1, render(scene(1), 300, 450), 300, 450)
        val a2 = cand(2, render(scene(1), 120, 180), 120, 180)
        val b1 = cand(3, render(scene(2), 300, 450), 300, 450)
        val b2 = cand(4, render(scene(2), 120, 180), 120, 180)
        val c = cand(5, render(scene(3), 300, 450), 300, 450)
        val groups = DuplicateFinder.group(listOf(a1, b1, c, a2, b2), Sensitivity.BALANCED)
        assertEquals(2, groups.size)
        assertEquals(setOf(setOf(1L, 2L), setOf(3L, 4L)), groups.map { it.ids.toSet() }.toSet())
        assertTrue(groups.all { it.kind != MatchKind.EXACT })
    }

    @Test
    fun identicalFilesAreExactEvenIfSignaturesWereToDiffer() {
        val sc = scene(5)
        val sig = render(sc, 150, 225)
        val x = cand(1, sig, 150, 225, sha = "abc")
        val y = cand(2, render(scene(6), 150, 225), 150, 225, sha = "abc")
        assertTrue(DuplicateFinder.isMatch(x, y, Sensitivity.STRICT))
        assertEquals(MatchKind.EXACT, DuplicateFinder.pairKind(x, y))
    }

    @Test
    fun areaResizeAveragesCells() {
        val src = floatArrayOf(0f, 10f, 20f, 30f)
        val out = HashEngine.areaResize(src, 4, 1, 2, 1)
        assertEquals(5f, out[0], 1e-4f)
        assertEquals(25f, out[1], 1e-4f)
    }
}
