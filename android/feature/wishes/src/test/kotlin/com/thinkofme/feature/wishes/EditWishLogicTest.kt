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

    @Test
    fun `photo details fill only empty fields`() {
        val draft = com.thinkofme.core.model.WishDraft(title = "My own title", currency = "USD")
        val details = com.thinkofme.core.data.PhotoDetails(
            isProduct = true, title = "Sony WH-1000XM6", store = "KSP", priceAmount = 1299.0, priceCurrency = "ILS",
            note = "Black", searchQuery = "sony wh-1000xm6",
        )
        val merged = EditWishViewModel.mergePhotoDetails(draft, details)
        assertEquals("My own title", merged.title)
        assertEquals("1299", merged.priceText)
        assertEquals("ILS", merged.currency)
        assertEquals("KSP", merged.store)
        assertEquals("Black", merged.note)
        assertEquals("", merged.url)
    }

    @Test
    fun `brand and model stand in for a missing title`() {
        val details = com.thinkofme.core.data.PhotoDetails(isProduct = true, brand = "Hario", model = "V60")
        assertEquals("Hario V60", EditWishViewModel.mergePhotoDetails(com.thinkofme.core.model.WishDraft(), details).title)
        assertEquals(false, EditWishViewModel.needsDetails(com.thinkofme.core.model.WishDraft(title = "a", priceText = "1", store = "b")))
    }

    @Test
    fun `clearing takes back only what the photo filled and the user kept`() {
        val before = com.thinkofme.core.model.WishDraft(title = "Mine", currency = "USD")
        val details = com.thinkofme.core.data.PhotoDetails(
            isProduct = true, title = "Ignored", store = "KSP", priceAmount = 1299.0, priceCurrency = "ILS", note = "Black",
        )
        val merged = EditWishViewModel.mergePhotoDetails(before, details)
        val fill = EditWishViewModel.photoFillBetween(before, merged)!!
        assertNull(fill.title)
        // The user then edits the note by hand.
        val edited = merged.copy(note = "Black, size M")
        val cleared = EditWishViewModel.clearPhotoFill(edited, fill)
        assertEquals("Mine", cleared.title)
        assertEquals("", cleared.priceText)
        assertEquals("USD", cleared.currency)
        assertEquals("", cleared.store)
        assertEquals("Black, size M", cleared.note)
    }

    @Test
    fun `nothing filled means nothing to clear`() {
        val draft = com.thinkofme.core.model.WishDraft(title = "a", priceText = "1", store = "b", note = "c")
        assertNull(EditWishViewModel.photoFillBetween(draft, draft))
    }
}
