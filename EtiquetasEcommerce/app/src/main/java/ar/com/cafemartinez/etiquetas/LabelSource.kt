package ar.com.cafemartinez.etiquetas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LabelNotFoundException(code: String) : Exception("No se encontró etiqueta para $code")

/**
 * Busca la imagen de la etiqueta cuyo nombre de archivo coincide con el código escaneado.
 * Ej: código 1234567 -> 1234567.png / 1234567.jpg / 1234567.jpeg (sin distinguir mayúsculas).
 */
class LabelSource(private val context: Context, private val prefs: Prefs) {

    fun load(code: String): Bitmap {
        val bytes = if (prefs.sourceMode == Prefs.MODE_URL) fromUrl(code) else fromFolder(code)
        return decode(bytes) ?: throw IOException("El archivo de $code no es una imagen válida")
    }

    // ---------- Carpeta del equipo (Storage Access Framework) ----------

    private fun fromFolder(code: String): ByteArray {
        val tree = prefs.folderUri
        if (tree.isBlank()) throw IllegalStateException("Elegí la carpeta de etiquetas en Configuración")
        val treeUri = Uri.parse(tree)
        val key = code.lowercase()

        var uri = cachedLookup(treeUri, key, forceReload = false)
        if (uri == null) uri = cachedLookup(treeUri, key, forceReload = true)
        if (uri == null) throw LabelNotFoundException(code)

        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("No se pudo abrir el archivo de $code")
    }

    private fun cachedLookup(treeUri: Uri, key: String, forceReload: Boolean): Uri? {
        synchronized(Cache) {
            val now = System.currentTimeMillis()
            val stale = Cache.tree != treeUri.toString() || Cache.files.isEmpty()
            val canReload = now - Cache.loadedAt > 1500
            if (stale || (forceReload && canReload)) {
                Cache.files = listFolder(treeUri)
                Cache.tree = treeUri.toString()
                Cache.loadedAt = now
            }
            return Cache.files[key]
        }
    }

    private fun listFolder(treeUri: Uri): Map<String, Uri> {
        val result = HashMap<String, Uri>()
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in IMAGE_EXT) {
                    val base = name.substringBeforeLast('.').trim().lowercase()
                    // Si hay .png y .jpg con el mismo nombre, gana el PNG.
                    if (!result.containsKey(base) || ext == "png") {
                        result[base] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    }
                }
            }
        } ?: throw IOException("No hay permiso para leer la carpeta. Volvé a elegirla en Configuración")
        return result
    }

    // ---------- Servidor web ----------

    private fun fromUrl(code: String): ByteArray {
        val template = prefs.urlTemplate
        if (template.isBlank()) throw IllegalStateException("Configurá la URL de etiquetas")
        val encoded = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
        val url = if (template.contains("{codigo}")) template.replace("{codigo}", encoded)
        else template.trimEnd('/') + "/" + encoded + ".png"

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 10000
        try {
            val status = conn.responseCode
            if (status == 404) throw LabelNotFoundException(code)
            if (status !in 200..299) throw IOException("El servidor respondió $status")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- Decodificación ----------

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Evita cargar imágenes enormes: alcanza con ~2x la resolución de la etiqueta.
        val maxSide = maxOf(prefs.widthDots(), prefs.heightDots()) * 2
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private object Cache {
        var tree: String? = null
        var files: Map<String, Uri> = emptyMap()
        var loadedAt = 0L
    }

    companion object {
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg")

        fun clearCache() = synchronized(Cache) {
            Cache.tree = null
            Cache.files = emptyMap()
            Cache.loadedAt = 0L
        }
    }
}
