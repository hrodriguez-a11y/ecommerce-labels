package ar.com.cafemartinez.etiquetas

import android.content.Intent

/**
 * Lectura del código que envía el escáner Urovo por broadcast.
 * Urovo (ScanManager): acción "android.intent.ACTION_DECODE_DATA", extra "barcode_string".
 * Equipos más viejos: acción "urovo.rcv.message", extras "barcode" (byte[]) + "length".
 */
object ScanIntent {
    const val UROVO_ACTION = "android.intent.ACTION_DECODE_DATA"
    const val UROVO_LEGACY_ACTION = "urovo.rcv.message"

    fun actions(custom: String): List<String> =
        linkedSetOf(custom, UROVO_ACTION, UROVO_LEGACY_ACTION).filter { it.isNotBlank() }

    fun extract(intent: Intent, customExtra: String): String? {
        val keys = linkedSetOf(customExtra, "barcode_string", "barcodeData", "data", "scannerdata")
        for (key in keys) {
            if (key.isBlank()) continue
            val value = runCatching { intent.getStringExtra(key) }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
        val bytes = runCatching { intent.getByteArrayExtra("barcode") }.getOrNull() ?: return null
        val len = intent.getIntExtra("length", bytes.size).coerceIn(0, bytes.size)
        return String(bytes, 0, len, Charsets.UTF_8)
    }
}
