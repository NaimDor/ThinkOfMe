package com.thinkofme.feature.wishes

import com.thinkofme.core.model.ALL_CIRCLES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditWishLogicTest {
    @Test
    fun `all circles and specific circles are mutually exclusive`() {
        var ids = listOf(ALL_CIRCLES)
        ids = EditWishViewModel.toggleVisibility(ids, "family")
        assertEquals(listOf("family"), ids)
        ids = EditWishViewModel.toggleVisibility(ids, ALL_CIRCLES)
        assertEquals(listOf(ALL_CIRCLES), ids)
        assertEquals(emptyList<String>(), EditWishViewModel.toggleVisibility(ids, ALL_CIRCLES))
    }

    @Test
    fun `extracts a product link from shared text`() {
        assertEquals("https://shop.example/p/1", EditWishViewModel.extractUrl("Look at this! https://shop.example/p/1."))
        assertNull(EditWishViewModel.extractUrl("no link here"))
    }

    @Test
    fun `formats amounts without needless decimals`() {
        assertEquals("349", EditWishViewModel.formatAmount(349.0))
        assertEquals("12.50", EditWishViewModel.formatAmount(12.5))
    }
}
