package com.clearqr

internal data class ImageBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int
        get() = right - left
    val height: Int
        get() = bottom - top
}

internal data class RecognizedTextLine(val text: String, val bounds: ImageBounds)

internal object NearbyNumberExtractor {
    private val numberPattern =
        Regex("(?<![\\p{L}\\p{N}])\\+?\\d(?:[\\d\\s()./-]*\\d)?(?![\\p{L}\\p{N}])")

    fun findNearbyLines(barcode: ImageBounds, lines: List<RecognizedTextLine>): List<String> {
        val maxGap = maxOf(barcode.width, barcode.height) * 0.75
        return lines.asSequence()
            .filter { isNearby(barcode, it.bounds, maxGap) }
            .filter { line -> numberPattern.containsMatchIn(line.text) }
            .map { it.text.trim() }
            .distinct()
            .toList()
    }

    fun extractNumbers(lines: List<String>): List<String> =
        lines.asSequence()
            .flatMap { line -> numberPattern.findAll(line).map { it.value.trim() } }
            .filter(String::isNotEmpty)
            .distinct()
            .toList()

    private fun isNearby(barcode: ImageBounds, text: ImageBounds, maxGap: Double): Boolean {
        if (barcode.width <= 0 || barcode.height <= 0 || text.width <= 0 || text.height <= 0) return false

        val overlaps = barcode.left < text.right && barcode.right > text.left &&
            barcode.top < text.bottom && barcode.bottom > text.top
        if (overlaps) return false

        val horizontalGap = maxOf(barcode.left - text.right, text.left - barcode.right, 0)
        val verticalGap = maxOf(barcode.top - text.bottom, text.top - barcode.bottom, 0)
        return kotlin.math.hypot(horizontalGap.toDouble(), verticalGap.toDouble()) <= maxGap
    }
}
