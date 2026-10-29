package org.mediasare.android

import org.junit.Assert.*
import org.junit.Test

class SharedUrlTest {
    @Test fun acceptsCaptionAndTrackingQuery() {
        assertEquals("https://youtu.be/abc?si=123", SharedUrl.extract("看這支影片！\nhttps://youtu.be/abc?si=123"))
    }
    @Test fun trimsChinesePunctuation() {
        assertEquals("https://www.instagram.com/reel/abc/", SharedUrl.extract("（https://www.instagram.com/reel/abc/）。"))
    }
    @Test fun rejectsUnsupportedOrMalformedInput() {
        listOf("", "只有文字", "file:///sdcard/a.mp4", "javascript:alert(1)", "https://", "http://example.com/a", "https://user:pass@example.com/video").forEach {
            assertNull(it, SharedUrl.extract(it))
        }
    }
    @Test fun preservesSignedUrls() {
        val value = "https://example.com/video?a=1&signature=a%2Fb%3D"
        assertEquals(value, SharedUrl.extract(value))
    }
}
