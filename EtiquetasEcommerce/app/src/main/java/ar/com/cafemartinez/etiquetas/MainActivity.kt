package ar.com.cafemartinez.etiquetas

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var printerInfo: TextView
    private lateinit var input: EditText
    private lateinit var preview: ImageView
    private lateinit var historyView: TextView

    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var tones: ToneGenerator? = null
    private val history = ArrayDeque<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var lastScan = ""
    private var lastScanAt = 0L
    private var lastPrinted: String? = null
    private var receiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ScanIntent.extract(intent, prefs.scanExtra)?.let { onScan(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        status = findViewById(R.id.status)
        printerInfo = findViewById(R.id.printerInfo)
        input = findViewById(R.id.input)
        preview = findViewById(R.id.preview)
        historyView = findViewById(R.id.history)

        tones = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }.getOrNull()

        // Modo teclado del escáner (o tipeo manual): el código llega seguido de Enter.
        input.showSoftInputOnFocus = false
        input.setOnEditorActionListener { _, actionId, event ->
            when {
                actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO -> {
                    submitInput(); true
                }
                event != null && event.keyCode == KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_DOWN) submitInput()
                    true
                }
                else -> false
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null && (s.contains('\n') || s.contains('\r'))) submitInput()
            }
        })

        findViewById<Button>(R.id.btnReprint).setOnClickListener {
            val code = lastPrinted
            if (code == null) setStatus("Todavía no se imprimió nada", COLOR_IDLE)
            else onScan(code, force = true)
        }
        findViewById<Button>(R.id.btnKeyboard).setOnClickListener {
            input.showSoftInputOnFocus = true
            input.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, 0)
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        registerScanner()
        input.showSoftInputOnFocus = false
        input.requestFocus()
        refreshPrinterInfo()
        if (prefs.printerIp.isBlank()) {
            setStatus("Configurá la impresora en ⚙ Configuración", COLOR_WARN)
        } else if (lastPrinted == null && history.isEmpty()) {
            setStatus("Listo. Escaneá una etiqueta", COLOR_IDLE)
        }
    }

    override fun onPause() {
        super.onPause()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(scanReceiver) }
            receiverRegistered = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
        tones?.release()
    }

    private fun registerScanner() {
        if (receiverRegistered) return
        val filter = IntentFilter()
        ScanIntent.actions(prefs.scanAction).forEach { filter.addAction(it) }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(scanReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun submitInput() {
        val text = input.text.toString()
        input.setText("")
        onScan(text)
    }

    private fun onScan(raw: String, force: Boolean = false) {
        val code = raw.filter { it >= ' ' }.trim()
        if (code.isEmpty()) return

        // Evita imprimir dos veces la misma lectura (rebote del gatillo / doble canal).
        val now = System.currentTimeMillis()
        if (!force && code == lastScan && now - lastScanAt < 3000) return
        lastScan = code
        lastScanAt = now

        setStatus("Buscando $code…", COLOR_BUSY)
        worker.execute { process(code) }
    }

    private fun process(code: String) {
        try {
            if (prefs.printerIp.isBlank()) throw IllegalStateException("Falta configurar la IP de la impresora")
            val bitmap = LabelSource(this, prefs).load(code)
            val zpl = ZplConverter.toZpl(bitmap, prefs.zplOptions())
            ui.post {
                preview.setImageBitmap(bitmap)
                setStatus("Imprimiendo $code…", COLOR_BUSY)
            }
            PrinterClient.send(prefs.printerIp, prefs.printerPort, zpl)
            ui.post {
                lastPrinted = code
                addHistory("✓ $code")
                setStatus("✓ Impresa: $code", COLOR_OK)
                beep(ok = true)
            }
        } catch (e: LabelNotFoundException) {
            ui.post { fail(code, "No hay etiqueta para $code") }
        } catch (e: Exception) {
            ui.post { fail(code, e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun fail(code: String, message: String) {
        addHistory("✗ $code — $message")
        setStatus("✗ $message", COLOR_ERROR)
        beep(ok = false)
    }

    private fun addHistory(line: String) {
        history.addFirst("${timeFmt.format(Date())}  $line")
        while (history.size > 15) history.removeLast()
        historyView.text = history.joinToString("\n")
    }

    private fun setStatus(text: String, color: Int) {
        status.text = text
        status.setBackgroundColor(color)
    }

    private fun refreshPrinterInfo() {
        val ip = prefs.printerIp.ifBlank { "sin configurar" }
        val source = if (prefs.sourceMode == Prefs.MODE_URL) "URL" else "carpeta"
        printerInfo.text = "Impresora: $ip:${prefs.printerPort} · ${prefs.labelWidthMm}×${prefs.labelHeightMm} mm · ${prefs.dpi} dpi · origen: $source"
    }

    private fun beep(ok: Boolean) {
        tones?.startTone(if (ok) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_SUP_ERROR, if (ok) 120 else 400)
    }

    companion object {
        private val COLOR_IDLE = Color.parseColor("#455A64")
        private val COLOR_BUSY = Color.parseColor("#1565C0")
        private val COLOR_OK = Color.parseColor("#2E7D32")
        private val COLOR_WARN = Color.parseColor("#EF6C00")
        private val COLOR_ERROR = Color.parseColor("#C62828")
    }
}
