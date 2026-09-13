// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.text.InputType
import helium314.keyboard.compat.AppWorkarounds
import helium314.keyboard.latin.utils.InputTypeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContenteditableDuplicationFixTest {

    @Test
    fun testIsMalformedWord_detectsDuplicationArtifacts() {
        // Words from the bug report
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord("ChChChecChChChecCheck"))
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord("ChChChecChChChecCheckC"))
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord("ChChChec"))
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord("ChChCheck"))
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord("ThThThis"))
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord("StStStop"))

        // Excessive repetitions
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord("abcabcabcabc"))
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord("aaaaa"))

        // Excessive length
        val longString = "a".repeat(49)
        assertTrue(DictionaryFacilitatorImpl.isMalformedWord(longString))
    }

    @Test
    fun testIsMalformedWord_allowsLegitimateWords() {
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("Check"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("Hello"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("Christmas"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("Children"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("School"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("Through"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("chacha"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("cancan"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("murmur"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("hahaha"))
        assertFalse(DictionaryFacilitatorImpl.isMalformedWord("couscous"))
    }

    @Test
    fun testAppWorkarounds_webBrowserDetection() {
        assertTrue(AppWorkarounds.isWebBrowser("com.brave.browser"))
        assertTrue(AppWorkarounds.isWebBrowser("com.android.chrome"))
        assertTrue(AppWorkarounds.isWebBrowser("org.mozilla.firefox"))
        assertFalse(AppWorkarounds.isWebBrowser("com.discord"))
        assertFalse(AppWorkarounds.isWebBrowser("org.telegram.messenger"))
        assertFalse(AppWorkarounds.isWebBrowser(null))
    }

    @Test
    fun testAppWorkarounds_adjustInputTypeWithAutoCorrect() {
        val baseType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT

        val adjusted = AppWorkarounds.adjustInputType(baseType, "com.brave.browser")
        // With AUTO_CORRECT present, it should add WEB_EDIT_TEXT and NOT add NO_SUGGESTIONS
        val hasWebEditText = (adjusted and InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) != 0
        val hasNoSuggestions = (adjusted and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

        assertTrue("Should add WEB_EDIT_TEXT", hasWebEditText)
        assertFalse("Should NOT add NO_SUGGESTIONS when AUTO_CORRECT is present", hasNoSuggestions)
    }

    @Test
    fun testInputTypeUtils_isWebEditText() {
        val webText = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
        val webEmail = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        val webPassword = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        val normalText = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL

        assertTrue(InputTypeUtils.isWebEditText(webText))
        assertTrue(InputTypeUtils.isWebEditText(webEmail))
        assertTrue(InputTypeUtils.isWebEditText(webPassword))
        assertFalse(InputTypeUtils.isWebEditText(normalText))
        assertFalse(InputTypeUtils.isWebEditText(InputType.TYPE_CLASS_NUMBER))
    }

    @Test
    fun testIsBelatedExpectedUpdate_handlesMissingComposingSpan() {
        val mockIms = org.mockito.Mockito.mock(android.inputmethodservice.InputMethodService::class.java)
        val ric = RichInputConnection(mockIms)

        val expectedStartField = RichInputConnection::class.java.getDeclaredField("mExpectedSelStart").apply { isAccessible = true }
        val expectedEndField = RichInputConnection::class.java.getDeclaredField("mExpectedSelEnd").apply { isAccessible = true }
        val composingField = RichInputConnection::class.java.getDeclaredField("mComposingText").apply { isAccessible = true }
        expectedStartField.setInt(ric, 5)
        expectedEndField.setInt(ric, 5)
        (composingField.get(ric) as StringBuilder).append("Check")

        // In contenteditable, cs = -1, ce = -1. When newSel matches expectedSel, it must return true
        assertTrue(ric.isBelatedExpectedUpdate(0, 5, 0, 5, -1, -1))

        // When cs and ce are valid (e.g. 0 to 5), it returns true
        assertTrue(ric.isBelatedExpectedUpdate(0, 5, 0, 5, 0, 5))

        // If editor truncated the composing span (e.g. cs = 0, ce = 2 < 5), it returns false
        assertFalse(ric.isBelatedExpectedUpdate(0, 5, 0, 5, 0, 2))

        // If cursor moved somewhere unexpected (e.g. newSel = 10 != 5), it returns false
        assertFalse(ric.isBelatedExpectedUpdate(0, 10, 0, 10, -1, -1))
    }
}
