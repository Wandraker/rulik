package app.rulik.data

import android.content.Context
import android.os.Build
import app.rulik.input.Aim
import app.rulik.model.Layout
import app.rulik.model.Presets
import app.rulik.model.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/** Раскладки лежат JSON-файлами, настройки в SharedPreferences. */
class Store(private val context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }
    private val dir = File(context.filesDir, "layouts").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("rulik", Context.MODE_PRIVATE)

    private val _layouts = MutableStateFlow(load())
    val layouts: StateFlow<List<Layout>> = _layouts

    val ru: Boolean get() = Locale.getDefault().language == "ru"

    private fun load(): List<Layout> {
        if (!prefs.getBoolean("seeded", false)) {
            Presets.all(Locale.getDefault().language == "ru").forEachIndexed { i, l -> write(l, i) }
            prefs.edit().putBoolean("seeded", true).apply()
        }
        return dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .sortedBy { it.name }
            .mapNotNull { f -> runCatching { json.decodeFromString<Layout>(f.readText()) }.getOrNull() }
    }

    private fun fileFor(id: String) = dir.listFiles { f -> f.name.endsWith("_$id.json") }?.firstOrNull()

    private fun write(layout: Layout, order: Int) {
        val f = fileFor(layout.id) ?: File(dir, "%04d_%s.json".format(order, layout.id))
        f.writeText(json.encodeToString(Layout.serializer(), layout))
    }

    fun save(layout: Layout) {
        val list = _layouts.value
        val idx = list.indexOfFirst { it.id == layout.id }
        write(layout, if (idx >= 0) idx else (nextOrder()))
        _layouts.value = if (idx >= 0) list.toMutableList().also { it[idx] = layout } else list + layout
    }

    private fun nextOrder(): Int = dir.listFiles().orEmpty()
        .mapNotNull { it.name.substringBefore('_').toIntOrNull() }.maxOrNull()?.plus(1) ?: 0

    fun delete(id: String) {
        fileFor(id)?.delete()
        _layouts.value = _layouts.value.filterNot { it.id == id }
    }

    fun duplicate(layout: Layout, suffix: String): Layout =
        layout.copy(id = newId(), name = "${layout.name} $suffix").also { save(it) }

    fun export(layout: Layout): String = json.encodeToString(Layout.serializer(), layout)

    /** Импорт из JSON. Всегда новый id, чтобы не затереть свою раскладку. */
    fun import(text: String): Layout? = runCatching {
        json.decodeFromString<Layout>(text.trim()).copy(id = newId())
    }.getOrNull()?.also { save(it) }

    /** Возвращает стандартные раскладки, которых сейчас нет (по названию). */
    fun restorePresets() {
        val names = _layouts.value.map { it.name }.toSet()
        Presets.all(ru).filter { it.name !in names }.forEach { save(it) }
    }

    // --- настройки ---

    var lastHost: String
        get() = prefs.getString("host", "") ?: ""
        set(v) = prefs.edit().putString("host", v).apply()

    var lastPort: Int
        get() = prefs.getInt("port", app.rulik.net.Proto.PORT)
        set(v) = prefs.edit().putInt("port", v).apply()

    var usbMode: Boolean
        get() = prefs.getBoolean("usb", false)
        set(v) = prefs.edit().putBoolean("usb", v).apply()

    var lastLayout: String
        get() = prefs.getString("layout", "") ?: ""
        set(v) = prefs.edit().putString("layout", v).apply()

    var deviceName: String
        get() = prefs.getString("device", null) ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        set(v) = prefs.edit().putString("device", v).apply()

    var showPing: Boolean
        get() = prefs.getBoolean("show_ping", true)
        set(v) = prefs.edit().putBoolean("show_ping", v).apply()

    /** Промах между кнопками засчитывается ближайшей. */
    var aimSnap: Boolean
        get() = prefs.getBoolean("aim_snap", true)
        set(v) = prefs.edit().putBoolean("aim_snap", v).apply()

    /** Щелчок вибрацией, когда палец переходит край кнопки. */
    var aimEdge: Boolean
        get() = prefs.getBoolean("aim_edge", true)
        set(v) = prefs.edit().putBoolean("aim_edge", v).apply()

    /** Зоны кнопок подстраиваются под то, куда человек на самом деле жмёт. */
    var aimLearn: Boolean
        get() = prefs.getBoolean("aim_learn", true)
        set(v) = prefs.edit().putBoolean("aim_learn", v).apply()

    /** Стрим: 0 как у монитора, 1 1080p, 2 720p, 3 480p. */
    var streamRes: Int
        get() = prefs.getInt("stream_res", 0)
        set(v) = prefs.edit().putInt("stream_res", v).apply()

    var streamFps: Int
        get() = prefs.getInt("stream_fps", if (maxRefresh() >= 119f) 120 else 60)
        set(v) = prefs.edit().putInt("stream_fps", v).apply()

    var streamMbps: Int
        get() = prefs.getInt("stream_mbps", 15)
        set(v) = prefs.edit().putInt("stream_mbps", v).apply()

    /** Насколько видна раскладка поверх картинки, 0.15..1. */
    var overlayAlpha: Float
        get() = prefs.getFloat("overlay_alpha", 0.5f)
        set(v) = prefs.edit().putFloat("overlay_alpha", v).apply()

    var streamAudio: Boolean
        get() = prefs.getBoolean("stream_audio", true)
        set(v) = prefs.edit().putBoolean("stream_audio", v).apply()

    /** Самая высокая частота экрана телефона. На 120 Гц стрим по умолчанию тоже 120 кадров: так меньше задержка. */
    private fun maxRefresh(): Float = runCatching {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        dm.getDisplay(android.view.Display.DEFAULT_DISPLAY).supportedModes.maxOf { it.refreshRate }
    }.getOrDefault(60f)

    fun streamRequest(): ByteArray {
        val (w, h) = STREAM_SIZES.getOrElse(streamRes) { 0 to 0 }
        return app.rulik.net.Proto.streamStart(w, h, streamFps, streamMbps * 1000, 0, streamAudio)
    }

    fun aimBiases(layoutId: String): Map<String, Aim.Bias> = Aim.decode(prefs.getString("aim_$layoutId", "") ?: "")

    fun saveAimBiases(layoutId: String, biases: Map<String, Aim.Bias>) =
        prefs.edit().putString("aim_$layoutId", Aim.encode(biases)).apply()

    fun resetAim() {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("aim_") && it !in AIM_FLAGS }.forEach { edit.remove(it) }
        edit.apply()
    }

    fun pinFor(host: String): String = prefs.getString("pin_$host", "") ?: ""
    fun setPin(host: String, pin: String) = prefs.edit().putString("pin_$host", pin).apply()

    private companion object {
        val AIM_FLAGS = setOf("aim_snap", "aim_edge", "aim_learn")
        val STREAM_SIZES = listOf(0 to 0, 1920 to 1080, 1280 to 720, 854 to 480)
    }
}
