package app.rulik.input

import androidx.compose.ui.geometry.Offset
import app.rulik.model.CType
import app.rulik.model.Control
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Куда засчитать касание. По порядку:
 * 1. точно по нарисованному элементу (верхний побеждает);
 * 2. по зоне кнопки, сдвинутой под то, как человек на самом деле жмёт (если включено обучение);
 * 3. с небольшим запасом вокруг любого элемента;
 * 4. ближайшая кнопка или крестовина в радиусе пальца (если включено).
 * Сдвиг копится только из нажатий, поэтому нарисованная кнопка всегда остаётся своей.
 */
class Aim(
    var snap: Boolean = true,
    var learn: Boolean = true,
    biases: Map<String, Bias> = emptyMap(),
) {
    /** Среднее смещение нажатий от центра в долях половинного размера кнопки. */
    data class Bias(val x: Float = 0f, val y: Float = 0f, val n: Int = 0)

    private val _biases = HashMap(biases)
    val biases: Map<String, Bias> get() = _biases

    fun <H> hit(p: Offset, items: List<H>, control: (H) -> Control, geo: (H) -> Geo, slack: Float, reach: Float): H? {
        for (i in items.indices.reversed()) if (geo(items[i]).contains(p)) return items[i]
        if (learn) {
            for (i in items.indices.reversed()) {
                val c = control(items[i])
                if (c.type == CType.BUTTON && shifted(c, geo(items[i])).contains(p)) return items[i]
            }
        }
        for (i in items.indices.reversed()) if (geo(items[i]).contains(p, slack)) return items[i]
        if (!snap) return null
        var best: H? = null
        var bestDist = reach
        for (h in items) {
            val type = control(h).type
            if (type != CType.BUTTON && type != CType.DPAD) continue
            val d = edgeDistance(p, geo(h))
            if (d <= bestDist) { best = h; bestDist = d }
        }
        return best
    }

    /** Запоминает, куда пришлось нажатие по кнопке. */
    fun record(c: Control, p: Offset, g: Geo) {
        if (!learn || c.type != CType.BUTTON) return
        val nx = ((p.x - g.cx) / g.hw).coerceIn(-LIMIT, LIMIT)
        val ny = ((p.y - g.cy) / g.hh).coerceIn(-LIMIT, LIMIT)
        val b = _biases[c.id] ?: Bias()
        // скользящее среднее: первые нажатия весят больше, потом подстройка медленная
        val w = 1f / min(b.n + 1, WINDOW)
        _biases[c.id] = Bias(b.x + (nx - b.x) * w, b.y + (ny - b.y) * w, min(b.n + 1, MAX_COUNT))
    }

    /** Зона кнопки со сдвигом под пальцы. До MIN_SAMPLES нажатий сдвига нет. */
    fun shifted(c: Control, g: Geo): Geo {
        val b = _biases[c.id] ?: return g
        if (b.n < MIN_SAMPLES) return g
        return g.copy(cx = g.cx + b.x.coerceIn(-MAX_SHIFT, MAX_SHIFT) * g.hw, cy = g.cy + b.y.coerceIn(-MAX_SHIFT, MAX_SHIFT) * g.hh)
    }

    /** Убирает данные удалённых кнопок. */
    fun keepOnly(ids: Set<String>) {
        _biases.keys.retainAll(ids)
    }

    companion object {
        const val MIN_SAMPLES = 8
        const val MAX_SHIFT = 0.4f
        private const val LIMIT = 1.5f
        private const val WINDOW = 40
        private const val MAX_COUNT = 1000

        fun edgeDistance(p: Offset, g: Geo): Float =
            hypot(max(abs(p.x - g.cx) - g.hw, 0f), max(abs(p.y - g.cy) - g.hh, 0f))

        fun encode(biases: Map<String, Bias>): String =
            biases.entries.joinToString(";") { (id, b) -> "$id:${b.x}:${b.y}:${b.n}" }

        fun decode(s: String): Map<String, Bias> = s.split(';').mapNotNull { part ->
            val f = part.split(':')
            if (f.size != 4) return@mapNotNull null
            val x = f[1].toFloatOrNull() ?: return@mapNotNull null
            val y = f[2].toFloatOrNull() ?: return@mapNotNull null
            val n = f[3].toIntOrNull() ?: return@mapNotNull null
            f[0] to Bias(x, y, n)
        }.toMap()
    }
}
