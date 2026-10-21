# Etiquetas Ecommerce (Urovo → Zebra por red)

App Android para colectores Urovo: se escanea el código del pedido, la app busca la imagen
de la etiqueta de envío (PNG/JPG) cuyo nombre coincide con el código, la convierte a ZPL y la
manda a la impresora Zebra por WiFi (puerto 9100).

```
Escaneo 1234567  →  busca 1234567.png / .jpg  →  convierte a ZPL  →  Zebra 192.168.x.x:9100
```

## 1. Compilar el APK

**Opción A — GitHub (sin instalar nada)**
1. Crear un repositorio (puede ser privado) y subir esta carpeta completa.
2. Ir a la pestaña **Actions** → "Compilar APK" → **Run workflow** (también corre solo con cada push a `main`).
3. Cuando termine (~4 min), bajar el artefacto `etiquetas-ecommerce-apk`: adentro está `app-release.apk`.

**Opción B — Android Studio**
1. File → Open → elegir la carpeta `EtiquetasEcommerce`.
2. Build → Build App Bundle(s) / APK(s) → Build APK(s).
3. El APK queda en `app/build/outputs/apk/`.

El APK está firmado con clave de depuración: sirve para instalación interna (no para Play Store).

## 2. Instalar en la Urovo
Copiar el APK al equipo (USB o Drive) y abrirlo. Si pide permiso para "instalar apps de origen
desconocido", habilitarlo para el explorador de archivos.

## 3. Configurar el escáner de la Urovo
La app acepta las dos formas de salida del escáner:

- **Intent / Broadcast (recomendado):** Ajustes → *Scanner* / *Escáner* → *Output mode* → **Intent**.
  Acción `android.intent.ACTION_DECODE_DATA`, extra `barcode_string` (los valores de fábrica de Urovo).
- **Teclado (Keyboard wedge):** también funciona, siempre que el escáner agregue **Enter** al final
  (opción *Append Enter* / *Sufijo: Enter*).

Si un modelo usa otra acción o extra, se cambian en Configuración → *Escáner (avanzado)*.

## 4. Configurar la app (botón ⚙ Config.)
| Campo | Valor típico |
|---|---|
| IP de la impresora | la que figura en el reporte de configuración de la Zebra (mantener apretado FEED) |
| Puerto | 9100 |
| dpi | 203 (la mayoría) o 300 (modelos "-300dpi") |
| Tamaño | 100 × 150 mm (etiqueta de envío 4"×6") |
| Umbral de negro | 140; subirlo si sale clara, bajarlo si sale empastada |

Tocar **Imprimir etiqueta de prueba** para verificar la conexión.

**Origen de las etiquetas** (elegir uno):
- **Carpeta en el equipo:** se elige una carpeta del almacenamiento de la Urovo con las imágenes.
  Los archivos se cargan ahí por USB o sincronizándola con alguna app.
  Google Drive no aparece como carpeta elegible: para Drive, usar una app que sincronice a una carpeta local.
- **Servidor web (URL):** si las etiquetas están en una PC o servidor, se publican por HTTP y se carga
  una plantilla como `http://192.168.1.20/etiquetas/{codigo}.png`. Es la opción más cómoda para
  muchos equipos, porque no hay que copiar archivos a cada colector.

**Regla de nombres:** el archivo se tiene que llamar exactamente igual que lo que lee el escáner
(sin distinguir mayúsculas). Ej: código `ML-20441` → `ML-20441.png`. Si existen `.png` y `.jpg`
con el mismo nombre, se usa el PNG.

## 5. Uso
- Pantalla verde = impresa, roja = error (con pitido distinto), azul = trabajando.
- Una lectura repetida del mismo código dentro de 3 segundos se ignora (evita dobles impresiones).
- **Reimprimir** vuelve a mandar la última etiqueta.
- **Teclado** muestra el teclado en pantalla para tipear un código a mano.
- Abajo queda el historial de las últimas 15 lecturas.

## Problemas frecuentes
| Mensaje | Qué revisar |
|---|---|
| No hay etiqueta para X | El nombre del archivo no coincide con lo escaneado, o falta el archivo |
| La impresora no responde | IP mal cargada, impresora apagada o en otra red/VLAN |
| Rechazó la conexión | Puerto distinto de 9100 o impresora sin ZPL habilitado |
| Sale muy chica o cortada | Revisar dpi y tamaño de etiqueta en Configuración |
| No reacciona al escanear | Revisar modo de salida del escáner (punto 3) |

## Estructura del código
- `MainActivity.kt` — pantalla principal, recibe escaneos (broadcast o teclado).
- `LabelSource.kt` — busca la imagen en la carpeta o la URL.
- `ZplConverter.kt` — imagen → ZPL `^GFA` (blanco y negro, escalado y rotación).
- `PrinterClient.kt` — envío por TCP a la impresora.
- `SettingsActivity.kt` / `Prefs.kt` — configuración.
