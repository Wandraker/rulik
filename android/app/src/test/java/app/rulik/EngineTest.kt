package app.rulik

import androidx.compose.ui.geometry.Offset
import app.rulik.input.Engine
import app.rulik.input.Geo
import app.rulik.input.StickHandler
import app.rulik.model.Action
import app.rulik.model.CType
import app.rulik.model.Control
import app.rulik.model.Keys
import app.rulik.model.Kind
import app.rulik.model.Layout
import app.rulik.model.Out
import app.rulik.model.Presets
import app.rulik.net.Proto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val sent = mutableListOf<ByteArray>()
    private val engine = Engine { sent += it }

    private fun keys(): List<Pair<Int, Boolean>> = sent
        .filter { it[3].toInt() == Proto.KEY }
        .map { ((it[4].toInt() and 0xFF) or ((it[5].toInt() and 0xFF) shl 8)) to (it[6].toInt() == 1) }

    @Test fun twoButtonsOnOneKeyDoNotReleaseEachOther() {
        val a = Action(Kind.KEY, keys = listOf(Keys.W))
        engine.press(a)
        engine.press(a)
        engine.release(a)
        assertEquals(listOf(Keys.W to true), keys())
        engine.release(a)
        assertEquals(listOf(Keys.W to true, Keys.W to false), keys())
    }

    @Test fun comboReleasesInReverse() {
        engine.press(Action(Kind.KEY, keys = listOf(0x12, 0x09)))
        engine.release(Action(Kind.KEY, keys = listOf(0x12, 0x09)))
        assertEquals(listOf(0x12 to true, 0x09 to true, 0x09 to false, 0x12 to false), keys())
    }

    @Test fun stickAsWasdPressesDiagonal() {
        val c = Control(type = CType.STICK, x = .5f, y = .5f, w = .4f, out = Out.WASD)
        val h = StickHandler(c, engine, null)
        val g = Geo(100f, 100f, 50f, 50f, 1f)
        h.down(1, Offset(100f, 100f), g, 0)
        h.move(1, Offset(140f, 60f), g, 10) // вверх-вправо
        assertEquals(setOf(Keys.W to true, Keys.D to true), keys().toSet())
        h.up(1, g, 20)
        assertEquals(4, keys().size)
        assertTrue(keys().takeLast(2).all { !it.second })
    }

    @Test fun releaseAllClearsHeld() {
        engine.press(Action(Kind.KEY, keys = listOf(Keys.A)))
        engine.press(Action(Kind.MOUSE, 1))
        engine.releaseAll()
        val held = sent.last()
        assertEquals(Proto.HELD, held[3].toInt())
        assertEquals(0, held[4].toInt())
        assertEquals(0, held[5].toInt())
    }

    @Test fun presetsSurviveJson() {
        val json = Json { ignoreUnknownKeys = true }
        Presets.all(true).forEach { l ->
            assertEquals(l, json.decodeFromString<Layout>(json.encodeToString(Layout.serializer(), l)))
        }
    }

    @Test fun siteGalleryLayoutsParse() {
        // раскладки с сайта должны импортироваться приложением как есть
        val json = Json { ignoreUnknownKeys = true }
        val files = java.io.File("../../site/src/layouts").listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue(files.isNotEmpty())
        files.forEach { f ->
            val l = json.decodeFromString<Layout>(f.readText())
            assertTrue(f.name, l.controls.isNotEmpty())
        }
    }
}
