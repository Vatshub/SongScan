package com.example.analyzer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SongScan analysis engine. Pure Kotlin, no Android dependencies.
 * Input: mono PCM at [SR] Hz in the range -1..1.
 * Measures tempo, key, chords, sections and energy from the audio itself.
 */
object SongScanEngine {
    const val SR = 22050

    // ---------- Results ----------
    data class Tempo(val bpm: Int, val alt: Int, val confidence: Double)
    data class Key(
        val tonic: Int, val minor: Boolean, val name: String, val camelot: String,
        val confidence: Double, val fit: Double, val runnerUp: String
    )
    data class Chord(val name: String, val startSec: Double, val endSec: Double)
    data class Section(
        val name: String, val label: String, val startSec: Double, val endSec: Double,
        val firstBar: Int, val bars: Int, val db: Double, val loop: List<String>, val loopTimes: Int
    )
    data class Structure(
        val bars: Int, val chords: List<Chord>, val sections: List<Section>,
        val mainLoop: List<String>, val mainLoopTimes: Int, val notes: List<String>
    )
    data class Result(
        val durationSec: Double, val tempo: Tempo, val key: Key,
        val avgDb: Double, val peakDb: Double, val energyCurve: FloatArray,
        val structure: Structure?
    )

    // Note names in the spelling the app's MusicTheory table uses
    val NOTES = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    private val MAJ = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MIN = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
    private val CAM_MAJ = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
    private val CAM_MIN = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

    fun keyName(tonic: Int, minor: Boolean) = "${NOTES[tonic]} ${if (minor) "Minor" else "Major"}"
    fun camelot(tonic: Int, minor: Boolean) = if (minor) "${CAM_MIN[tonic]}A" else "${CAM_MAJ[tonic]}B"

    // ---------- FFT ----------
    private class FFT(val n: Int) {
        private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
        private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }
        private val rev = IntArray(n)
        private val win = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        private val re = DoubleArray(n)
        private val im = DoubleArray(n)
        val mag = DoubleArray(n / 2)

        init {
            val levels = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) {
                var r = 0; var x = i
                repeat(levels) { r = (r shl 1) or (x and 1); x = x shr 1 }
                rev[i] = r
            }
        }

        fun run(x: FloatArray, off: Int): DoubleArray {
            for (i in 0 until n) {
                val j = off + i
                re[rev[i]] = (if (j < x.size) x[j].toDouble() else 0.0) * win[i]
                im[rev[i]] = 0.0
            }
            var size = 2
            while (size <= n) {
                val half = size shr 1; val step = n / size
                var i = 0
                while (i < n) {
                    var k = 0
                    for (j in i until i + half) {
                        val l = j + half
                        val tre = re[l] * cosT[k] + im[l] * sinT[k]
                        val tim = -re[l] * sinT[k] + im[l] * cosT[k]
                        re[l] = re[j] - tre; im[l] = im[j] - tim
                        re[j] += tre; im[j] += tim
                        k += step
                    }
                    i += size
                }
                size = size shl 1
            }
            for (i in 0 until n / 2) mag[i] = hypot(re[i], im[i])
            return mag
        }
    }

    // ---------- Onsets ----------
    private class Onsets(val o: DoubleArray, val oLow: DoubleArray, val fps: Double)

    private fun onsetEnvelope(x: FloatArray, progress: (Float) -> Unit): Onsets {
        val n = 1024; val h = 256; val fft = FFT(n)
        val frames = max(1, (x.size - n) / h)
        val flux = DoubleArray(frames); val lowFlux = DoubleArray(frames)
        val prev = DoubleArray(n / 2)
        val lowBins = (250.0 * n / SR).roundToInt()
        for (t in 0 until frames) {
            val m = fft.run(x, t * h)
            var s = 0.0; var sl = 0.0
            for (k in 1 until n / 2) {
                val v = ln1p(100 * m[k]); val d = v - prev[k]
                if (d > 0) { s += d; if (k <= lowBins) sl += d }
                prev[k] = v
            }
            flux[t] = if (t == 0) 0.0 else s; lowFlux[t] = if (t == 0) 0.0 else sl
            if (t % 2000 == 0) progress(0.6f * t / frames)
        }
        val w = 16; val o = DoubleArray(frames); var acc = 0.0
        for (t in 0 until frames) {
            acc += flux[t]; if (t - 2 * w - 1 >= 0) acc -= flux[t - 2 * w - 1]
            val c = t - w
            if (c >= 0) { val cnt = min(t + 1, 2 * w + 1); o[c] = max(0.0, flux[c] - acc / cnt) }
        }
        val lm = lowFlux.average(); val om = o.average()
        val oLow = DoubleArray(frames) { t ->
            (if (lm > 0) lowFlux[t] / lm else 0.0) + 0.3 * (if (om > 0) o[t] / om else 0.0)
        }
        return Onsets(o, oLow, SR.toDouble() / h)
    }

    // ---------- Tempo ----------
    private fun estimateTempo(o: DoubleArray, fps: Double): Tempo {
        val n = o.size; val mean = o.average()
        val z = DoubleArray(n) { o[it] - mean }
        val maxLag = (60 * fps / 30).toInt() + 3
        val ac = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var s = 0.0; var t = 0
            while (t + lag < n) { s += z[t] * z[t + lag]; t++ }
            ac[lag] = if (n - lag > 0) s / (n - lag) else 0.0
        }
        fun at(l: Double): Double {
            val i = floor(l).toInt(); val f = l - i
            return if (i + 1 > maxLag) 0.0 else ac[i] * (1 - f) + ac[i + 1] * f
        }
        val count = 1401 // 60.0 .. 200.0 in 0.1 steps
        val scores = DoubleArray(count)
        var best = Double.NEGATIVE_INFINITY; var bestBpm = 120.0
        for (i in 0 until count) {
            val b = 60.0 + i * 0.1
            val l = 60 * fps / b
            val raw = at(l) + 0.5 * at(2 * l) + 0.25 * at(4 * l)
            val w = exp(-0.5 * (log2(b / 120.0) / 1.0).pow(2))
            val s = raw * (0.6 + 0.4 * w)
            scores[i] = s
            if (s > best) { best = s; bestBpm = b }
        }
        fun near(a: Double, b: Double, tol: Double) = abs(a / b - 1) < tol
        var rival = 0.0
        for (i in 0 until count) {
            val b = 60.0 + i * 0.1
            if (near(b, bestBpm, 0.05) || near(b, bestBpm * 2, 0.04) || near(b, bestBpm / 2, 0.04) ||
                near(b, bestBpm * 1.5, 0.03) || near(b, bestBpm / 1.5, 0.03)) continue
            rival = max(rival, scores[i])
        }
        val conf = if (best <= 0) 0.0 else (1 - max(0.0, rival) / best).coerceIn(0.0, 1.0)
        val bpm = bestBpm.roundToInt()
        val alt = if (bpm < 100) bpm * 2 else (bpm / 2.0).roundToInt()
        return Tempo(bpm, alt, conf)
    }

    // ---------- Key ----------
    private fun pearson(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average(); val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) { val x = a[i] - ma; val y = b[i] - mb; num += x * y; da += x * x; db += y * y }
        return num / sqrt(da * db + 1e-12)
    }

    private fun estimateKey(x: FloatArray, progress: (Float) -> Unit): Key {
        val n = 8192; val h = 4096; val fft = FFT(n)
        val frames = max(1, (x.size - n) / h)
        val binPc = IntArray(n / 2) { -1 }
        for (k in 1 until n / 2) {
            val f = k.toDouble() * SR / n
            if (f < 80 || f > 2000) continue
            val p = 12 * log2(f / 440) + 69
            binPc[k] = ((p.roundToInt() % 12) + 12) % 12
        }
        val chroma = DoubleArray(12); val c = DoubleArray(12)
        for (t in 0 until frames) {
            val m = fft.run(x, t * h); c.fill(0.0)
            for (k in 1 until n / 2) if (binPc[k] >= 0) c[binPc[k]] += m[k]
            val mx = c.max()
            if (mx > 1e-6) for (i in 0 until 12) chroma[i] += c[i] / mx
            if (t % 200 == 0) progress(0.6f + 0.3f * t / frames)
        }
        data class Cand(val tonic: Int, val minor: Boolean, val score: Double)
        val cands = mutableListOf<Cand>()
        for (r in 0 until 12) {
            val rotMaj = DoubleArray(12) { MAJ[(it - r + 12) % 12] }
            val rotMin = DoubleArray(12) { MIN[(it - r + 12) % 12] }
            cands += Cand(r, false, pearson(chroma, rotMaj))
            cands += Cand(r, true, pearson(chroma, rotMin))
        }
        cands.sortByDescending { it.score }
        val top = cands[0]; val second = cands[1]
        val conf = ((top.score - second.score) / 0.15).coerceIn(0.0, 1.0)
        return Key(top.tonic, top.minor, keyName(top.tonic, top.minor), camelot(top.tonic, top.minor),
            conf, top.score, keyName(second.tonic, second.minor))
    }

    // ---------- Energy ----------
    private class Energy(val avgDb: Double, val peakDb: Double)

    private fun energyLevels(x: FloatArray): Energy {
        var peak = 0.0; var sumSq = 0.0
        for (v in x) { val a = abs(v.toDouble()); if (a > peak) peak = a; sumSq += v.toDouble() * v }
        val overall = sqrt(sumSq / max(1, x.size))
        return Energy(20 * log10(overall + 1e-9), 20 * log10(peak + 1e-9))
    }

    /** [points] normalized loudness values (0..1) across the song, for the waveform display. */
    fun energyCurve(x: FloatArray, points: Int): FloatArray {
        val out = FloatArray(points)
        val per = max(1, x.size / points)
        for (p in 0 until points) {
            var s = 0.0; val a = p * per; val b = min(x.size, a + per)
            for (i in a until b) s += x[i].toDouble() * x[i]
            out[p] = sqrt(s / max(1, b - a)).toFloat()
        }
        val mx = out.maxOrNull()?.takeIf { it > 0f } ?: 1f
        for (p in 0 until points) out[p] = (out[p] / mx).coerceIn(0.04f, 1f)
        return out
    }

    // ---------- Chroma frames for chords and structure ----------
    private class ChromaFrames(val tre: FloatArray, val bas: FloatArray, val en: FloatArray, val frames: Int, val fps: Double)

    private fun chromaFrames(x: FloatArray, progress: (Float) -> Unit): ChromaFrames {
        val n = 4096; val h = 1024; val fft = FFT(n)
        val frames = max(1, (x.size - n) / h)
        val tre = FloatArray(frames * 12); val bas = FloatArray(frames * 12); val en = FloatArray(frames)
        val tpc = IntArray(n / 2) { -1 }; val bpc = IntArray(n / 2) { -1 }
        for (k in 1 until n / 2) {
            val f = k.toDouble() * SR / n
            val pc = (((12 * log2(f / 440) + 69).roundToInt() % 12) + 12) % 12
            if (f in 110.0..2000.0) tpc[k] = pc
            if (f >= 40 && f < 180) bpc[k] = pc
        }
        for (t in 0 until frames) {
            val m = fft.run(x, t * h); var e = 0.0
            for (k in 1 until n / 2) {
                val v = m[k]; e += v * v
                if (tpc[k] >= 0) tre[t * 12 + tpc[k]] += v.toFloat()
                if (bpc[k] >= 0) bas[t * 12 + bpc[k]] += v.toFloat()
            }
            en[t] = e.toFloat()
            if (t % 400 == 0) progress(0.9f + 0.05f * t / frames)
        }
        return ChromaFrames(tre, bas, en, frames, SR.toDouble() / h)
    }

    // ---------- Helpers ----------
    private fun vecAvg(arr: FloatArray, f0d: Double, f1d: Double): DoubleArray {
        val c = DoubleArray(12)
        val f0 = max(0, floor(f0d).toInt()); val f1 = max(f0 + 1, floor(f1d).toInt())
        var f = f0
        while (f < f1 && f * 12 < arr.size) { for (i in 0 until 12) c[i] += arr[f * 12 + i]; f++ }
        return c
    }
    private fun norm(v: DoubleArray): DoubleArray {
        var n = 0.0; for (a in v) n += a * a; n = sqrt(n); if (n == 0.0) n = 1.0
        return DoubleArray(v.size) { v[it] / n }
    }
    private fun cosSim(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { s += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val d = sqrt(na * nb); return if (d == 0.0) 0.0 else s / d
    }

    private class Triad(val root: Int, val minor: Boolean, val t: DoubleArray, val name: String)
    private val TRIADS: List<Triad> = buildList {
        for (r in 0 until 12) for (minor in listOf(false, true)) {
            val iv = if (minor) intArrayOf(0, 3, 7) else intArrayOf(0, 4, 7)
            val t = DoubleArray(12)
            iv.forEachIndexed { i, s -> t[(r + s) % 12] = when (i) { 0 -> 1.0; 1 -> 0.9; else -> 0.8 } }
            add(Triad(r, minor, norm(t), NOTES[r] + if (minor) "m" else ""))
        }
    }

    private fun beatGrid(o: DoubleArray, fps: Double, bpm: Int, duration: Double): MutableList<Double> {
        val p = 60 * fps / bpm; var best = -1.0; var phase = 0.0
        var ph = 0.0
        while (ph < p) {
            var s = 0.0; var t = ph
            while (t < o.size) {
                val i = t.roundToInt()
                for (d in -2..2) { val j = i + d; if (j in o.indices && o[j] > 0) s += o[j] * (if (d == 0) 1.0 else 0.5) }
                t += p
            }
            if (s > best) { best = s; phase = ph }
            ph += 1.0
        }
        val beats = mutableListOf<Double>()
        val period = 60.0 / bpm
        var t = phase / fps
        while (t < duration) { beats += t; t += period }
        if (beats.isNotEmpty() && beats[0] - period > -0.15 * period) beats.add(0, max(0.0, beats[0] - period))
        return beats
    }

    private class ChordUnit(val t0: Double, val t1: Double, val tr: DoubleArray, val bas: DoubleArray, val e: Double) { var chord = "–" }
    private class Seg(val b0: Int, val b1: Int, val vec: DoubleArray, val db: Double, val chords: List<String>) {
        var group = 0; var label = ""; var name = ""
        var start = 0.0; var end = 0.0; var loop: List<String> = emptyList(); var loopTimes = 1
    }

    private fun condenseLoop(seq: List<String>): Pair<List<String>, Int> {
        val clean = seq.filter { it != "–" }
        for (len in intArrayOf(4, 2)) {
            val counts = linkedMapOf<String, Int>()
            for (i in 0..clean.size - len) { val k = clean.subList(i, i + len).joinToString(" | "); counts[k] = (counts[k] ?: 0) + 1 }
            val best = counts.maxByOrNull { it.value }
            if (best != null && best.value >= 2) {
                var times = 0; var i = 0
                while (i + len <= clean.size) {
                    if (clean.subList(i, i + len).joinToString(" | ") == best.key) { times++; i += len } else i++
                }
                if (times >= 2) return best.key.split(" | ") to times
            }
        }
        val uniq = mutableListOf<String>(); clean.forEach { if (uniq.lastOrNull() != it) uniq += it }
        return uniq.take(8) to 1
    }

    fun fmt(s: Double): String { val t = max(0.0, s); return "%d:%02d".format((t / 60).toInt(), (t % 60).toInt()) }

    private fun analyzeStructure(cf: ChromaFrames, o: DoubleArray, ofps: Double, tempo: Tempo, duration: Double): Structure? {
        val beats = beatGrid(o, ofps, tempo.bpm, duration)
        if (beats.size < 16) return null
        fun fr(t: Double) = t * cf.fps
        val beatChroma = beats.mapIndexed { k, t -> norm(vecAvg(cf.tre, fr(t), fr(beats.getOrNull(k + 1) ?: duration))) }
        var bestOff = 0; var bestS = -1.0
        for (off in 0 until 4) {
            var s = 0.0; var k = off
            while (k < beats.size) { if (k > 0) s += 1 - cosSim(beatChroma[k - 1], beatChroma[k]); k += 4 }
            if (s > bestS) { bestS = s; bestOff = off }
        }
        data class Bar(val start: Double, val end: Double)
        val bars = mutableListOf<Bar>()
        run {
            var k = bestOff
            while (k < beats.size) {
                if (beats[k] < duration - 120.0 / tempo.bpm) bars += Bar(beats[k], min(duration, beats.getOrNull(k + 4) ?: duration))
                k += 4
            }
        }
        val unitBeats = if (tempo.bpm > 140) 4 else 2
        val units = mutableListOf<ChordUnit>()
        run {
            var k = bestOff
            while (k < beats.size) {
                val t0 = beats[k]; val t1 = beats.getOrNull(k + unitBeats) ?: duration
                val tr = vecAvg(cf.tre, fr(t0), fr(t1)); val ba = vecAvg(cf.bas, fr(t0), fr(t1))
                var e = 0.0; var f = floor(fr(t0)).toInt()
                while (f < floor(fr(t1)).toInt() && f < cf.frames) { e += cf.en[f]; f++ }
                val bmax = ba.max().takeIf { it > 0 } ?: 1.0
                units += ChordUnit(t0, t1, norm(tr), DoubleArray(12) { ba[it] / bmax }, e)
                k += unitBeats
            }
        }
        if (units.isEmpty()) return null
        val eSorted = units.map { it.e }.sorted()
        val eRef = eSorted[(eSorted.size * 0.9).toInt().coerceAtMost(eSorted.size - 1)].takeIf { it > 0 } ?: 1.0
        // Viterbi over 24 triads + no-chord
        val states = TRIADS.size + 1; val pen = 0.1
        fun emit(u: ChordUnit): DoubleArray {
            val silent = u.e < eRef * 0.003
            return DoubleArray(states) { s -> if (s < TRIADS.size) cosSim(u.tr, TRIADS[s].t) + 0.12 * u.bas[TRIADS[s].root] else if (silent) 2.0 else 0.45 }
        }
        var score = emit(units[0]); val back = ArrayList<IntArray>()
        for (i in 1 until units.size) {
            val em = emit(units[i]); var bestPrev = 0
            for (s in 1 until states) if (score[s] > score[bestPrev]) bestPrev = s
            val ns = DoubleArray(states); val bk = IntArray(states)
            for (s in 0 until states) {
                val stay = score[s]; val sw = score[bestPrev] - pen
                if (stay >= sw) { ns[s] = stay + em[s]; bk[s] = s } else { ns[s] = sw + em[s]; bk[s] = bestPrev }
            }
            back += bk; score = ns
        }
        var st = 0; for (s in 1 until states) if (score[s] > score[st]) st = s
        val path = IntArray(units.size); path[units.size - 1] = st
        for (i in units.size - 2 downTo 0) path[i] = back[i][path[i + 1]]
        units.forEachIndexed { i, u -> u.chord = if (path[i] == TRIADS.size) "–" else TRIADS[path[i]].name }
        val chords = mutableListOf<Chord>()
        for (u in units) {
            val last = chords.lastOrNull()
            if (last != null && last.name == u.chord) chords[chords.size - 1] = last.copy(endSec = u.t1)
            else chords += Chord(u.chord, u.t0, u.t1)
        }
        val nb = bars.size
        if (nb < 4) return Structure(nb, chords, emptyList(), emptyList(), 0, emptyList())
        val barChord = bars.map { b ->
            val names = mutableListOf<String>()
            units.filter { it.t0 >= b.start - 0.01 && it.t0 < b.end - 0.01 }.forEach { if (names.lastOrNull() != it.chord) names += it.chord }
            if (names.isEmpty()) "–" else names.joinToString(" ")
        }
        val barVec = bars.map { vecAvg(cf.tre, fr(it.start), fr(it.end)) }
        val barDb = bars.map { b ->
            var e = 0.0; var c = 0; var f = floor(fr(b.start)).toInt()
            while (f < floor(fr(b.end)).toInt() && f < cf.frames) { e += cf.en[f]; c++; f++ }
            10 * log10(e / max(1, c) + 1e-12)
        }
        val W = 4
        fun win(i0: Int, i1: Int): DoubleArray {
            val c = DoubleArray(12)
            for (i in max(0, i0) until min(nb, i1)) { val v = norm(barVec[i]); for (k in 0 until 12) c[k] += v[k] }
            return c
        }
        fun winDb(i0: Int, i1: Int): Double {
            var s = 0.0; var c = 0
            for (i in max(0, i0) until min(nb, i1)) { s += barDb[i]; c++ }
            return if (c > 0) s / c else 0.0
        }
        val nov = DoubleArray(nb)
        for (i in 2 until nb - 1) {
            val w = minOf(W, i, nb - i)
            nov[i] = ((1 - cosSim(win(i - w, i), win(i, i + w))) * 4 + min(1.5, abs(winDb(i - w, i) - winDb(i, i + w)) / 4)) *
                (w.toDouble() / W) * (if (i % 4 == 0) 1.15 else 1.0)
        }
        val bounds = mutableListOf(0)
        val cand = mutableListOf<Int>()
        for (i in 2 until nb - 1) {
            var isMax = true; var lo = Double.POSITIVE_INFINITY
            for (d in -4..4) {
                val j = i + d; if (j < 0 || j >= nb) continue
                if (abs(d) <= 2 && j != i && nov[j] > nov[i]) isMax = false
                lo = min(lo, nov[j])
            }
            if (isMax && nov[i] >= 0.6 && nov[i] - lo >= 0.5) cand += i
        }
        cand.sortByDescending { nov[it] }
        for (c in cand) if (bounds.all { abs(it - c) >= 4 } && nb - c >= 2) bounds += c
        bounds.sort()
        val segs = bounds.mapIndexed { i, b0 ->
            val b1 = bounds.getOrNull(i + 1) ?: nb
            Seg(b0, b1, norm(win(b0, b1)), winDb(b0, b1), barChord.subList(b0, b1))
        }
        fun seqSim(a: List<String>, b: List<String>): Double {
            val n = min(a.size, b.size); var m = 0
            for (i in 0 until n) if (a[i] == b[i]) m++
            return if (n > 0) m.toDouble() / max(a.size, b.size) else 0.0
        }
        var g = 0
        segs.forEachIndexed { i, s ->
            var found = false
            for (j in 0 until i) {
                val t = segs[j]; val cs = cosSim(s.vec, t.vec)
                if (cs > 0.97 && abs(s.db - t.db) < 4 && (seqSim(s.chords, t.chords) >= 0.5 || cs > 0.99)) { s.group = t.group; found = true; break }
            }
            if (!found) s.group = g++
        }
        val groups = segs.groupBy { it.group }
        val loudest = segs.maxOf { it.db }
        val repeated = groups.values.filter { it.size > 1 }.sortedByDescending { grp -> grp.sumOf { it.db } / grp.size }
        val labelOf = mutableMapOf<Int, String>()
        repeated.getOrNull(0)?.let { labelOf[it[0].group] = "Chorus" }
        repeated.getOrNull(1)?.let { labelOf[it[0].group] = "Verse" }
        var letter = 0
        segs.forEachIndexed { i, s ->
            val grp = groups.getValue(s.group)
            s.label = when {
                labelOf[s.group] in listOf("Chorus", "Verse") -> labelOf.getValue(s.group)
                segs.size == 1 -> "Whole track"
                i == 0 && s.db < loudest - 3 && grp.all { it === s || it === segs.last() } -> "Intro"
                i == segs.size - 1 && s.db < loudest - 3 && grp.all { it === s || it === segs.first() } -> "Outro"
                grp.size == 1 && i > 0 && i < segs.size - 1 -> if (s.db < loudest - 4) "Break" else "Bridge"
                else -> labelOf.getOrPut(s.group) { "Part " + ('A' + letter++) }
            }
            s.start = if (i == 0) 0.0 else bars[s.b0].start
            s.end = if (s.b1 >= nb) duration else bars[s.b1].start
            val lp = condenseLoop(s.chords); s.loop = lp.first; s.loopTimes = lp.second
        }
        val seen = mutableMapOf<String, Int>()
        segs.forEach { s ->
            val n = groups.getValue(s.group).size
            s.name = if (n > 1 && s.label !in listOf("Intro", "Outro")) {
                seen[s.label] = (seen[s.label] ?: 0) + 1; "${s.label} ${seen[s.label]}"
            } else s.label
        }
        val main = condenseLoop(barChord)
        val sections = segs.map { Section(it.name, it.label, it.start, it.end, it.b0 + 1, it.b1 - it.b0, it.db, it.loop, it.loopTimes) }
        return Structure(nb, chords, sections, main.first, main.second, arrangementNotes(sections, main, tempo, nb))
    }

    private fun arrangementNotes(segs: List<Section>, main: Pair<List<String>, Int>, tempo: Tempo, nb: Int): List<String> {
        fun an(n: Int) = if (n.toString()[0] == '8' || n == 11 || n == 18) "an" else "a"
        val out = mutableListOf<String>()
        out += "At ${tempo.bpm} BPM one bar of four beats lasts ${"%.1f".format(240.0 / tempo.bpm)} seconds, and the song runs about $nb bars."
        val first = segs.first()
        out += if (first.label == "Intro") "It opens with ${an(first.bars)} ${first.bars}-bar intro that is quieter than the rest, ending at ${fmt(first.endSec)}."
        else "It starts straight into the main material, with no quiet intro."
        val top = segs.maxBy { it.db }
        out += "The loudest section starts at ${fmt(top.startSec)}, in bar ${top.firstBar}."
        val chorus = segs.filter { it.label == "Chorus" }
        if (chorus.size > 1) out += "The section that sounds like a chorus comes back ${chorus.size} times, first at ${fmt(chorus[0].startSec)}."
        for (i in 1 until segs.size - 1) if (segs[i].db < segs[i - 1].db - 4 && segs[i].label !in listOf("Verse", "Chorus")) {
            out += "The energy drops at ${fmt(segs[i].startSec)} for ${segs[i].bars} bars before the music builds again."; break
        }
        if (main.second >= 2) out += "The most repeated chord pattern is ${main.first.joinToString(", ")}, heard ${main.second} times."
        val last = segs.last()
        out += if (last.label == "Outro") "It winds down with a quieter ${last.bars}-bar ending." else "It ends without a quieter outro."
        out += if (segs.size > 1) "Altogether the song has ${segs.size} sections." else "No clear section changes were found, so the whole track keeps a similar sound and level."
        return out
    }

    // ---------- Entry point ----------
    fun analyze(x: FloatArray, curvePoints: Int = 72, progress: (Float) -> Unit = {}): Result {
        val duration = x.size.toDouble() / SR
        val on = onsetEnvelope(x, progress)
        val tempo = estimateTempo(on.o, on.fps)
        val key = estimateKey(x, progress)
        progress(0.9f)
        val energy = energyLevels(x)
        val cf = chromaFrames(x, progress)
        val structure = try { analyzeStructure(cf, on.oLow, on.fps, tempo, duration) } catch (e: Exception) { null }
        progress(1f)
        return Result(duration, tempo, key, energy.avgDb, energy.peakDb, energyCurve(x, curvePoints), structure)
    }

    // ---------- Test song with a known structure: 124 BPM, A minor ----------
    fun synthTestSong(): FloatArray {
        val bpm = 124.0; val beat = 60 / bpm; val bar = 4 * beat
        data class P(val n: Int, val prog: List<String>, val gain: Double, val drums: Boolean, val hats: Boolean)
        val plan = listOf(
            P(4, listOf("Am", "Dm", "E", "Am"), 0.3, false, false), P(8, listOf("Am", "Dm", "E", "Am"), 0.6, true, false),
            P(8, listOf("F", "G", "C", "E"), 1.0, true, true), P(8, listOf("Am", "Dm", "E", "Am"), 0.6, true, false),
            P(8, listOf("F", "G", "C", "E"), 1.0, true, true), P(4, listOf("Dm", "Dm", "E", "E"), 0.35, false, false),
            P(8, listOf("F", "G", "C", "E"), 1.0, true, true), P(4, listOf("Am", "Dm", "E", "Am"), 0.25, false, false)
        )
        val triad = mapOf("Am" to intArrayOf(57, 60, 64), "Dm" to intArrayOf(62, 65, 69), "E" to intArrayOf(64, 68, 71),
            "F" to intArrayOf(65, 69, 72), "G" to intArrayOf(67, 71, 74), "C" to intArrayOf(60, 64, 67))
        val root = mapOf("Am" to 45, "Dm" to 50, "E" to 40, "F" to 41, "G" to 43, "C" to 48)
        val totalBars = plan.sumOf { it.n }; val len = kotlin.math.ceil(totalBars * bar * SR).toInt()
        val y = FloatArray(len)
        fun hz(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)
        var seed = 7L; fun rnd(): Double { seed = (seed * 16807) % 2147483647; return seed / 2147483647.0 * 2 - 1 }
        var b = 0
        for (p in plan) for (j in 0 until p.n) {
            val ch = p.prog[j % 4]; val b0 = floor(b * bar * SR).toInt(); val b1 = floor((b + 1) * bar * SR).toInt()
            for (i in b0 until min(b1, len)) {
                val t = (i - b0).toDouble() / SR; val env = min(1.0, t * 20) * min(1.0, (b1 - i).toDouble() / SR * 20)
                var s = 0.0
                for (m in triad.getValue(ch)) { val f = hz(m); s += 0.6 * sin(2 * PI * f * t) + 0.25 * sin(4 * PI * f * t); if (p.hats) s += 0.25 * sin(PI * f * t * 4.0 * 1.0005) }
                var v = p.gain * 0.06 * s * env
                if (p.drums) { val fb = hz(root.getValue(ch)); v += p.gain * 0.18 * (sin(2 * PI * fb * t) + 0.3 * sin(4 * PI * fb * t)) * env }
                y[i] += v.toFloat()
            }
            if (p.drums) for (q in 0 until 4) {
                val k0 = floor((b * 4 + q) * beat * SR).toInt()
                for (i in 0 until (SR * 0.35).toInt()) { if (k0 + i >= len) break; val t = i.toDouble() / SR; y[k0 + i] += (p.gain * 0.55 * sin(2 * PI * (50 + 100 * exp(-t * 25)) * t) * exp(-t * 9)).toFloat() }
                if (p.hats) { val h0 = floor((b * 4 + q + 0.5) * beat * SR).toInt(); for (i in 0 until (SR * 0.05).toInt()) { if (h0 + i >= len) break; y[h0 + i] += (0.12 * rnd() * exp(-i.toDouble() / SR * 70)).toFloat() } }
            }
            b++
        }
        var mx = 0f; for (v in y) mx = max(mx, abs(v))
        if (mx > 0) for (i in y.indices) y[i] *= 0.89f / mx
        return y
    }
}
