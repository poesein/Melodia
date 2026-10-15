package com.lin0721.linmusic.core.source

import kotlin.math.abs
import kotlin.math.sqrt

/** Evidence from two aligned audio excerpts, not a claim about the remainder of a recording. */
internal data class PcmExcerptMatch(
    val comparedDurationMs: Long,
    val normalizedCorrelation: Double,
    val candidateOffsetSamples: Int
)

/** Pure PCM comparison. Input is mono, box-filtered to 8 kHz by the decoder. */
internal object PcmIdentityMatcher {
    const val SAMPLE_RATE = 8_000
    private const val SEGMENT_SAMPLES = SAMPLE_RATE * 4
    private const val MIN_AUDIBLE_SAMPLES = SAMPLE_RATE * 3
    private const val MAX_ALIGNMENT_SAMPLES = SAMPLE_RATE / 2
    private const val MIN_CORRELATION = 0.94
    private const val COARSE_FACTOR = 8

    fun match(reference: FloatArray, candidate: FloatArray, expectedOffsetSamples: Int): PcmExcerptMatch? {
        if (reference.size < SEGMENT_SAMPLES * 2 || candidate.size < SEGMENT_SAMPLES * 2) return null
        if (expectedOffsetSamples.toLong() !in -MAX_ALIGNMENT_SAMPLES.toLong()..candidate.size.toLong()) return null
        val starts = mutableListOf<Int>()
        var cursor = 0
        while (cursor + SEGMENT_SAMPLES <= reference.size && starts.size < 2) {
            if (audibleSamples(reference, cursor, SEGMENT_SAMPLES) >= MIN_AUDIBLE_SAMPLES) {
                starts += cursor
                cursor += SEGMENT_SAMPLES
            } else cursor += SAMPLE_RATE / 2
        }
        if (starts.size != 2) return null

        val lowReference = lowPass(reference)
        val lowCandidate = lowPass(candidate)
        val coarseStart = starts.first() / COARSE_FACTOR
        val coarseLength = SEGMENT_SAMPLES / COARSE_FACTOR
        val minOffset = expectedOffsetSamples - MAX_ALIGNMENT_SAMPLES
        val maxOffset = expectedOffsetSamples + MAX_ALIGNMENT_SAMPLES
        val coarseCandidates = mutableListOf<Pair<Int, Double>>()
        for (offset in minOffset / COARSE_FACTOR..maxOffset / COARSE_FACTOR) {
            val score = correlation(lowReference, coarseStart, lowCandidate, coarseStart + offset, coarseLength)
            if (score != null && abs(score) >= 0.45) coarseCandidates += offset * COARSE_FACTOR to abs(score)
        }
        if (coarseCandidates.isEmpty()) return null
        // A periodic lead can create a stronger coarse phase alias than the true sub-millisecond lag.
        // Retain several peaks and require both independent full-resolution excerpts to agree.
        val fineOffsets = coarseCandidates.sortedByDescending { it.second }.take(24)
            .flatMap { (offset, _) ->
                (maxOf(minOffset, offset - COARSE_FACTOR)..minOf(maxOffset, offset + COARSE_FACTOR)).toList()
            }.toSet()

        var best: PcmExcerptMatch? = null
        for (offset in fineOffsets) {
            var polarity = 0
            var minimumCorrelation = 1.0
            var audible = 0
            var accepted = true
            for (start in starts) {
                val aligned = start + offset
                val score = correlation(reference, start, candidate, aligned, SEGMENT_SAMPLES)
                if (score == null || abs(score) < MIN_CORRELATION) { accepted = false; break }
                val sign = if (score < 0) -1 else 1
                if (polarity != 0 && sign != polarity) { accepted = false; break }
                polarity = sign
                val count = minOf(audibleSamples(reference, start, SEGMENT_SAMPLES),
                    audibleSamples(candidate, aligned, SEGMENT_SAMPLES))
                if (count < MIN_AUDIBLE_SAMPLES) { accepted = false; break }
                audible += count
                minimumCorrelation = minOf(minimumCorrelation, abs(score))
            }
            if (accepted && minimumCorrelation > (best?.normalizedCorrelation ?: 0.0)) {
                best = PcmExcerptMatch(audible * 1000L / SAMPLE_RATE, minimumCorrelation, offset)
            }
        }
        return best
    }

    private fun lowPass(input: FloatArray): FloatArray = FloatArray(input.size / COARSE_FACTOR) { index ->
        var sum = 0.0
        for (i in 0 until COARSE_FACTOR) sum += input[index * COARSE_FACTOR + i]
        (sum / COARSE_FACTOR).toFloat()
    }

    private fun audibleSamples(input: FloatArray, start: Int, length: Int): Int {
        if (start < 0 || start.toLong() + length > input.size) return 0
        val blockSize = SAMPLE_RATE / 20 // 50 ms; require at least 3 audible seconds in each excerpt.
        var audible = 0
        for (block in start until start + length step blockSize) {
            val count = minOf(blockSize, start + length - block)
            var sum = 0.0
            var squares = 0.0
            for (i in block until block + count) { val x = input[i].toDouble(); sum += x; squares += x * x }
            if (squares / count - (sum / count) * (sum / count) > 0.00001) audible += count
        }
        return audible
    }

    private fun correlation(a: FloatArray, aStart: Int, b: FloatArray, bStart: Int, length: Int): Double? {
        if (aStart < 0 || bStart < 0 || aStart.toLong() + length > a.size || bStart.toLong() + length > b.size) return null
        var aSum = 0.0; var bSum = 0.0; var aa = 0.0; var bb = 0.0; var ab = 0.0
        for (i in 0 until length) {
            val x = a[aStart + i].toDouble(); val y = b[bStart + i].toDouble()
            if (!x.isFinite() || !y.isFinite()) return null
            aSum += x; bSum += y; aa += x * x; bb += y * y; ab += x * y
        }
        val aEnergy = aa - aSum * aSum / length
        val bEnergy = bb - bSum * bSum / length
        if (aEnergy <= 1e-12 || bEnergy <= 1e-12) return null
        return ((ab - aSum * bSum / length) / sqrt(aEnergy * bEnergy)).coerceIn(-1.0, 1.0)
    }
}
