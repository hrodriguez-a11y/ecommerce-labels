package ar.com.cafemartinez.etiquetas

import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Envía ZPL "crudo" a la impresora por TCP (puerto 9100 en Zebra). */
object PrinterClient {

    fun send(host: String, port: Int, zpl: String) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5000)
                socket.soTimeout = 15000
                val out = socket.getOutputStream()
                out.write(zpl.toByteArray(Charsets.US_ASCII))
                out.flush()
                socket.shutdownOutput()
            }
        } catch (e: SocketTimeoutException) {
            throw IOException("La impresora $host:$port no responde (¿está prendida y en la misma red?)")
        } catch (e: ConnectException) {
            throw IOException("La impresora $host:$port rechazó la conexión (revisá IP y puerto)")
        } catch (e: NoRouteToHostException) {
            throw IOException("No hay ruta a $host (¿el equipo está en la WiFi correcta?)")
        } catch (e: UnknownHostException) {
            throw IOException("Dirección de impresora inválida: $host")
        }
    }

    fun testLabel(widthDots: Int, heightDots: Int): String {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        return "^XA^CI28^PW$widthDots^LL$heightDots" +
            "^FO40,40^A0N,50,50^FDPrueba de impresion^FS" +
            "^FO40,110^A0N,30,30^FDEtiquetas Ecommerce - $now^FS" +
            "^FO40,170^GB${(widthDots - 80).coerceAtLeast(10)},4,4^FS" +
            "^FO40,200^BY3^BCN,120,Y,N,N^FD123456789^FS" +
            "^XZ"
    }
}
