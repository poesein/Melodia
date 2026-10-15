package com.lin0721.linmusic.core.source

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmIdentityMatcherTest {
    private fun recording(seconds: Int = 12, variant: Int = 0): FloatArray =
        FloatArray(seconds * PcmIdentityMatcher.SAMPLE_RATE) { index ->
            val t = index.toDouble() / PcmIdentityMatcher.SAMPLE_RATE
            val envelope = 0.35 + 0.15 * sin(2 * PI * 0.37 * t)
            val lead = sin(2 * PI * ((177 + variant * 83) * t + 2.7 * t * t))
            val backing = 0.3 * sin(2 * PI * ((431 + variant * 29) * t + 0.8 * t * t))
            (envelope * (lead + backing)).toFloat()
        }

    @Test fun `same recording survives codec-like quantization and gain`() {
        val reference = recording()
        val candidate = FloatArray(reference.size) { i ->
            ((reference[i] * 0.3 * 4096).toInt() / 4096.0).toFloat()
        }
        val match = PcmIdentityMatcher.match(reference, candidate, 0)
        assertNotNull(match)
        assertTrue(match!!.comparedDurationMs >= 6000)
        assertTrue(match.normalizedCorrelation >= 0.94)
    }

    @Test fun `known excerpt offset and global inverse phase are accepted`() {
        val reference = recording()
        val prefix = PcmIdentityMatcher.SAMPLE_RATE / 3
        val candidate = FloatArray(reference.size + prefix) { i ->
            if (i < prefix) 0f else -reference[i - prefix] * 0.6f
        }
        val match = PcmIdentityMatcher.match(reference, candidate, prefix)
        assertNotNull(match)
        assertTrue(kotlin.math.abs(match!!.candidateOffsetSamples - prefix) <= 1)
    }

    @Test fun `encoder alignment error within half a second is found`() {
        val reference = recording()
        val delay = 1731
        val candidate = FloatArray(reference.size + delay) { i -> if (i < delay) 0f else reference[i - delay] }
        assertNotNull(PcmIdentityMatcher.match(reference, candidate, 0))
    }

    @Test fun `other recording of equal length is rejected`() {
        assertNull(PcmIdentityMatcher.match(recording(), recording(variant = 1), 0))
    }

    @Test fun `only one matching excerpt cannot approve a stream`() {
        val reference = recording()
        val different = recording(variant = 1)
        val candidate = reference.copyOf()
        for (i in PcmIdentityMatcher.SAMPLE_RATE * 4 until candidate.size) candidate[i] = different[i]
        assertNull(PcmIdentityMatcher.match(reference, candidate, 0))
    }

    @Test fun `silence and DC do not provide audio identity`() {
        assertNull(PcmIdentityMatcher.match(FloatArray(96_000), FloatArray(96_000), 0))
        assertNull(PcmIdentityMatcher.match(FloatArray(96_000) { 0.1f }, FloatArray(96_000) { 0.1f }, 0))
    }

    @Test fun `too short and mostly silent input are rejected`() {
        val short = recording(seconds = 5)
        assertNull(PcmIdentityMatcher.match(short, short, 0))
        val sparse = recording()
        for (i in sparse.indices) if (i % 8000 > 1000) sparse[i] = 0f
        assertNull(PcmIdentityMatcher.match(sparse, sparse, 0))
    }

    @Test fun `unknown nonfinite PCM cannot pass`() {
        val broken = recording().also { samples -> for (i in samples.indices) samples[i] = Float.NaN }
        assertNull(PcmIdentityMatcher.match(recording(), broken, 0))
    }
}
