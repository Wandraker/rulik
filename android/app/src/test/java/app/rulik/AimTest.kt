package app.rulik

import androidx.compose.ui.geometry.Offset
import app.rulik.input.Aim
import app.rulik.input.Geo
import app.rulik.model.CType
import app.rulik.model.Control
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AimTest {
    // две кнопки 100x100 с центрами в 100 и 300 по x, щель между ними 100 пикселей
    private val a = Control(id = "a", type = CType.BUTTON, x = 0f, y = 0f, w = 0f)
    private val b = Control(id = "b", type = CType.BUTTON, x = 0f, y = 0f, w = 0f)
    private val pad = Control(id = "t", type = CType.TOUCHPAD, x = 0f, y = 0f, w = 0f)
    private val geos = mapOf(
        "a" to Geo(100f, 100f, 50f, 50f, 1f),
        "b" to Geo(300f, 100f, 50f, 50f, 1f),
        "t" to Geo(200f, 400f, 50f, 50f, 1f),
    )

    private fun hit(aim: Aim, x: Float, y: Float = 100f, items: List<Control> = listOf(a, b)) =
        aim.hit(Offset(x, y), items, { it }, { geos.getValue(it.id) }, slack = 8f, reach = 28f)

    @Test fun exactHitWins() {
        assertSame(a, hit(Aim(), 140f))
        assertSame(b, hit(Aim(), 260f))
    }

    @Test fun gapGoesToNearestButtonWithinReach() {
        assertSame(a, hit(Aim(), 170f)) // 20 от края a, 80 от края b
        assertSame(b, hit(Aim(), 225f)) // 25 от края b
        assertNull(hit(Aim(), 200f)) // середина щели: обе дальше 28
    }

    @Test fun snapCanBeTurnedOff() {
        val aim = Aim(snap = false)
        assertSame(a, hit(aim, 155f)) // запас 8 работает всегда
        assertNull(hit(aim, 170f))
    }

    @Test fun touchpadsDoNotAttractMisses() {
        assertNull(hit(Aim(), 200f, 340f, listOf(pad)))
    }

    @Test fun learnedShiftNeedsEnoughPresses() {
        val aim = Aim(snap = false)
        val g = geos.getValue("a")
        repeat(Aim.MIN_SAMPLES - 1) { aim.record(a, Offset(100f, 140f), g) } // жмёт ниже центра
        assertNull(hit(aim, 100f, 165f, listOf(a)))
        aim.record(a, Offset(100f, 140f), g)
        assertSame(a, hit(aim, 100f, 165f, listOf(a)))
    }

    @Test fun shiftIsCapped() {
        val aim = Aim()
        val g = geos.getValue("a")
        repeat(50) { aim.record(a, Offset(100f, 175f), g) } // всегда сильно ниже
        assertEquals(100f + Aim.MAX_SHIFT * 50f, aim.shifted(a, g).cy, 0.01f)
    }

    @Test fun learningOffRecordsNothing() {
        val aim = Aim(learn = false)
        repeat(20) { aim.record(a, Offset(100f, 140f), geos.getValue("a")) }
        assertEquals(emptyMap<String, Aim.Bias>(), aim.biases)
    }

    @Test fun drawnNeighbourBeatsShiftedZone() {
        val aim = Aim()
        val g = geos.getValue("a")
        repeat(50) { aim.record(a, Offset(150f, 100f), g) } // a жмут правым краем, зона a сдвигается к b
        assertSame(b, hit(aim, 255f)) // но нарисованная b остаётся своей
    }

    @Test fun biasesSurviveEncoding() {
        val aim = Aim()
        repeat(10) { aim.record(a, Offset(110f, 120f), geos.getValue("a")) }
        assertEquals(aim.biases, Aim.decode(Aim.encode(aim.biases)))
        assertEquals(emptyMap<String, Aim.Bias>(), Aim.decode(""))
    }
}
