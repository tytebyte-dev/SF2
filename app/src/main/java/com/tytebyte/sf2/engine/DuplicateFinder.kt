package com.tytebyte.sf2.engine

import kotlin.math.abs

class Candidate(
    val id: Long,
    /** SHA-256 содержимого файла; считается только для файлов, у которых есть одинаковые по размеру. */
    val sha: String?,
    val sig: ImageSignature,
)

enum class MatchKind(val title: String) {
    EXACT("Точная копия"),
    ALMOST("Почти идентичны"),
    SIMILAR("Похожие"),
}

class RawGroup(val ids: List<Long>, val kind: MatchKind)

object DuplicateFinder {

    fun isMatch(a: Candidate, b: Candidate, s: Sensitivity): Boolean {
        if (a.sha != null && a.sha == b.sha) return true
        val x = a.sig
        val y = b.sig
        if (abs(x.aspectLog - y.aspectLog) > s.maxAspectLog) return false
        if (HashEngine.hamming(x.pHash, y.pHash) > s.maxPHash) return false
        if (HashEngine.hamming(x.dHash, y.dHash) > s.maxDHash) return false
        if (HashEngine.colorDistance(x.color, y.color) > s.maxColorDiff) return false
        return HashEngine.correlation(x.corr, y.corr) >= s.minCorrelation
    }

    fun pairKind(a: Candidate, b: Candidate): MatchKind {
        if (a.sha != null && a.sha == b.sha) return MatchKind.EXACT
        val close = HashEngine.hamming(a.sig.pHash, b.sig.pHash) <= 4 &&
            HashEngine.correlation(a.sig.corr, b.sig.corr) >= 0.97f
        return if (close) MatchKind.ALMOST else MatchKind.SIMILAR
    }

    /**
     * Полная связность: фото попадает в группу, только если оно совпадает СО ВСЕМИ её участниками,
     * а не с одним из них. Это не даёт цепочке «A~B, B~C» склеить разные картинки в одну группу.
     */
    fun group(candidates: List<Candidate>, s: Sensitivity): List<RawGroup> {
        val groups = ArrayList<MutableList<Candidate>>()
        for (c in candidates) {
            val target = groups.firstOrNull { g -> g.all { isMatch(it, c, s) } }
            if (target != null) target.add(c) else groups.add(mutableListOf(c))
        }
        return groups.filter { it.size > 1 }.map { g ->
            var worst = MatchKind.EXACT
            for (i in g.indices) for (j in i + 1 until g.size) {
                val k = pairKind(g[i], g[j])
                if (k.ordinal > worst.ordinal) worst = k
            }
            RawGroup(g.map { it.id }, worst)
        }
    }
}
