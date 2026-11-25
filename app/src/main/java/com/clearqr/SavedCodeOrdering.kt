package com.clearqr

import java.util.Locale

internal fun compareSavedCodeNames(first: String, second: String): Int {
    val left = first.lowercase(Locale.ROOT)
    val right = second.lowercase(Locale.ROOT)
    var leftIndex = 0
    var rightIndex = 0

    while (leftIndex < left.length && rightIndex < right.length) {
        val leftIsDigit = left[leftIndex] in '0'..'9'
        val rightIsDigit = right[rightIndex] in '0'..'9'
        if (leftIsDigit && rightIsDigit) {
            var leftEnd = leftIndex
            var rightEnd = rightIndex
            while (leftEnd < left.length && left[leftEnd] in '0'..'9') leftEnd++
            while (rightEnd < right.length && right[rightEnd] in '0'..'9') rightEnd++

            var leftSignificant = leftIndex
            var rightSignificant = rightIndex
            while (leftSignificant < leftEnd - 1 && left[leftSignificant] == '0') leftSignificant++
            while (rightSignificant < rightEnd - 1 && right[rightSignificant] == '0') rightSignificant++

            val leftDigits = leftEnd - leftSignificant
            val rightDigits = rightEnd - rightSignificant
            if (leftDigits != rightDigits) return leftDigits.compareTo(rightDigits)
            for (offset in 0 until leftDigits) {
                val digitComparison = left[leftSignificant + offset].compareTo(right[rightSignificant + offset])
                if (digitComparison != 0) return digitComparison
            }
            val runLengthComparison = (leftEnd - leftIndex).compareTo(rightEnd - rightIndex)
            if (runLengthComparison != 0) return runLengthComparison
            leftIndex = leftEnd
            rightIndex = rightEnd
        } else {
            val characterComparison = left[leftIndex].compareTo(right[rightIndex])
            if (characterComparison != 0) return characterComparison
            leftIndex++
            rightIndex++
        }
    }

    return (left.length - leftIndex).compareTo(right.length - rightIndex)
}

internal fun savedCodeMatchesQuery(name: String, value: String, query: String): Boolean {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return normalizedQuery.isEmpty() ||
        name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
        value.lowercase(Locale.ROOT).contains(normalizedQuery)
}
