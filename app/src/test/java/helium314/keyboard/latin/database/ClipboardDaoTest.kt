// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardDaoTest {
    private lateinit var dao: ClipboardDao

    @Before
    fun setUp() {
        ClipboardDao.closeInstance()
        Database.closeInstance()
        dao = ClipboardDao.getInstance(ApplicationProvider.getApplicationContext())!!
        dao.clear()
    }

    @After
    fun tearDown() {
        ClipboardDao.closeInstance()
        Database.closeInstance()
    }

    @Test
    fun addClip_keepsSequentialImageClipsWithSamePlaceholderText() {
        dao.addClip(1L, pinned = false, text = "[Image]", imageUri = "/cache/clipboard_images/img_1.jpg")
        dao.addClip(2L, pinned = false, text = "[Image]", imageUri = "/cache/clipboard_images/img_2.jpg")
        dao.addClip(3L, pinned = false, text = "[Image]", imageUri = "/cache/clipboard_images/img_3.jpg")

        val clips = dao.getClips()
        assertEquals(3, clips.size)
        assertTrue(clips.any { it.imageUri == "/cache/clipboard_images/img_1.jpg" })
        assertTrue(clips.any { it.imageUri == "/cache/clipboard_images/img_2.jpg" })
        assertTrue(clips.any { it.imageUri == "/cache/clipboard_images/img_3.jpg" })
    }

    @Test
    fun addClip_deduplicatesSameImageUri() {
        dao.addClip(1L, pinned = false, text = "[Image]", imageUri = "/cache/clipboard_images/img_same.jpg")
        dao.addClip(2L, pinned = false, text = "[Screenshot]", imageUri = "/cache/clipboard_images/img_same.jpg")

        assertEquals(1, dao.count())
        assertEquals(2L, dao.getClips().first().timeStamp)
    }

    @Test
    fun addClip_textDeduplicationStillWorks() {
        dao.addClip(1L, pinned = false, text = "hello")
        dao.addClip(2L, pinned = false, text = "hello")

        assertEquals(1, dao.count())
        assertEquals(2L, dao.getClips().first().timeStamp)
    }
}
