package com.clearqr

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyNumberExtractorTest {
    private val barcode = ImageBounds(100, 100, 300, 300)

    @Test
    fun extractsNearbyNumbersButIgnoresOverlappingAndDistantText() {
        val elements = listOf(
            RecognizedTextElement("Call 555-0123", ImageBounds(310, 150, 430, 180)),
            RecognizedTextElement("Order 42", ImageBounds(160, 150, 210, 175)),
            RecognizedTextElement("Far away 999", ImageBounds(700, 100, 780, 130)),
            RecognizedTextElement("QR text 123", ImageBounds(120, 120, 180, 145))
        )

        assertEquals(listOf("555-0123"), NearbyNumberExtractor.extract(barcode, elements))
    }

    @Test
    fun extractsStandaloneNumericRunsAndKeepsFormattedNumbers() {
        val elements = listOf(
            RecognizedTextElement("Call 1 800 555 0199 today", ImageBounds(310, 150, 500, 180)),
            RecognizedTextElement("A12B", ImageBounds(310, 200, 360, 230)),
            RecognizedTextElement("1 800 555 0199", ImageBounds(310, 250, 500, 280))
        )

        assertEquals(
            listOf("1 800 555 0199"),
            NearbyNumberExtractor.extract(barcode, elements)
        )
    }

    @Test
    fun rejectsInvalidBounds() {
        val invalidBarcode = ImageBounds(10, 10, 10, 20)
        val element = RecognizedTextElement("123", ImageBounds(11, 10, 30, 20))

        assertEquals(emptyList<String>(), NearbyNumberExtractor.extract(invalidBarcode, listOf(element)))
    }
}
