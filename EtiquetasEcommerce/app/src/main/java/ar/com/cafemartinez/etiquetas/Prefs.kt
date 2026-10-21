package ar.com.cafemartinez.etiquetas

import android.content.Context
import kotlin.math.roundToInt

/** Configuración guardada en el equipo. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    var printerIp: String
        get() = sp.getString("printerIp", "") ?: ""
        set(v) = sp.edit().putString("printerIp", v.trim()).apply()

    var printerPort: Int
        get() = sp.getInt("printerPort", 9100)
        set(v) = sp.edit().putInt("printerPort", v).apply()

    /** 203 o 300 según la impresora. */
    var dpi: Int
        get() = sp.getInt("dpi", 203)
        set(v) = sp.edit().putInt("dpi", v).apply()

    var labelWidthMm: Int
        get() = sp.getInt("labelWidthMm", 100)
        set(v) = sp.edit().putInt("labelWidthMm", v).apply()

    var labelHeightMm: Int
        get() = sp.getInt("labelHeightMm", 150)
        set(v) = sp.edit().putInt("labelHeightMm", v).apply()

    var copies: Int
        get() = sp.getInt("copies", 1)
        set(v) = sp.edit().putInt("copies", v).apply()

    /** 0-255: por debajo de este brillo el punto se imprime negro. */
    var threshold: Int
        get() = sp.getInt("threshold", 140)
        set(v) = sp.edit().putInt("threshold", v).apply()

    var autoRotate: Boolean
        get() = sp.getBoolean("autoRotate", true)
        set(v) = sp.edit().putBoolean("autoRotate", v).apply()

    /** "folder" = carpeta en el equipo, "url" = servidor web. */
    var sourceMode: String
        get() = sp.getString("sourceMode", MODE_FOLDER) ?: MODE_FOLDER
        set(v) = sp.edit().putString("sourceMode", v).apply()

    var folderUri: String
        get() = sp.getString("folderUri", "") ?: ""
        set(v) = sp.edit().putString("folderUri", v).apply()

    /** Ej: http://192.168.1.50/etiquetas/{codigo}.png */
    var urlTemplate: String
        get() = sp.getString("urlTemplate", "") ?: ""
        set(v) = sp.edit().putString("urlTemplate", v.trim()).apply()

    var scanAction: String
        get() = sp.getString("scanAction", ScanIntent.UROVO_ACTION) ?: ScanIntent.UROVO_ACTION
        set(v) = sp.edit().putString("scanAction", v.trim()).apply()

    var scanExtra: String
        get() = sp.getString("scanExtra", "barcode_string") ?: "barcode_string"
        set(v) = sp.edit().putString("scanExtra", v.trim()).apply()

    fun widthDots(): Int = (labelWidthMm / 25.4 * dpi).roundToInt()
    fun heightDots(): Int = (labelHeightMm / 25.4 * dpi).roundToInt()

    fun zplOptions() = ZplConverter.Options(
        widthDots = widthDots(),
        heightDots = heightDots(),
        threshold = threshold,
        autoRotate = autoRotate,
        copies = copies.coerceIn(1, 99)
    )

    companion object {
        const val MODE_FOLDER = "folder"
        const val MODE_URL = "url"
    }
}
