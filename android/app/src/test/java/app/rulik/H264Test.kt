package app.rulik

import app.rulik.net.H264
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class H264Test {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val start = byteArrayOf(0, 0, 0, 1)

    // SPS, которые выдал NVENC при стриме 1280×720 и 1920×1080 (у 1080p есть обрезка до 1088)
    private val sps720 = hex("67640020ac2b200a00b7602d4043409000003e80001d4c0e0000030098968000a299bbca23e38549")
    private val sps1080 = hex("6764002aac2b200f0044fcb80b5010d05400000fa0000753038000002625a00028a66ef288f8e15240")

    @Test fun sizeFromSps() {
        assertEquals(1280 to 720, H264.size(start + sps720))
        assertEquals(1920 to 1080, H264.size(start + sps1080))
    }

    @Test fun parameterSetsFromKeyframe() {
        val pps = hex("68ee3cb0")
        val idr = hex("6588840021")
        val (s, p) = H264.parameterSets(start + sps720 + start + pps + byteArrayOf(0, 0, 1) + idr)
        assertArrayEquals(start + sps720, s)
        assertArrayEquals(start + pps, p)
    }

    @Test fun garbageGivesNull() {
        assertEquals(null, H264.size(byteArrayOf(0x67, 1)))
    }
}
