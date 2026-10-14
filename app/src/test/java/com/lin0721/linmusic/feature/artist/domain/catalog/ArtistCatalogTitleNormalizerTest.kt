package com.lin0721.linmusic.feature.artist.domain.catalog

import org.junit.Assert.*
import org.junit.Test

class ArtistCatalogTitleNormalizerTest {
    private val normalizer = ArtistCatalogTitleNormalizer()

    @Test
    fun bundledConverterLoadsTheCompleteOfficialCharacterDictionary() {
        assertEquals(4148, OpenCcCharacterConverter.bundled.mappingCount)
        assertEquals("陈奕迅认了吧音乐台里", OpenCcCharacterConverter.bundled.convert("陳奕迅認了吧音樂臺裏"))
        assertEquals("干", OpenCcCharacterConverter.bundled.convert("乾")) // First upstream target, not an invented mapping.
        assertNotNull(javaClass.getResourceAsStream("/catalog/opencc/LICENSE"))
    }

    @Test
    fun caseWidthPunctuationAndChineseScriptsFoldConsistently() {
        assertEquals(normalizer.normalizeTitle("Ｌ．Ｏ．Ｖ．Ｅ．"), normalizer.normalizeTitle("L.O.V.E."))
        assertEquals(normalizer.normalizeTitle("認了吧"), normalizer.normalizeTitle("认了吧"))
    }

    @Test
    fun editionOnlyDescriptorsAreRemovedAndNestedTrailingDescriptorsAreSupported() {
        listOf("A (2018 Remastered)", "A [Deluxe Edition]", "A (US Version)", "A（豪華重製版）", "A (Japan Edition) (Deluxe Edition)", "A 10th Anniversary Edition").forEach {
            assertEquals(it, "a", normalizer.baseTitle(it))
        }
    }

    @Test
    fun meaningfulOriginalSuffixesAndRecordingVersionsArePreserved() {
        listOf("A (Part II)", "A (Live)", "A (Remix)", "A (Acoustic)", "A (Live Remastered)", "Live for Today").forEach {
            assertEquals(it, normalizer.normalizeTitle(it), normalizer.baseTitle(it))
        }
    }

    @Test
    fun converterCanBeInjectedAndSupplementaryUnicodeLettersArePreserved() {
        val identity = ArtistCatalogTitleNormalizer { it }
        assertEquals("𠮷野家", identity.normalizeTitle("𠮷野家"))
        assertNotEquals(identity.normalizeTitle("認了吧"), identity.normalizeTitle("认了吧"))
    }
}
