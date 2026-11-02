package app.rulik

import app.rulik.net.Proto
import org.junit.Assert.assertEquals
import org.junit.Test

/** Байты сверены с серверной стороной (Python struct), чтобы протокол не разъехался. */
class ProtoTest {
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test fun pad() = assertEquals("524b01020110ff00ff7f018000006400", Proto.pad(0x1001, 255, 0, 32767, -32767, 0, 100).hex())

    @Test fun key() = assertEquals("524b0106410001", Proto.key(0x41, true).hex())

    @Test fun held() = assertEquals("524b010b010257001000", Proto.held(1, listOf(0x57, 0x10)).hex())

    @Test fun hello() = assertEquals("524b010104313233340550686f6e65", Proto.hello("1234", "Phone").hex())

    @Test fun parseWelcome() {
        val data = byteArrayOf('R'.code.toByte(), 'K'.code.toByte(), 1, 0x81.toByte(), 0, 1, 2, 'P'.code.toByte(), 'C'.code.toByte())
        val (type, body) = Proto.parse(data)!!
        assertEquals(Proto.WELCOME, type)
        assertEquals(0, body.get().toInt())
        assertEquals(1, body.get().toInt())
        assertEquals("PC", Proto.readStr(body))
    }
}
