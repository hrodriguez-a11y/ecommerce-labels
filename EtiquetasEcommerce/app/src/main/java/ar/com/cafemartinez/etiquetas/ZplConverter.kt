package ar.com.cafemartinez.etiquetas

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Convierte una imagen (PNG/JPG) en un comando ZPL ^GFA que cualquier Zebra imprime. */
object ZplConverter {

    data class Options(
        val widthDots: Int,
        val heightDots: Int,
        val threshold: Int,
        val autoRotate: Boolean,
        val copies: Int
    )

    fun toZpl(source: Bitmap, o: Options): String {
        var bmp = source

        // Si la imagen viene apaisada y la etiqueta es vertical (o al revés), se gira 90°.
        val labelPortrait = o.heightDots >= o.widthDots
        val imagePortrait = bmp.height >= bmp.width
        if (o.autoRotate && labelPortrait != imagePortrait) {
            val m = Matrix().apply { postRotate(90f) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }

        // Escala para ocupar la etiqueta respetando la proporción.
        val scale = min(o.widthDots.toFloat() / bmp.width, o.heightDots.toFloat() / bmp.height)
        val w = max(1, (bmp.width * scale).roundToInt()).coerceAtMost(o.widthDots)
        val h = max(1, (bmp.height * scale).roundToInt()).coerceAtMost(o.heightDots)
        val scaled = if (w == bmp.width && h == bmp.height) bmp
        else Bitmap.createScaledBitmap(bmp, w, h, true)

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val bytesPerRow = (w + 7) / 8
        val total = bytesPerRow * h
        val data = StringBuilder(total * 2)
        for (y in 0 until h) {
            val rowStart = y * w
            for (bx in 0 until bytesPerRow) {
                var b = 0
                for (bit in 0 until 8) {
                    val x = bx * 8 + bit
                    if (x < w && isBlack(pixels[rowStart + x], o.threshold)) {
                        b = b or (0x80 ushr bit)
                    }
                }
                data.append(HEX[b ushr 4]).append(HEX[b and 0x0F])
            }
        }

        val x0 = (o.widthDots - w) / 2
        val y0 = (o.heightDots - h) / 2
        return buildString(data.length + 200) {
            append("^XA")
            append("^PW").append(o.widthDots)
            append("^LL").append(o.heightDots)
            append("^LH0,0")
            append("^FO").append(x0).append(',').append(y0)
            append("^GFA,").append(total).append(',').append(total).append(',').append(bytesPerRow).append(',')
            append(data)
            append("^FS")
            append("^PQ").append(o.copies)
            append("^XZ")
        }
    }

    private fun isBlack(argb: Int, threshold: Int): Boolean {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        // Los píxeles transparentes cuentan como blanco.
        val visible = 255 - (255 - lum) * a / 255
        return visible < threshold
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
