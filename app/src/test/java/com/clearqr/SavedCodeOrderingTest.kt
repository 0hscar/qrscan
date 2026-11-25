package com.clearqr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedCodeOrderingTest {
    @Test
    fun comparesDigitRunsByNumericValueWithoutIntegerOverflow() {
        val names = listOf("57", "3", "23", "2", "100000000000000000000", "35")

        assertEquals(
            listOf("2", "3", "23", "35", "57", "100000000000000000000"),
            names.sortedWith(::compareSavedCodeNames)
        )
    }

    @Test
    fun comparesMultipleDigitRunsAndIgnoresNameCase() {
        val names = listOf("Item 12", "item 2", "ITEM 2a", "Item 2B")

        assertEquals(
            listOf("item 2", "ITEM 2a", "Item 2B", "Item 12"),
            names.sortedWith(::compareSavedCodeNames)
        )
    }

    @Test
    fun searchMatchesNameAndValueCaseInsensitively() {
        assertTrue(savedCodeMatchesQuery("My Code", "https://example.com", "my"))
        assertTrue(savedCodeMatchesQuery("My Code", "https://example.com", "EXAMPLE"))
        assertTrue(savedCodeMatchesQuery("My Code", "https://example.com", "  "))
    }
}
