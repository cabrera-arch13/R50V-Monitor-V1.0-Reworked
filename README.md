# R50 V MONITOR (Android)

Proyecto Android Kotlin + Jetpack Compose, compilable por GitHub Actions. La APK es debug y no está firmada para distribución comercial.

## Novedades de esta versión

- Controles de herramientas visuales compactos tipo chip, con área táctil de activación consistente.
- Detección periódica de dispositivos USB con interfaz/clase USB Video (UVC) cada 1,5 s mientras la pestaña USB está abierta.
- Selecciona automáticamente una cámara externa que Android publique mediante Camera2 cuando hay una capturadora UVC conectada.
- Estado distinto para: no hay UVC, UVC encontrada pero Camera2 no la expone, y UVC con cámara externa Camera2 disponible.
- Destinos Java y Kotlin alineados a JVM 17; el flujo Actions construye la variante debug y sube el APK como artefacto.

## Importante sobre USB‑C/UVC

La detección USB identifica dispositivos con clase de vídeo UVC. La vista previa usa el proveedor Camera2 del teléfono. Android no ofrece una API USB universal de vídeo que funcione con todas las capturadoras; si una capturadora se detecta por USB pero el Samsung no la publica como cámara `LENS_FACING_EXTERNAL`, esta app mostrará ese estado y no podrá presentar imagen. No se implementa un driver UVC nativo propio.

Los chips de histograma, zebras, focus peaking, false color y carga LUT siguen siendo controles de interfaz; el análisis de píxeles y la aplicación de LUT al vídeo aún no están implementados. La regla de tercios sí se dibuja sobre la vista Camera2. Esta versión no se ha validado físicamente con el Galaxy S23 Ultra ni con una capturadora.

## Wi‑Fi Canon

La pestaña Wi‑Fi usa HTTP CCAPI para solicitar Live View JPEG. En la R50 V activa Camera Control API, configura HTTP (HTTPS deshabilitado para esta versión) y establece la conexión Wi‑Fi. Conecta el teléfono a la misma red e introduce la IP y el puerto indicados por la cámara. Si la cámara exige autenticación, introduce las credenciales. La conexión Wi‑Fi puede tener mayor latencia que HDMI; tampoco se ha probado físicamente en esta entrega.

## Generar el APK con GitHub

El flujo está en `.github/workflows/android.yml`. Sube el contenido del proyecto a la raíz del repositorio y, en Actions, espera a que termine el flujo **Android APK**. Descarga el artefacto `R50V-Monitor-debug-apk`; dentro del ZIP está `app-debug.apk`.
