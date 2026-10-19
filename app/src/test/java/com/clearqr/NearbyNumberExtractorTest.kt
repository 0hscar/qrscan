package com.clearqr

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyNumberExtractorTest {
    private val barcode = ImageBounds(100, 100, 300, 300)

    @Test
    fun extractsNearbyNumbersButIgnoresOverlappingAndDistantText() {
        val elements = listOf(
            RecognizedTextLine("Call 555-0123", ImageBounds(310, 150, 430, 180)),
            RecognizedTextLine("Order 42", ImageBounds(160, 150, 210, 175)),
            RecognizedTextLine("Far away 999", ImageBounds(700, 100, 780, 130)),
            RecognizedTextLine("QR text 123", ImageBounds(120, 120, 180, 145))
        )

        val nearbyLines = NearbyNumberExtractor.findNearbyLines(barcode, elements)

        assertEquals(listOf("Call 555-0123"), nearbyLines)
        assertEquals(listOf("555-0123"), NearbyNumberExtractor.extractNumbers(nearbyLines))
    }

    @Test
    fun extractsStandaloneNumericRunsAndKeepsFormattedNumbers() {
        val elements = listOf(
            RecognizedTextLine("Location '1 800 555 0199' Name '42'", ImageBounds(310, 150, 500, 180)),
            RecognizedTextLine("A12B", ImageBounds(310, 200, 360, 230)),
            RecognizedTextLine("1 800 555 0199", ImageBounds(310, 250, 500, 280))
        )

        val nearbyLines = NearbyNumberExtractor.findNearbyLines(barcode, elements)

        assertEquals(
            listOf("Location '1 800 555 0199' Name '42'", "1 800 555 0199"),
            nearbyLines
        )
        assertEquals(
            listOf("1 800 555 0199", "42"),
            NearbyNumberExtractor.extractNumbers(nearbyLines)
        )
    }

    @Test
    fun rejectsInvalidBounds() {
        val invalidBarcode = ImageBounds(10, 10, 10, 20)
        val line = RecognizedTextLine("123", ImageBounds(11, 10, 30, 20))

        assertEquals(emptyList<String>(), NearbyNumberExtractor.findNearbyLines(invalidBarcode, listOf(line)))
    }
}
