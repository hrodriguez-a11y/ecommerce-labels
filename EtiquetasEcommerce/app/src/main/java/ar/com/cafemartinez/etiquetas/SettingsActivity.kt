package ar.com.cafemartinez.etiquetas

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    private lateinit var ip: EditText
    private lateinit var port: EditText
    private lateinit var dpi: EditText
    private lateinit var width: EditText
    private lateinit var height: EditText
    private lateinit var copies: EditText
    private lateinit var threshold: EditText
    private lateinit var autoRotate: CheckBox
    private lateinit var rbFolder: RadioButton
    private lateinit var rbUrl: RadioButton
    private lateinit var folderBox: LinearLayout
    private lateinit var folderLabel: TextView
    private lateinit var urlBox: LinearLayout
    private lateinit var url: EditText
    private lateinit var scanAction: EditText
    private lateinit var scanExtra: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        ip = findViewById(R.id.ip)
        port = findViewById(R.id.port)
        dpi = findViewById(R.id.dpi)
        width = findViewById(R.id.width)
        height = findViewById(R.id.height)
        copies = findViewById(R.id.copies)
        threshold = findViewById(R.id.threshold)
        autoRotate = findViewById(R.id.autoRotate)
        rbFolder = findViewById(R.id.rbFolder)
        rbUrl = findViewById(R.id.rbUrl)
        folderBox = findViewById(R.id.folderBox)
        folderLabel = findViewById(R.id.folderLabel)
        urlBox = findViewById(R.id.urlBox)
        url = findViewById(R.id.url)
        scanAction = findViewById(R.id.scanAction)
        scanExtra = findViewById(R.id.scanExtra)

        ip.setText(prefs.printerIp)
        port.setText(prefs.printerPort.toString())
        dpi.setText(prefs.dpi.toString())
        width.setText(prefs.labelWidthMm.toString())
        height.setText(prefs.labelHeightMm.toString())
        copies.setText(prefs.copies.toString())
        threshold.setText(prefs.threshold.toString())
        autoRotate.isChecked = prefs.autoRotate
        url.setText(prefs.urlTemplate)
        scanAction.setText(prefs.scanAction)
        scanExtra.setText(prefs.scanExtra)
        if (prefs.sourceMode == Prefs.MODE_URL) rbUrl.isChecked = true else rbFolder.isChecked = true
        updateSourceVisibility()
        updateFolderLabel()

        rbFolder.setOnClickListener { updateSourceVisibility() }
        rbUrl.setOnClickListener { updateSourceVisibility() }

        findViewById<Button>(R.id.btnFolder).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, REQ_FOLDER)
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            save()
            val host = prefs.printerIp
            if (host.isBlank()) {
                toast("Cargá la IP de la impresora")
                return@setOnClickListener
            }
            toast("Enviando prueba a $host…")
            val zpl = PrinterClient.testLabel(prefs.widthDots(), prefs.heightDots())
            Thread {
                val result = runCatching { PrinterClient.send(host, prefs.printerPort, zpl) }
                runOnUiThread {
                    toast(result.fold({ "✓ Prueba enviada" }, { "✗ ${it.message}" }))
                }
            }.start()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            save()
            toast("Configuración guardada")
            finish()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FOLDER || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs.folderUri = uri.toString()
        LabelSource.clearCache()
        updateFolderLabel()
    }

    private fun save() {
        prefs.printerIp = ip.text.toString()
        prefs.printerPort = port.intOr(9100).coerceIn(1, 65535)
        prefs.dpi = dpi.intOr(203).coerceIn(100, 600)
        prefs.labelWidthMm = width.intOr(100).coerceIn(10, 300)
        prefs.labelHeightMm = height.intOr(150).coerceIn(10, 500)
        prefs.copies = copies.intOr(1).coerceIn(1, 99)
        prefs.threshold = threshold.intOr(140).coerceIn(1, 254)
        prefs.autoRotate = autoRotate.isChecked
        prefs.sourceMode = if (rbUrl.isChecked) Prefs.MODE_URL else Prefs.MODE_FOLDER
        prefs.urlTemplate = url.text.toString()
        prefs.scanAction = scanAction.text.toString().ifBlank { ScanIntent.UROVO_ACTION }
        prefs.scanExtra = scanExtra.text.toString().ifBlank { "barcode_string" }
    }

    private fun updateSourceVisibility() {
        folderBox.visibility = if (rbFolder.isChecked) View.VISIBLE else View.GONE
        urlBox.visibility = if (rbUrl.isChecked) View.VISIBLE else View.GONE
    }

    private fun updateFolderLabel() {
        val uri = prefs.folderUri
        folderLabel.text = if (uri.isBlank()) "Ninguna carpeta elegida"
        else runCatching {
            "Carpeta: " + DocumentsContract.getTreeDocumentId(Uri.parse(uri))
        }.getOrDefault(uri)
    }

    private fun EditText.intOr(default: Int): Int = text.toString().trim().toIntOrNull() ?: default

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_FOLDER = 42
    }
}
